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
import arc.struct.LongMap;
import arc.struct.LongSeq;
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
import mindustry.gen.Groups;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.entities.Units;
import mindustry.entities.units.WeaponMount;
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
 * 该项目公开源码供学习）。逻辑与手感保持原样，改动有四处：
 *  - 设置项改由 {@link project.SSWarpSettings} 接管（VE 用的是它自己的 VESettings），
 *    总开关、指挥自动跃迁、移动端自动跃迁、阈值、音量、特效都能在游戏里调
 *  - 配色从青绿 00ffce 换成星辉的橙金 ffa665，与本 mod 机体辉光同色系
 *  - 新增 warpMaxDistance，默认 0（不限），需要时可用来封顶跃迁距离
 *  - 每台单位的状态独立存放（见 {@link WarpState}），VE 原版是能力实例上的字段，
 *    同型单位会共享同一份蓄力值，一群 AI 会叠到同一个落点
 *
 * 手感设计：
 *  - 蓄力时间 = 基础值 + 距离 × 系数，跳得越远越久，不能贴脸乱闪
 *  - 默认必须先把机头转到目标方向才允许跃迁（rotAllowWarp），
 *    避免背对着瞬移造成的方向突变；大机体可以用 warpRequireFacing 关掉，
 *    理由见该字段的注释
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
    /**
     * 蓄力时是否主动把机头转向跃迁方向。
     *
     * <p>这一项管「转不转」，{@link #warpRequireFacing} 管「转不到就不许起跳」，两件事。
     * 开着它，玩家按住 Alt 之后机体会自己朝落点扭头，松手瞬移出去时是朝前飞的；
     * 关掉就是保持原朝向平移，视觉上像被一只看不见的手拎走。
     */
    public boolean warpTurnToTarget = true;
    /**
     * 是否要求机头先对准目标才允许跃迁。
     *
     * <p>小机体（星辉 rotateSpeed 5）转 180° 只要 0.6 秒，蓄力期顺手就转完了，
     * 要求对准能防止背对着瞬移造成的朝向突变。
     * 但日蚀这种 T5 轰炸机 rotateSpeed 只有 1，转 180° 要整整 3 秒 ——
     * 比蓄力还久，玩家按住 Alt 会先卡在转向里，跃迁等于不可用。
     *
     * <p>所以日蚀把这一项关掉。关掉不等于不转：它会走
     * {@link #rotateToWarp(Unit, WarpState, float)} 的自适应转速，
     * 把剩余角度摊到剩余蓄力帧上，蓄力结束时刚好对正，起跳前再硬对齐一次兜底。
     * 也就是说「不管现在朝哪，跳出去一定是朝落点的」。
     * 它的武器是 rotate = true 的炮塔，落地后照样能立刻开火，不会因为朝向不对而哑火。
     */
    public boolean warpRequireFacing = true;
    /**
     * 是否要求「当前没在开火」才允许跃迁。
     *
     * <p>小机体开火时间短，等一个射击间歇很容易，这条能挡住「边打边闪」。
     * 但日蚀三门炮塔都是自动索敌的 rotate 武器，进战场后几乎帧帧在开火，
     * 保留这条等于玩家永远按不出跃迁 —— 一个只有在没敌人时才能用的位移技能没有意义。
     *
     * <p>关掉之后不是白送：跃迁瞬间把所有炮塔的装填推到 {@link #warpWeaponLockout}，
     * 落地有半秒多的火力空窗，既保留「位移要付代价」的手感，又不至于让技能直接废掉。
     */
    public boolean warpRequireIdleWeapons = true;
    /** 不要求停止开火时，跃迁后强行压上的武器装填帧数 */
    public float warpWeaponLockout = 36f;
    /**
     * 蓄力期间每帧速度保留比例，1 = 不刹车。
     *
     * <p>大机体蓄力两秒，如果还能全速巡航，玩家按 Alt 选点的两秒里机体已经飘出去半个屏，
     * 落点提示一直在动，很难瞄准。给一个温和的阻尼（0.985，约 60 帧降到四成速度），
     * 观感上像「机体在定锚」，也不至于把玩家钉死在原地。
     */
    public float warpChargeBrake = 1f;

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
    /**
     * 蓄力期间在机体周围额外刷的棱形碎片（VE 幻象同款）。
     *
     * <p>VE 幻象入场时，机体周围每一帧都在冒小菱形光片，那是它「空间在碎」的主要观感来源。
     * 星辉机身只有 48px，飘这么大的棱形会盖住整个机体，所以默认 null；
     * 日蚀（320px）配 {@code WarpShapes.SPLINTER_CHARGE_LARGE}。
     */
    public Effect warpPrepareSplinter;
    public float warpPrepareSplinterChance = 0.18f;
    public Sound warpSound = Sounds.massdriver;
    /** 起跳 / 落地音效：mod 自带的 stAstroWarp1、stAstroWarp2，加载失败时退回 massdriver */
    public static Sound warpStartSound, warpEndSound;
    /** 大机体专用的落地重音 stAstroWarp3，叠在 stAstroWarp2 上，低音更厚 */
    public static Sound warpHeavySound;
    /** 落地时是否额外叠一层重音（大型单位开，小飞机不开，否则同屏一群星辉会吵死） */
    public boolean useHeavyLandingSound = false;
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
    public static final Effect warpTrailEffectLarge = new WarpTrailEffect(1.35f);
    public static final Effect warpParticleEffect = new WarpParticleEffect();
    public static final Effect warpParticleEffectLarge = new WarpParticleEffect(5.8f);
    /** 轨迹特效，可被单位覆盖成大号版本 */
    public Effect trailEffect = warpTrailEffect;
    public Effect trailParticleEffect = warpParticleEffect;
    public boolean useAutoWarpEffect = true;

    /*
     * 落地残影：同一张机体贴图分两批，一批向外推开，一批晚 20 帧从最远处收回。
     *
     * <p>结构照 VE 幻象（strengthened/meta-spawn.json）的入场：那边是 8 个 RadialEffect，
     * 每个都拿整张机体贴图当 ParticleEffect —— 4 个 circleOut 向外走（21 帧），
     * 4 个延迟 20 帧用 circleIn 反向走（20 帧）。两条曲线一放一收，
     * 叠起来就是「散开 → 停一拍 → 收拢」。
     * 我原来只写了一条 sin 曲线，四个影子同出同回，缺少中间那一拍。
     *
     * <p>尺寸按机体原贴图 1:1 画（{@code reg.width/height}），不再缩到 0.88：
     * 影子比本体小一圈，看起来像远处的另一架飞机，而不是「我刚从这里离开」。
     *
     * <p>为什么不写成 Effect：Effect 是全局单例，既不能持有单位引用
     * （单位会被对象池复用），也拿不到每帧的朝向和 region。
     */
    /** 每一批的残影个数（外扩、内收各这么多份） */
    public int ghostCount = 4;
    /** 残影离机体的最大距离（像素） */
    public float ghostRadius = 58f;
    /** 外扩批次的扩散时长（帧） */
    public float ghostExpandTime = 21f;
    /** 内收批次晚多少帧开始 */
    public float ghostContractDelay = 20f;
    /** 内收批次收回中心的时长（帧） */
    public float ghostContractTime = 20f;
    /** 残影存活帧数；比「延迟 + 内收时长」短时会被顶到那个值 */
    public float ghostLife = 41f;
    /** 残影最亮时的不透明度（外扩批次起点） */
    public float ghostAlpha = 0.75f;
    public Color ghostColor = Color.valueOf("ffe2bc");

    /**
     * 落点气浪：把周围的非友方单位往外推。
     *
     * <p>灵感来自 VE 幻象 / 超维的「跃迁入场」——那两台不是按键位移，
     * 而是隐形信标在落点自爆、打出一圈冲击波把场子清出来。
     * 我们不要那么夸张（那是 BOSS 演出），但大机体砸下来一点风都没有太假，
     * 所以给一圈**不造成伤害**的推挤：只改速度，不改血量。
     *
     * <p>为什么不做成伤害：日蚀本身火力已经够 T5 了，再送一发免费 AOE，
     * 玩家就会把它当一次性炸弹用，跃迁从「机动手段」变成「输出手段」，
     * 那这个小功能就把整台单位的定位带歪了。
     */
    public float warpImpactRadius = 0f;
    /** 气浪把满强度单位推开的速度（像素/帧） */
    public float warpImpactForce = 0f;

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

    /**
     * 每台单位各存一份跃迁状态。
     *
     * <p>为什么必须这么写：Mindustry 的 Ability 是挂在 UnitType 上的**共享实例**，
     * 一个 {@code new WarpAbility()} 会被该类型的所有单位同时使用。
     * VE 原版把 warpWarmup / wx / wy 直接写成实例字段，
     * 结果是一群同型 AI 会共用同一个蓄力值和同一个落点坐标 ——
     * 指挥十二台日蚀，十二台会同时起跳、全部砸在鼠标最后停的那个点上。
     * 原版 Ability 里这种写法很常见（ShieldArcAbility 也这样），
     * 因为它们的状态是连续量，串了也看不出来；跃迁是离散事件，串了就是堆叠。
     */
    public static class WarpState{
        /**
         * 这份状态属于哪台单位。
         *
         * <p>单位 id 会被回收复用（对象池 + EntityGroup 的 id 分配），
         * 光用 id 当键的话，新造的日蚀可能一出生就继承上一台尸体的冷却。
         * 存一份引用做比对，id 撞了直接换新状态。
         */
        public transient Unit owner;
        public float warmup, cooldown;
        public float wx, wy, wRot;
        public boolean rotAllow, charging, auto;
        public float mobileTimer, camX, camY;
        public float ghostTimer, ghostRot;
    }

    /** unit.id → 状态 */
    private final transient LongMap<WarpState> states = new LongMap<>();
    private float cleanupTimer;

    /** 取状态，没有就建；id 被别的单位复用了也重建 */
    private WarpState st(Unit unit){
        WarpState s = states.get(unit.id);
        if(s == null || s.owner != unit){
            s = new WarpState();
            s.owner = unit;
            states.put(unit.id, s);
        }
        return s;
    }

    /** 只读地取状态，不创建（draw 每帧都会调用，不能顺手建对象） */
    private WarpState peek(Unit unit){
        WarpState s = states.get(unit.id);
        return s != null && s.owner == unit ? s : null;
    }

    /**
     * 把 mod 自带的跃迁音效挂进来，由 ShatteredStarMod.init() 调用。
     *
     * <p>stAstroWarp1 前 0.5 秒就是全部能量的八成，收得干净，拿来做「起跳」；
     * stAstroWarp2 有一层拖到两秒的低频余韵，放在「落地」正好收尾；
     * stAstroWarp3 更沉，只给大型单位叠在 2 上面，让落地的份量跟体型对得上。
     * headless 下 loadSound 返回空音效，服务端不会炸。
     */
    public static void loadSounds(){
        warpStartSound = loadSoundOr("stAstroWarp1");
        warpEndSound = loadSoundOr("stAstroWarp2");
        warpHeavySound = loadSoundOr("stAstroWarp3");
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
    public boolean tryingWarp(Unit unit, WarpState s){
        s.auto = false;
        if(!SSWarpSettings.enabled) return false;

        // 冷却统一在这里判，三条路径都要受它限制。
        // VE 原版只在「玩家按 Alt」那句 return 里写了 warpCooldownTimer <= 0，
        // 指挥自动跃迁和移动端自动跃迁都直接 return true，等于 AI 可以无限连闪，
        // 而玩家自己按 Alt 反而要等满 150 帧 —— 这个不对称就是「指挥没间隔」的来源。
        if(s.cooldown > 0f) return false;

        if(Vars.mobile && SSWarpSettings.mobileAutoWarp && unit.isPlayer() && s.mobileTimer >= mobileAutoWarpTime){
            float md = SSWarpSettings.mobileAutoWarpDistance;
            if(Mathf.dst2(unit.x, unit.y, Core.camera.position.x, Core.camera.position.y) >= md * md){
                s.wx = Core.camera.position.x;
                s.wy = Core.camera.position.y;
                s.auto = true;
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
                s.wx = c.targetPos.getX();
                s.wy = c.targetPos.getY();
                s.auto = true;
                return true;
            }
        }

        return unit.isPlayer() && Core.input.keyDown(warpKey) && !Core.input.keyDown(warpBanKey)
            && (!warpRequireIdleWeapons || !unit.isShooting());
    }

    @Override
    public void update(Unit unit){
        WarpState s = st(unit);

        // 残影要先走完：下面「设置里关掉跃迁」会 early return，放后面的话残影会冻在半路
        if(s.ghostTimer > 0f) s.ghostTimer -= Time.delta;
        cleanup();

        // 助推姿态是「指挥自动跃迁」的触发条件之一，单位没有就补上
        if(commandAutoWarpBoost && !unit.type.stances.contains(UnitStance.boost)) unit.type.stances.add(UnitStance.boost);

        if(!SSWarpSettings.enabled){
            // 设置里关掉跃迁：立刻收手，蓄力清零；冷却照常走完，重新开启时不会白送一次
            s.warmup = 0f;
            s.charging = false;
            if(s.cooldown > 0f) s.cooldown -= Time.delta;
            return;
        }

        if(Vars.mobile && SSWarpSettings.mobileAutoWarp){
            if(s.camX != Core.camera.position.x || s.camY != Core.camera.position.y){
                s.mobileTimer = 0;
            }else{
                s.mobileTimer += Time.delta;
            }
            s.camX = Core.camera.position.x;
            s.camY = Core.camera.position.y;
        }

        if(tryingWarp(unit, s)){
            s.charging = true;
            s.warmup += Time.delta;

            if(!s.auto){
                s.wx = Mathf.clamp(Core.input.mouseWorldX(), 0, Vars.state.map.width * tilesize);
                s.wy = Mathf.clamp(Core.input.mouseWorldY(), 0, Vars.state.map.height * tilesize);
            }
            if(warpMaxDistance > 0f){
                float d = Mathf.dst(unit.x, unit.y, s.wx, s.wy);
                if(d > warpMaxDistance){
                    float k = warpMaxDistance / d;
                    s.wx = unit.x + (s.wx - unit.x) * k;
                    s.wy = unit.y + (s.wy - unit.y) * k;
                }
            }

            s.wRot = Mathf.atan2(s.wx - unit.x, s.wy - unit.y) * Mathf.radiansToDegrees;
            if(SSWarpSettings.effects && Mathf.chanceDelta(warpPrepareEffectChance)){
                warpPrepareEffect.at(unit.x + Mathf.range(warpPrepareEffectSpread), unit.y + Mathf.range(warpPrepareEffectSpread),
                    unit.rotation, warpEffectColor);
            }
            // 棱形碎片铺得比粒子更开：粒子是机身内部在震，棱形是机体周围的空间在裂，
            // 两者贴在一起反而分不出谁是谁
            if(SSWarpSettings.effects && warpPrepareSplinter != null && Mathf.chanceDelta(warpPrepareSplinterChance)){
                warpPrepareSplinter.at(unit.x + Mathf.range(warpPrepareEffectSpread * 2f),
                    unit.y + Mathf.range(warpPrepareEffectSpread * 2f), unit.rotation, warpEffectColor);
            }

            float d = Mathf.dst(unit.x, unit.y, s.wx, s.wy);
            float need = warmupNeeded(d);

            if(warpTurnToTarget){
                // 把「还要多少帧才起跳」交给转向，让它自己决定要不要提速。
                // 以前这里只在 warpRequireFacing 时转，不要求对准的单位全程保持原朝向，
                // 瞬移出去就是倒着飞 / 侧着飞，落点一转身三门炮还要再找一次目标
                rotateToWarp(unit, s, need - s.warmup);
            }else{
                s.rotAllow = true;
            }

            if(warpStatus != StatusEffects.none){
                unit.apply(warpStatus, 2f);
            }

            // 蓄力刹车：只削速度不改朝向，玩家仍然能用推进器微调位置
            if(warpChargeBrake < 1f && s.warmup > 20f){
                unit.vel.scl(Mathf.pow(warpChargeBrake, Time.delta));
            }

            if(s.rotAllow && s.warmup >= need){
                warp(unit, s);
            }
        }else{
            s.warmup = 0;
            s.charging = false;
        }

        if(s.cooldown > 0){
            s.cooldown -= Time.delta;
            s.charging = false;
        }
    }

    /**
     * 按距离算出这次跃迁需要蓄多少帧。
     *
     * <p>原来这串表达式直接写在 if 里，转向也要用它（要算出「还剩多少帧」），
     * 提出来避免两处各写一遍、以后改参数只改一处漏一处。
     */
    private float warmupNeeded(float dist){
        return warpMinWarmup * (dist / Math.max(warpMinWarmupDistance, 1f))
            + (warpBaseWarmup > 0f ? warpBaseWarmup : 0f);
    }

    public void warp(Unit unit, WarpState s){
        s.cooldown = s.auto ? autoWarpCooldown : warpCooldown;
        float x1 = unit.x, y1 = unit.y;
        float x2 = s.wx, y2 = s.wy;
        float r = s.wRot;
        float d = Mathf.dst(x1, y1, x2, y2);

        // 起跳前硬对齐：自适应转速理论上已经转到位，但玩家最后几帧还在甩鼠标的话，
        // 目标方向本身就在变。这里兜一次底，保证瞬移出去一定朝着落点，
        // 而不是带着一个正在追的偏角飞出去
        if(warpTurnToTarget) unit.rotation(r);

        unit.x(x2);
        unit.y(y2);

        // 四份剪影从落点散开再收回，和下面的落点特效同时起步
        s.ghostTimer = ghostTotalLife();
        s.ghostRot = r;

        // 允许边打边跳的单位：落地立刻把炮塔压进空窗，位移的代价从「不能开火」换成「跳完要重新装」
        if(!warpRequireIdleWeapons && warpWeaponLockout > 0f){
            for(WeaponMount mount : unit.mounts()){
                if(mount.reload < warpWeaponLockout) mount.reload = warpWeaponLockout;
            }
        }

        // 气浪：只推不伤，见 warpImpactRadius 的注释
        if(warpImpactRadius > 0f && warpImpactForce > 0f){
            float rad = warpImpactRadius;
            // nearbyEnemies 自己就会剔掉同队单位，不用再手写 team 判断
            Units.nearbyEnemies(unit.team, x2, y2, rad, other -> {
                if(other.dead) return;
                float od = Mathf.dst(other.x, other.y, x2, y2);
                if(od > rad) return;
                // 越近推得越狠，边缘为 0，避免出现一刀切的边界
                float f = 1f - od / rad;
                f *= f;
                float ang = Mathf.atan2(other.x - x2, other.y - y2);
                other.vel.add(Mathf.cos(ang) * warpImpactForce * f, Mathf.sin(ang) * warpImpactForce * f);
            });
        }

        // 特效整组受设置控制：低端设备或多人卡顿时可以只留音效
        if(SSWarpSettings.effects){
            warpBeginEffect.at(x1, y1, r, warpEffectColor);
            warpEndEffect.at(x2, y2, r, warpEffectColor);
            // 屏幕扭曲叠在粒子下面：起跳是空间被撕开，落地是空间合拢
            warpOutDistortion.at(x1, y1);
            warpInDistortion.at(x2, y2);

            // 轨迹与通道粒子：长度塞在 data 里，Effect 本体是复用的单例
            trailEffect.at(x1, y1, r, warpEffectColor, d);
            if(useAutoWarpEffect){
                trailParticleEffect.at(x1, y1, r, warpEffectColor, d);
            }

            Effect.shake(warpShake, warpShake * 2f, x1, y1);
            Effect.shake(warpShake, warpShake * 2f, x2, y2);
        }

        if(SSWarpSettings.volume > 0.01f){
            float vol = SSWarpSettings.volume * Math.max(0.33f, d / warpMinWarmupDistance);
            // 起点与终点各一声：起点短促（stAstroWarp1），终点带余韵（stAstroWarp2）
            (warpStartSound == null ? warpSound : warpStartSound).at(x1, y1, 1f, vol);
            (warpEndSound == null ? warpSound : warpEndSound).at(x2, y2, 1f, vol * 0.95f);
            if(useHeavyLandingSound && warpHeavySound != null){
                // 重音只放落点，音量再压一点：它只是垫在下面的一层，不是主角
                warpHeavySound.at(x2, y2, 1f, vol * 0.8f);
            }
        }
    }

    /**
     * 转向目标。
     *
     * <p>基础转速是单位自己的 {@code rotateSpeed}，但只有「转得到就转、转不到就算了」时够用。
     * 日蚀 rotateSpeed = 1，蓄力 120 帧最多转 120°，背对目标时根本对不正，
     * 起跳瞬间硬掰就会看到机头「啪」地跳 60°。
     *
     * <p>所以加一道自适应：把当前剩余角度摊到剩余蓄力帧上，得到「刚好能转完」的转速，
     * 取它和原转速的较大值。角度越接近目标，这个值越小，最后自然会回落到原转速附近，
     * 曲线是收敛的，不会来回摆。
     *
     * @param remain 蓄力还剩多少帧（&lt;= 0 表示已经到点，交给调用方处理）
     */
    public void rotateToWarp(Unit unit, WarpState s, float remain){
        s.rotAllow = false;
        float spd = unit.type.rotateSpeed * unit.speedMultiplier() * Time.delta;
        float dist = Angles.angleDist(s.wRot, unit.rotation());

        // 不强制对准的单位不会等，必须在这点时间里把角度吃完；
        // 强制对准的单位保持原转速，让「转够了才亮白圈」这个节奏不变
        if(!warpRequireFacing && remain > 0f){
            spd = Math.max(spd, dist / remain);
        }

        if(dist <= spd){
            unit.rotation(s.wRot);
            s.rotAllow = true;
            return;
        }
        // sinDeg 取方向：正数说明目标在顺时针一侧。
        // spd 已经是「本帧应转的角度」（rotateSpeed 以 60fps 为基准，乘过 Time.delta），
        // 自适应那一项是角度除以帧数，单位同样是「度/帧」，两者可以直接比
        unit.rotation(unit.rotation() + (Mathf.sinDeg(s.wRot - unit.rotation()) > 0 ? spd : -spd));
    }

    @Override
    public void draw(Unit unit){
        WarpState s = peek(unit);
        if(s == null) return;

        drawGhosts(unit, s);

        if(s.charging && warpEndParts.size > 0){
            // 蓄力进度驱动目标端零件（如果单位配了的话）
            float prog = Mathf.clamp(s.warmup / Math.max(warpMinWarmup, 1f));
            DrawPart.params.set(prog, prog, prog, prog, prog, prog, s.wx, s.wy, s.wRot);
            DrawPart.params.life = prog;
            for(DrawPart p : warpEndParts){
                p.draw(DrawPart.params);
            }
        }

        if(!s.charging || !SSWarpSettings.effects) return;

        // 准星单独给了开关：指挥一整队同时自动跃迁时，屏幕上会同时出现十几个落点提示，
        // 除了挡住战场没有任何帮助。可以全关，也可以只留玩家自己操控的那台。
        if(!SSWarpSettings.reticle) return;
        if(SSWarpSettings.reticlePlayerOnly && !unit.isPlayer()) return;

        // 准星尺寸跟着机体走：星辉 48px 的贴图配 20px 的环刚好，
        // 日蚀 320px 的贴图套同一个环就成了脚边的小圆点，落点在哪根本看不清
        float rs = Mathf.clamp(unit.type.region == null ? 1f : unit.type.region.width / 48f, 0.85f, 8f);

        // 蓄力期间在落点上画准星：光靠机体冒粒子，玩家判断不出会跳到哪
        float dist = Mathf.dst(unit.x, unit.y, s.wx, s.wy);
        float need = warpBaseWarmup + warpMinWarmup * (dist / warpMinWarmupDistance);
        float prog = Mathf.clamp(s.warmup / Math.max(need, 1f));
        float pulse = 0.65f + 0.35f * Mathf.sin(Time.time * 0.22f);

        float oldZ = Draw.z();
        Draw.z(Layer.effect);

        // 单位到落点的虚线，颜色随脉冲呼吸
        Tmp.c1.set(warpEffectColor).a(0.35f + 0.3f * pulse);
        Drawf.dashLine(Tmp.c1, unit.x, unit.y, s.wx, s.wy);

        // 落点双环
        Draw.color(warpEffectColor);
        Draw.alpha(0.8f * pulse);
        Lines.stroke(1.6f * Mathf.clamp(rs * 0.7f, 1f, 2.2f));
        Lines.circle(s.wx, s.wy, (11f + pulse * 2f) * rs);
        Draw.alpha(0.3f * pulse);
        Lines.stroke(1f * Mathf.clamp(rs * 0.7f, 1f, 2.2f));
        Lines.circle(s.wx, s.wy, (17f + pulse * 3f) * rs);

        // 两段反向旋转的弧：蓄力越满，弧越长，看起来像在慢慢「咬合」
        Draw.alpha(0.9f);
        Lines.stroke(2f * Mathf.clamp(rs * 0.6f, 1f, 2f));
        float arc = 0.16f + 0.14f * prog;
        float rot = Time.time * 2.2f;
        Lines.arc(s.wx, s.wy, 15f * rs, arc, rot);
        Lines.arc(s.wx, s.wy, 15f * rs, arc, rot + 180f);

        // 十字准星
        Lines.stroke(1.4f * Mathf.clamp(rs * 0.6f, 1f, 2f));
        float cross = 6f * rs;
        Lines.line(s.wx - cross, s.wy, s.wx - cross * 0.35f, s.wy);
        Lines.line(s.wx + cross * 0.35f, s.wy, s.wx + cross, s.wy);
        Lines.line(s.wx, s.wy - cross, s.wx, s.wy - cross * 0.35f);
        Lines.line(s.wx, s.wy + cross * 0.35f, s.wx, s.wy + cross);

        // 机头已经对准：整圈闪白，等于告诉玩家「现在松手就能跳」
        if(s.rotAllow){
            Draw.color(Color.white);
            Draw.alpha(0.75f * pulse);
            Lines.stroke(1.2f * Mathf.clamp(rs * 0.6f, 1f, 2f));
            Lines.circle(s.wx, s.wy, (20f + pulse * 2f) * rs);
        }

        Draw.z(oldZ);
        Draw.color();
        Draw.alpha(1f);
    }

    /**
     * 落地残影：{@link #ghostCount} 份机体剪影向外推开，
     * 延迟后再来一批从最远处收回中心（结构见字段上方的注释）。
     *
     * <p>两批共用同一条半径 R：外扩批在 {@code ghostExpandTime} 帧时正好停在 R，
     * 内收批在 {@code ghostContractDelay} 帧时也正好从 R 起步，
     * 位置和亮度都接得上，看上去是一批影子停了一拍之后被吸回去，而不是换了一批。
     *
     * <p>亮度：外扩段从 {@code ghostAlpha} 淡到它的一半，内收段接着从一半淡到 0。
     * 首尾相接，中间没有跳变。
     */
    private void drawGhosts(Unit unit, WarpState s){
        if(s.ghostTimer <= 0f || !SSWarpSettings.effects || ghostCount <= 0) return;
        TextureRegion reg = unit.type.region;
        if(reg == null) return;

        float life = ghostTotalLife();
        float elapsed = life - s.ghostTimer;
        if(elapsed < 0f) return;

        // 两批严格交接：外扩在 handoff 帧走到头，内收在 handoff 帧从最远处接手。
        // 如果让两组各按各的时长跑，中段会有几帧同时出现 8 份 320px 贴图，
        // 叠起来就是一团深色，既看不清轮廓也看不出「四份」
        float handoff = Math.max(1f, Math.min(ghostExpandTime, ghostContractDelay));
        float contractT = Math.max(ghostContractTime, 1f);

        // 外扩：circleOut 起步快、收尾慢，影子「弹」出去之后稳稳停住
        float ep = Interp.circleOut.apply(Mathf.clamp(elapsed / handoff, 0f, 1f));
        // 内收：circleIn 起步慢、收尾快，像被机体吸回去
        float cp = Mathf.clamp((elapsed - ghostContractDelay) / contractT, 0f, 1f);
        float cpi = Interp.circleIn.apply(cp);

        boolean expanding = elapsed < handoff;
        boolean contracting = elapsed >= ghostContractDelay && cp < 1f;

        float ea = ghostAlpha * (1f - 0.5f * ep);
        float ca = ghostAlpha * 0.5f * (1f - cp);

        // Ability.draw 被调用时 Draw.z 就是本单位所在层，往下压一点让影子排在机体后面
        float oldZ = Draw.z();
        Draw.z(oldZ - 0.1f);
        Draw.blend(Blending.additive);
        Draw.color(ghostColor);

        for(int i = 0; i < ghostCount; i++){
            // 从 45° 起，四个影子落在斜向，不会正压住机头和机尾
            float ang = 45f + 360f / ghostCount * i;
            float cx = Mathf.cosDeg(ang), cy = Mathf.sinDeg(ang);

            // 两批会有一两帧重叠：外扩还没完全停住、内收已经开始，
            // 交接处不是硬切，看起来是「散到位的同时就被往回吸」
            if(expanding && ea > 0.01f){
                float r = ghostRadius * ep;
                Draw.alpha(ea);
                Draw.rect(reg, unit.x + cx * r, unit.y + cy * r, reg.width, reg.height, s.ghostRot - 90f);
            }
            if(contracting && ca > 0.01f){
                float r = ghostRadius * (1f - cpi);
                Draw.alpha(ca);
                Draw.rect(reg, unit.x + cx * r, unit.y + cy * r, reg.width, reg.height, s.ghostRot - 90f);
            }
        }

        Draw.blend();
        Draw.color();
        Draw.alpha(1f);
        Draw.z(oldZ);
    }

    /** 两批加起来的总帧数；配置写小了就以总时长为准，避免内收批画到一半被切断 */
    public float ghostTotalLife(){
        return Math.max(ghostLife, ghostContractDelay + ghostContractTime + 1f);
    }

    /**
     * 清理死掉单位的状态。
     *
     * <p>LongMap 没有自动回收，不清理的话一局打下来会攒几千个空壳。
     * 每 5 秒扫一次，且只在表比较大时才扫 —— 常规战斗里同屏跃迁单位也就十几台，
     * 走不到这个分支，不会给主循环添成本。
     */
    private void cleanup(){
        if(states.size < 32) return;
        cleanupTimer += Time.delta;
        if(cleanupTimer < 300f) return;
        cleanupTimer = 0f;

        LongSeq dead = new LongSeq();
        for(LongMap.Entry<WarpState> e : states){
            Unit u = Groups.unit.getByID((int)e.key);
            if(u == null || u.dead || u.type == null) dead.add(e.key);
        }
        for(int i = 0; i < dead.size; i++){
            states.remove(dead.get(i));
        }
    }
}
