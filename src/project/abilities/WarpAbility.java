package project.abilities;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.ai.UnitStance;
import mindustry.ai.types.CommandAI;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.abilities.Ability;
import mindustry.entities.part.DrawPart;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.type.StatusEffect;
import project.graphics.WarpParticleEffect;

import static mindustry.Vars.tilesize;

/**
 * 主动跃迁：按住 Alt 蓄力，把单位送到鼠标所指的位置。
 *
 * 移植自 Vanilla Expansion 的 WarpAbility（作者 火星人076 & 128_OTEML，
 * 该项目公开源码供学习）。逻辑与手感保持原样，改动只有三处：
 *  - 去掉对 VE 自有设置系统（VESettings）的依赖，移动端自动跃迁改为常开
 *  - 配色从青绿 00ffce 换成星辉的橙金 ffa665，与本 mod 机体辉光同色系
 *  - 新增 warpMaxDistance，默认 0（不限），需要时可用来封顶跃迁距离
 *
 * 手感设计：
 *  - 蓄力时间 = 基础值 + 距离 × 系数，跳得越远越久，不能贴脸乱闪
 *  - 必须先把机头转到目标方向才允许跃迁（rotAllowWarp），
 *    避免背对着瞬移造成的方向突变
 *  - 音效在起点和终点各播一次，配合震屏形成「撕开—合拢」的两段反馈
 *
 * 注意：多人游戏里服务端读不到客户端键盘，只有客户端会执行位移，
 * 因此合作模式可能出现位置回拉。单人战役不受影响。
 */
public class WarpAbility extends Ability{

    /** 蓄力距离基准：每跨越这么多像素，追加一次 warpMinWarmup 帧蓄力 */
    public float warpMinWarmupDistance = 800f;
    /** 上述距离对应的追加蓄力帧数 */
    public float warpMinWarmup = 15f;
    /** 与距离无关的固定蓄力帧数 */
    public float warpBaseWarmup = 15f;
    /** 跃迁结束后的冷却帧数 */
    public float warpCooldown = 150f;
    /** 单次跃迁的最大距离（像素）。0 = 不限，VE 原版就是不限的 */
    public float warpMaxDistance = 0f;

    public Seq<DrawPart> warpEndParts = new Seq<>();
    public Effect warpEndEffect = Fx.shootBigColor;
    public Effect warpBeginEffect = Fx.circleColorSpark;
    public Effect warpPrepareEffect = Fx.none;
    public float warpPrepareEffectChance = 0.25f;
    public float warpPrepareEffectSpread = 6f;
    public Sound warpSound = Sounds.massdriver;
    public float warpSoundVolume = 0.55f;
    public float warpShake = 10f;
    public StatusEffect warpStatus = StatusEffects.none;

    public Color warpEffectColor = Color.valueOf("ffa665");
    public Color warpTrailColor1 = Color.valueOf("ffa665").a(0.45f);
    public Color warpTrailColor2 = Color.valueOf("ffb87a").a(0.7f);
    public Color warpTrailColor3 = Color.white;
    public float warpTrailStroke = 18f;
    public float warpTrailLife = 28f;
    public boolean useAutoWarpEffect = true;

    /** 被指挥时是否允许自动跃迁 */
    public boolean commandAutoWarp = true;
    /** 普通移动指令下，目标超过此距离自动跃迁 */
    public float commandAutoWarpDistance = 480f;
    /** 助推姿态下是否允许自动跃迁（距离更短） */
    public boolean commandAutoWarpBoost = true;
    public float commandAutoWarpBoostDistance = 160f;
    /** 搬运 / 装载类指令下是否允许自动跃迁 */
    public boolean commandAutoWarpPayload = true;
    public float commandAutoWarpPayloadDistance = 80f;
    /** 采矿 / 建造 / 维修 / 协助类指令下是否允许自动跃迁 */
    public boolean commandAutoWarpSupport = true;
    public float commandAutoWarpSupportDistance = 120f;
    /** 移动端：镜头拉远后自动跃迁到镜头位置（触屏没有 Alt 键） */
    public boolean mobileAutoWarp = true;
    public float mobileAutoWarpDistance = 240f;
    /** 镜头静止多久之后才允许触发，避免拖动视角时乱闪 */
    public float mobileAutoWarpTime = 20f;

    private final KeyCode warpKey = KeyCode.altLeft;
    private final KeyCode warpBanKey = KeyCode.o;

