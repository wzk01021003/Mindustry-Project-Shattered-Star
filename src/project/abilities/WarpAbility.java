package project.abilities;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.ai.UnitStance;
import mindustry.ai.types.CommandAI;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.abilities.Ability;
import mindustry.entities.part.DrawPart;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.StatusEffect;
import project.SSWarpSettings;
import project.graphics.DistortionFx;
import project.graphics.WarpEffects.WarpTrailEffect;
import project.graphics.WarpParticleEffect;
import project.graphics.WarpShapes;

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
    /** 手动跃迁（按住 Alt）结束后的冷却帧数 */
    public float warpCooldown = 150f;
    /**
     * 自动跃迁（指挥 / 移动端）的冷却帧数。
     *
     * <p>不能跟手动共用 150 帧：星辉巡航速度 300 px/s，等满 2.5 秒已经飞出 750 像素，
     * 早就掉到指令自动跃迁的距离阈值以内了，等于把自动跃迁彻底废掉。
     * 40 帧足够挡住同一帧内的重复触发，又不会让 AI 白等。
     */
    public float autoWarpCooldown = 40f;
    /** 单次跃迁的最大距离（像素）。0 = 不限，VE 原版就是不限的 */
    public float warpMaxDistance = 0f;

    public Seq<DrawPart> warpEndParts = new Seq<>();
    /*
     * 三段特效 = 手写层（WarpEffects）+ 几何层（WarpShapes），由 MultiEffect 合成：
     * 手写层负责向心线、白闪、尖刺这些不好用参数描述的部分，
     * 几何层用官方的 WaveEffect 画出会呼吸的六边形 / 二十四边形能量环。
     * 三层环各自带 startDelay，错峰炸开，比原来单个圆环有层次。
     */
    public Effect warpEndEffect = WarpShapes.ARRIVE;
    public Effect warpBeginEffect = WarpShapes.BURST;
    public Effect warpPrepareEffect = WarpShapes.CHARGE;
    /** 蓄力特效每帧生成概率。0.12 在 60fps 下约每 8 帧一发，同屏叠 2~3 个，不会糊成一团 */
    public float warpPrepareEffectChance = 0.12f;
    public float warpPrepareEffectSpread = 4f;
    public Sound warpSound = Sounds.massdriver;
    /** 起跳 / 落地音效：mod 自带的 stAstroWarp1、stAstroWarp2，加载失败时退回 massdriver */
    public static Sound warpStartSound, warpEndSound;
    /**
     * 起跳撕开 / 落点合拢的屏幕扭曲。
     * 必须做成静态单例：Effect 的构造器会把自己加进 Effect.all，
     * 每次跃迁 new 一个等于往全局表里塞垃圾，越玩越卡。
     * 开关由 DistortionRenderer.addDistortion 自己判断，没开实验性渲染时是空转。
     */
    public static final Effect warpOutDistortion =
        new DistortionFx(72f, 1.3f, 14f, DistortionFx.TYPE_OUTWARD, 0.15f, 1.2f, Interp.pow2Out);
    public static final Effect warpInDistortion =
        new DistortionFx(96f, 1.7f, 20f, DistortionFx.TYPE_INWARD, 1.1f, 0f, Interp.pow3In);
    public float warpShake = 10f;
    public StatusEffect warpStatus = StatusEffects.none;

    public Color warpEffectColor = Color.valueOf("ffa665");
    /**
     * 能量轨迹与通道粒子也是静态单例，长度通过 at(..., data) 传。
     * 原因同上：Effect 构造器会把自己注册进 Effect.all，运行时 new 就是泄漏。
     */
    public static final Effect warpTrailEffect = new WarpTrailEffect();
    public static final Effect warpParticleEffect = new WarpParticleEffect();
    public boolean useAutoWarpEffect = true;

    /*
     * 落地残影：四份机体剪影，从落点向外推开再收回来。
     * 做法抄 VE 磁暴单位（lacuna/surge）warpEndParts 里的「sin 呼吸」——
     * 那边用 progress = sin(life * 0.5) * mag + offset 让零件半径先涨后落，
     * 这里把零件换成机体自己的贴图，看起来像「人还留在原地慢慢散掉」。
     */
    /** 残影个数 */
    public int ghostCount = 4;
    /** 残影向外扩散的最大半径（像素） */
    public float ghostRadius = 58f;
    /** 残影存活帧数，从落点开始算 */
    public float ghostLife = 34f;
    /** 残影最亮时的不透明度 */
    public float ghostAlpha = 0.75f;
    public Color ghostColor = Color.valueOf("ffe2bc");

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
    /** 落地残影的剩余寿命与落地朝向 */
    private float ghostTimer, ghostRot;

    /**
     * 把 mod 自带的跃迁音效挂进来，由 ShatteredStarMod.init() 调用。
     *
     * <p>stAstroWarp1 前 0.5 秒就是全部能量的八成，收得干净，拿来做「起跳」；
     * stAstroWarp2 有一层拖到两秒的低频余韵，放在「落地」正好收尾。
     * headless 下 loadSound 返回空音效，服务端不会炸。
     */
    public static void loadSounds(){
        warpStartSound = loadSoundOr("stAstroWarp1");
        warpEndSound = loadSoundOr("stAstroWarp2");
    }

    private static Sound loadSoundOr(String name){
        try{
            Sound s = Vars.tree.loadSound(name);
            if(s != null) return s;
        }catch(Throwable t){
            Log.warn("[ss] 跃迁音效 @ 加载失败，退回 massdriver", name);
        }
        return Sounds.massdriver;
    }

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

        // 冷却统一在这里判，三条路径都要受它限制。
        // VE 原版只在「玩家按 Alt」那句 return 里写了 warpCooldownTimer <= 0，
        // 指挥自动跃迁和移动端自动跃迁都直接 return true，等于 AI 可以无限连闪，
        // 而玩家自己按 Alt 反而要等满 150 帧 —— 这个不对称就是「指挥没间隔」的来源。
        if(warpCooldownTimer > 0f) return false;

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
            && !unit.isShooting();
    }

    @Override
    public void update(Unit unit){
        // 残影要先走完：下面「设置里关掉跃迁」会 early return，放后面的话残影会冻在半路
        if(ghostTimer > 0f) ghostTimer -= Time.delta;

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
                warpPrepareEffect.at(unit.x + Mathf.range(warpPrepareEffectSpread), unit.y + Mathf.range(warpPrepareEffectSpread),
                    unit.rotation, warpEffectColor);
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
        warpCooldownTimer = autoWarping ? autoWarpCooldown : warpCooldown;
        float x1 = unit.x, y1 = unit.y;
        float x2 = wx, y2 = wy;
        float r = wRot;
        float d = Mathf.dst(x1, y1, x2, y2);

        unit.x(x2);
        unit.y(y2);

        // 四份剪影从落点散开再收回，和下面的落点特效同时起步
        ghostTimer = ghostLife;
        ghostRot = r;

        // 特效整组受设置控制：低端设备或多人卡顿时可以只留音效
        if(SSWarpSettings.effects){
            warpBeginEffect.at(x1, y1, r, warpEffectColor);
            warpEndEffect.at(x2, y2, r, warpEffectColor);
            // 屏幕扭曲叠在粒子下面：起跳是空间被撕开，落地是空间合拢
            warpOutDistortion.at(x1, y1);
            warpInDistortion.at(x2, y2);

            // 轨迹与通道粒子：长度塞在 data 里，Effect 本体是复用的单例
            warpTrailEffect.at(x1, y1, r, warpEffectColor, d);
            if(useAutoWarpEffect){
                warpParticleEffect.at(x1, y1, r, warpEffectColor, d);
            }

            Effect.shake(warpShake, warpShake * 2f, x1, y1);
            Effect.shake(warpShake, warpShake * 2f, x2, y2);
        }

        if(SSWarpSettings.volume > 0.01f){
            float vol = SSWarpSettings.volume * Math.max(0.33f, d / warpMinWarmupDistance);
            // 起点与终点各一声：起点短促（stAstroWarp1），终点带余韵（stAstroWarp2）
            (warpStartSound == null ? warpSound : warpStartSound).at(x1, y1, 1f, vol);
            (warpEndSound == null ? warpSound : warpEndSound).at(x2, y2, 1f, vol * 0.95f);
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
        drawGhosts(unit);

        if(warpCharging && warpEndParts.size > 0){
            // 蓄力进度驱动目标端零件（如果单位配了的话）
            float prog = Mathf.clamp(warpWarmup / Math.max(warpMinWarmup, 1f));
            DrawPart.params.set(prog, prog, prog, prog, prog, prog, wx, wy, wRot);
            DrawPart.params.life = prog;
            for(DrawPart p : warpEndParts){
                p.draw(DrawPart.params);
            }
        }

        if(!warpCharging || !SSWarpSettings.effects) return;

        // 准星单独给了开关：指挥一整队同时自动跃迁时，屏幕上会同时出现十几个落点提示，
        // 除了挡住战场没有任何帮助。可以全关，也可以只留玩家自己操控的那台。
        if(!SSWarpSettings.reticle) return;
        if(SSWarpSettings.reticlePlayerOnly && !unit.isPlayer()) return;

        // 蓄力期间在落点上画准星：光靠机体冒粒子，玩家判断不出会跳到哪
        float dist = Mathf.dst(unit.x, unit.y, wx, wy);
        float need = warpBaseWarmup + warpMinWarmup * (dist / warpMinWarmupDistance);
        float prog = Mathf.clamp(warpWarmup / Math.max(need, 1f));
        float pulse = 0.65f + 0.35f * Mathf.sin(Time.time * 0.22f);

        float oldZ = Draw.z();
        Draw.z(Layer.effect);

        // 单位到落点的虚线，颜色随脉冲呼吸
        Tmp.c1.set(warpEffectColor).a(0.35f + 0.3f * pulse);
        Drawf.dashLine(Tmp.c1, unit.x, unit.y, wx, wy);

        // 落点双环
        Draw.color(warpEffectColor);
        Draw.alpha(0.8f * pulse);
        Lines.stroke(1.6f);
        Lines.circle(wx, wy, 11f + pulse * 2f);
        Draw.alpha(0.3f * pulse);
        Lines.stroke(1f);
        Lines.circle(wx, wy, 17f + pulse * 3f);

        // 两段反向旋转的弧：蓄力越满，弧越长，看起来像在慢慢「咬合」
        Draw.alpha(0.9f);
        Lines.stroke(2f);
        float arc = 0.16f + 0.14f * prog;
        float rot = Time.time * 2.2f;
        Lines.arc(wx, wy, 15f, arc, rot);
        Lines.arc(wx, wy, 15f, arc, rot + 180f);

        // 十字准星
        Lines.stroke(1.4f);
        Lines.line(wx - 6f, wy, wx - 2f, wy);
        Lines.line(wx + 2f, wy, wx + 6f, wy);
        Lines.line(wx, wy - 6f, wx, wy - 2f);
        Lines.line(wx, wy + 2f, wx, wy + 6f);

        // 机头已经对准：整圈闪白，等于告诉玩家「现在松手就能跳」
        if(rotAllowWarp){
            Draw.color(Color.white);
            Draw.alpha(0.75f * pulse);
            Lines.stroke(1.2f);
            Lines.circle(wx, wy, 20f + pulse * 2f);
        }

        Draw.z(oldZ);
        Draw.color();
        Draw.alpha(1f);
    }

    /**
     * 落地残影：{@link #ghostCount} 份机体剪影，从落点向外推开再收回来。
     *
     * <p>抄的是 VE 磁暴单位 lacuna/surge 的 warpEndParts：那边四个零件都用
     * {@code progress = sin(life * 0.5) * mag + offset} 让半径先涨后落，
     * 机体周围始终有东西在收放。这里把零件换成机体自己的贴图，
     * 比纯几何线条更像「人还留在原地、慢慢散掉」。
     *
     * <p>为什么不写成 Effect：Effect 是全局单例，既不能持有单位引用
     * （单位会被回收复用），也拿不到每帧的朝向和 region。
     *
     * <p>{@code sin(πt)} 是这里的关键：t 从 0 走到 1 时它先增后减，
     * 一次函数就能把「散开—缩回」讲完，两端的值为 0，天然没有突兀的起止。
     */
    private void drawGhosts(Unit unit){
        if(ghostTimer <= 0f || !SSWarpSettings.effects || ghostCount <= 0) return;
        TextureRegion reg = unit.type.region;
        if(reg == null) return;

        float t = Mathf.clamp(1f - ghostTimer / Math.max(ghostLife, 1f));
        float wave = Mathf.sin(t * Mathf.pi);
        // 位移开 0.6 次方：影子更快离身，否则大半段时间都挤在机体旁边，看着只是抖了一下
        float r = ghostRadius * Mathf.pow(wave, 0.6f);
        // 亮度单独再压一道曲线，两端收得比位移更快，起落不会突然冒出来
        float alpha = ghostAlpha * Mathf.pow(wave, 1.15f);
        // 越到后面越收缩一点，像影子被拉回机身
        float scl = 1f - 0.12f * t;

        // Ability.draw 被调用时 Draw.z 就是本单位所在层，往下压一点让影子排在机体后面
        float oldZ = Draw.z();
        Draw.z(oldZ - 0.1f);
        Draw.blend(Blending.additive);
        Draw.color(ghostColor);

        for(int i = 0; i < ghostCount; i++){
            // 从 45° 起均分，末尾那个 t * 26f 让四个影子整体缓慢转一点，不然像静态图标
            float ang = 45f + 360f / ghostCount * i + t * 26f;
            Draw.alpha(alpha);
            Draw.rect(reg,
                unit.x + Mathf.cosDeg(ang) * r,
                unit.y + Mathf.sinDeg(ang) * r,
                reg.width * scl, reg.height * scl,
                ghostRot - 90f);
        }

        Draw.blend();
        Draw.color();
        Draw.alpha(1f);
        Draw.z(oldZ);
    }
}
