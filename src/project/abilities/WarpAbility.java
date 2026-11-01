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
import project.SSWarpSettings;
import project.graphics.WarpParticleEffect;

import static mindustry.Vars.tilesize;

/**
 * 主动跃迁：按住 Alt 蓄力，把单位送到鼠标所指的位置。
 *
 * 移植自 Vanilla Expansion 的 WarpAbility（作者 火星人076 & 128_OTEML，
 * 该项目公开源码供学习）。逻辑与手感保持原样，改动只有三处：
 *  - 设置项改由 {@link project.SSWarpSettings} 接管（VE 用的是它自己的 VESettings），
 *    总开关、指挥自动跃迁、移动端自动跃迁、阈值、音量、特效都能在游戏里调
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
    /** 镜头静止多久之后才允许触发移动端自动跃迁，避免拖动视角时乱闪 */
    public float mobileAutoWarpTime = 20f;
    // 移动端自动跃迁的开关与距离在 SSWarpSettings 里（KEY_MOBILE / KEY_MOBILE_DIST），
    // 因为那是玩家最常改的一项，放在游戏设置里比写死在单位上更合适

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
        if(!SSWarpSettings.enabled) return false;

        if(Vars.mobile && SSWarpSettings.mobileAutoWarp && unit.isPlayer() && mobileAutoWarpTimer >= mobileAutoWarpTime){
            float md = SSWarpSettings.mobileAutoWarpDistance;
            if(Mathf.dst2(unit.x, unit.y, Core.camera.position.x, Core.camera.position.y) >= md * md){
                wx = Core.camera.position.x;
                wy = Core.camera.position.y;
                autoWarping = true;
                return true;
            }
        }

        if(!unit.isPlayer() && commandAutoWarp && SSWarpSettings.autoWarp && unit.isCommandable() && unit.command().targetPos != null){
            CommandAI c = unit.command();
            boolean boosting = commandAutoWarpBoost && c.hasStance(UnitStance.boost);
            // 括号不能省：&& 优先级高于 ||，写成 flat 版本会让开关只作用于第一个条件
            boolean payloadActing = commandAutoWarpPayload && (
                c.command == UnitCommand.loopPayloadCommand ||
                c.command == UnitCommand.loadUnitsCommand ||
                c.command == UnitCommand.loadBlocksCommand ||
                c.command == UnitCommand.unloadPayloadCommand);
            boolean supportActing = commandAutoWarpSupport && (
                c.command == UnitCommand.mineCommand ||
                c.command == UnitCommand.rebuildCommand ||
                c.command == UnitCommand.assistCommand ||
                c.command == UnitCommand.repairCommand);

            // 玩家调的是「普通移动」的阈值，其余几档按出厂比例同步缩放，
            // 这样滑块一动，整套自动跃迁的距离一起变，不会出现助推比普通还远的情况
            float scale = SSWarpSettings.autoWarpDistance / Math.max(commandAutoWarpDistance, 1f);
            float d = Mathf.dst(c.targetPos.getX(), c.targetPos.getY(), unit.x, unit.y);
            boolean shouldWarp = (boosting && d > commandAutoWarpBoostDistance * scale) ||
                (payloadActing && d > commandAutoWarpPayloadDistance * scale) ||
                (supportActing && d > commandAutoWarpSupportDistance * scale) ||
                (d > SSWarpSettings.autoWarpDistance);

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

        if(!SSWarpSettings.enabled){
            // 设置里关掉跃迁：立刻收手，蓄力清零；冷却照常走完，重新开启时不会白送一次
            warpWarmup = 0f;
            warpCharging = false;
            if(warpCooldownTimer > 0f) warpCooldownTimer -= Time.delta;
            return;
        }

        if(Vars.mobile && SSWarpSettings.mobileAutoWarp){
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
            if(SSWarpSettings.effects && Mathf.chanceDelta(warpPrepareEffectChance)){
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

        // 特效整组受设置控制：低端设备或多人卡顿时可以只留音效
        if(SSWarpSettings.effects){
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

            Effect.shake(warpShake, warpShake * 2f, x1, y1);
            Effect.shake(warpShake, warpShake * 2f, x2, y2);
        }

        if(SSWarpSettings.volume > 0.01f){
            float vol = SSWarpSettings.volume * Math.max(0.33f, d / warpMinWarmupDistance);
            warpSound.at(x1, y1, 1f, vol);
            warpSound.at(x2, y2, 1f, vol);
        }
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