    private float warpWarmup, warpCooldownTimer;
    private float wx, wy, wRot;
    private boolean rotAllowWarp, warpCharging, autoWarping;
    private float mobileAutoWarpTimer, mobileCamXLast, mobileCamYLast;

    @Override
    public void addStats(Table t){
        super.addStats(t);
        t.add(Core.bundle.format("ability.warp.stat",
            Strings.autoFixed(warpBaseWarmup / 60f, 2),
            Strings.autoFixed(warpMinWarmup / 60f, 2),
            Strings.autoFixed(warpMinWarmupDistance / tilesize, 0),
            Strings.autoFixed(warpCooldown / 60f, 2)));
        t.row();
    }

    /** 这一帧是否正在尝试跃迁（按键 / 指挥 / 移动端三条触发路径） */
    public boolean tryingWarp(Unit unit){
        autoWarping = false;

        if(Vars.mobile && mobileAutoWarp && unit.isPlayer() && mobileAutoWarpTimer >= mobileAutoWarpTime){
            if(Mathf.dst2(unit.x, unit.y, Core.camera.position.x, Core.camera.position.y) >= mobileAutoWarpDistance * mobileAutoWarpDistance){
                wx = Core.camera.position.x;
                wy = Core.camera.position.y;
                autoWarping = true;
                return true;
            }
        }

        if(!unit.isPlayer() && commandAutoWarp && unit.isCommandable() && unit.command().targetPos != null){
            CommandAI c = unit.command();
            boolean boosting = commandAutoWarpBoost && c.hasStance(UnitStance.boost);
            boolean payloadActing = commandAutoWarpPayload &&
                c.command == UnitCommand.loopPayloadCommand ||
                c.command == UnitCommand.loadUnitsCommand ||
                c.command == UnitCommand.loadBlocksCommand ||
                c.command == UnitCommand.unloadPayloadCommand;
            boolean supportActing = commandAutoWarpSupport &&
                c.command == UnitCommand.mineCommand ||
                c.command == UnitCommand.rebuildCommand ||
                c.command == UnitCommand.assistCommand ||
                c.command == UnitCommand.repairCommand;

            float d = Mathf.dst(c.targetPos.getX(), c.targetPos.getY(), unit.x, unit.y);
            boolean shouldWarp = (boosting && d > commandAutoWarpBoostDistance) ||
                (payloadActing && d > commandAutoWarpPayloadDistance) ||
                (supportActing && d > commandAutoWarpSupportDistance) ||
                (d > commandAutoWarpDistance);

            if(shouldWarp){
                wx = c.targetPos.getX();
                wy = c.targetPos.getY();
                autoWarping = true;
                return true;
            }
        }

        return unit.isPlayer() && Core.input.keyDown(warpKey) && !Core.input.keyDown(warpBanKey)
            && !unit.isShooting() && warpCooldownTimer <= 0;
    }

    @Override
    public void update(Unit unit){
        // 助推姿态是「指挥自动跃迁」的触发条件之一，单位没有就补上
        if(commandAutoWarpBoost && !unit.type.stances.contains(UnitStance.boost)) unit.type.stances.add(UnitStance.boost);

        if(Vars.mobile && mobileAutoWarp){
            if(mobileCamXLast != Core.camera.position.x || mobileCamYLast != Core.camera.position.y){
                mobileAutoWarpTimer = 0;
            }else{
                mobileAutoWarpTimer += Time.delta;
            }
            mobileCamXLast = Core.camera.position.x;
            mobileCamYLast = Core.camera.position.y;
        }

        if(tryingWarp(unit)){
            warpCharging = true;
            warpWarmup += Time.delta;

            if(!autoWarping){
                wx = Mathf.clamp(Core.input.mouseWorldX(), 0, Vars.state.map.width * tilesize);
                wy = Mathf.clamp(Core.input.mouseWorldY(), 0, Vars.state.map.height * tilesize);
            }
            if(warpMaxDistance > 0f){
                float d = Mathf.dst(unit.x, unit.y, wx, wy);
                if(d > warpMaxDistance){
                    float s = warpMaxDistance / d;
                    wx = unit.x + (wx - unit.x) * s;
                    wy = unit.y + (wy - unit.y) * s;
                }
            }

            wRot = Mathf.atan2(wx - unit.x, wy - unit.y) * Mathf.radiansToDegrees;
            if(Mathf.chanceDelta(warpPrepareEffectChance)){
                warpPrepareEffect.at(unit.x + Mathf.range(warpPrepareEffectSpread), unit.y + Mathf.range(warpPrepareEffectSpread));
            }
            rotateToWarp(unit);
            if(warpStatus != StatusEffects.none){
                unit.apply(warpStatus, 2f);
            }

            float d = Mathf.dst(unit.x, unit.y, wx, wy);
            if(rotAllowWarp && warpWarmup >= warpMinWarmup * (d / warpMinWarmupDistance) + (warpBaseWarmup > 0 ? warpBaseWarmup : 0f)){
                warp(unit);
            }
        }else{
            warpWarmup = 0;
            warpCharging = false;
        }

        if(warpCooldownTimer > 0){
            warpCooldownTimer -= Time.delta;
            warpCharging = false;
        }
    }

    public void warp(Unit unit){
        warpCooldownTimer = warpCooldown;
        float x1 = unit.x, y1 = unit.y;
        float x2 = wx, y2 = wy;
        float r = wRot;
        float d = Mathf.dst(x1, y1, x2, y2);

        unit.x(x2);
        unit.y(y2);

        warpBeginEffect.at(x1, y1, r, warpEffectColor);
        warpEndEffect.at(x2, y2, r, warpEffectColor);

        // 三层同线、逐层变细变亮，叠起来就是一束带核心高光的能量轨迹
        Effect line = new Effect(warpTrailLife, 80000f, e -> {
            Drawf.light(x1, y1, x2, y2, warpTrailStroke * e.fout(), warpEffectColor, 1f);
            Lines.stroke(warpTrailStroke * e.fout());
            Draw.color(warpTrailColor1);
            Lines.line(x1, y1, x2, y2);
            Lines.stroke(warpTrailStroke * e.fout() * 0.6f);
            Draw.color(warpTrailColor2);
            Lines.line(x1, y1, x2, y2);
            Lines.stroke(warpTrailStroke * e.fout() * 0.3f);
            Draw.color(warpTrailColor3);
            Lines.line(x1, y1, x2, y2);
        });
        line.at(x1, y1);

        if(useAutoWarpEffect){
            WarpParticleEffect wp = new WarpParticleEffect(){{
                generalLength = d;
                lengthFrom = Math.min(20f, d / 36f);
                lengthTo = Math.min(5f, d / 144f);
                stroke = 2f;
                colorFrom = warpEffectColor;
                colorTo = warpEffectColor;
                lifetime = 60f;
                particles = Mathf.ceil(d / 14.4f);
                width = unit.hitSize() * 0.8f;
            }};
            wp.at(x1, y1, r);
        }

        warpSound.at(x1, y1, 1f, warpSoundVolume * Math.max(0.33f, d / warpMinWarmupDistance));
        warpSound.at(x2, y2, 1f, warpSoundVolume * Math.max(0.33f, d / warpMinWarmupDistance));
        Effect.shake(warpShake, warpShake * 2f, x1, y1);
        Effect.shake(warpShake, warpShake * 2f, x2, y2);
    }

    /** 转向目标，转到足够接近才放开跃迁许可 */
    public void rotateToWarp(Unit unit){
        rotAllowWarp = false;
        if(Angles.angleDist(wRot, unit.rotation()) < unit.type.rotateSpeed * unit.speedMultiplier() * Time.delta){
            unit.rotation(wRot);
            rotAllowWarp = true;
            return;
        }
        if(Mathf.sinDeg(wRot - unit.rotation()) > 0){
            unit.rotation(unit.rotation() + unit.type.rotateSpeed * unit.speedMultiplier() * Time.delta);
        }else{
            unit.rotation(unit.rotation() - unit.type.rotateSpeed * unit.speedMultiplier() * Time.delta);
        }
    }

    @Override
    public void draw(Unit unit){
        if(warpCharging && warpEndParts.size > 0){
            // 蓄力进度驱动目标端零件（如果单位配了的话）
            float prog = Mathf.clamp(warpWarmup / Math.max(warpMinWarmup, 1f));
            DrawPart.params.set(prog, prog, prog, prog, prog, prog, wx, wy, wRot);
            DrawPart.params.life = prog;
            for(DrawPart p : warpEndParts){
                p.draw(DrawPart.params);
            }
        }
    }
}
