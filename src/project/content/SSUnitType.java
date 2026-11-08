package project.content;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.struct.ObjectSet;
import arc.util.Time;

import mindustry.ai.UnitCommand;
import mindustry.ai.types.AssemblerAI;
import mindustry.ai.types.BuilderAI;
import mindustry.ai.types.CargoAI;
import mindustry.ai.types.CommandAI;
import mindustry.ai.types.DefenderAI;
import mindustry.ai.types.HugAI;
import mindustry.ai.types.SuicideAI;
import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.gen.Sounds;
import mindustry.gen.BuildingTetherPayloadUnit;
import mindustry.gen.ElevationMoveUnit;
import mindustry.gen.LegsUnit;
import mindustry.gen.MechUnit;
import mindustry.gen.PayloadUnit;
import mindustry.gen.TankUnit;
import mindustry.gen.Unit;
import mindustry.gen.UnitWaterMove;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.abilities.EnergyFieldAbility;
import mindustry.entities.abilities.ForceFieldAbility;
import mindustry.entities.abilities.MoveEffectAbility;
import mindustry.entities.abilities.RepairFieldAbility;
import mindustry.entities.abilities.ShieldArcAbility;
import mindustry.entities.abilities.ShieldRegenFieldAbility;
import mindustry.entities.abilities.SpawnDeathAbility;
import mindustry.entities.abilities.StatusFieldAbility;
import mindustry.entities.abilities.SuppressionFieldAbility;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BombBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.entities.bullet.EmpBulletType;
import mindustry.entities.bullet.ExplosionBulletType;
import mindustry.entities.bullet.FlakBulletType;
import mindustry.entities.bullet.LaserBoltBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.entities.bullet.LiquidBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.bullet.RailBulletType;
import mindustry.entities.bullet.SapBulletType;
import mindustry.entities.bullet.ShrapnelBulletType;
import mindustry.entities.effect.ExplosionEffect;
import mindustry.entities.effect.MultiEffect;
import mindustry.entities.effect.ParticleEffect;
import mindustry.entities.effect.WaveEffect;
import mindustry.entities.effect.WrapEffect;
import mindustry.entities.part.DrawPart.PartProgress;
import mindustry.entities.part.FlarePart;
import mindustry.entities.part.HoverPart;
import mindustry.entities.part.DrawPart.PartMove;
import mindustry.entities.part.RegionPart;
import mindustry.entities.part.ShapePart;
import mindustry.entities.pattern.ShootAlternate;
import mindustry.entities.pattern.ShootBarrel;
import mindustry.entities.pattern.ShootHelix;
import mindustry.entities.pattern.ShootPattern;
import mindustry.entities.pattern.ShootSpread;
import mindustry.type.weapons.BuildWeapon;
import mindustry.type.weapons.PointDefenseWeapon;
import mindustry.type.weapons.RepairBeamWeapon;
import mindustry.type.UnitType.UnitEngine;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.type.unit.ErekirUnitType;
import mindustry.type.unit.MissileUnitType;
import mindustry.type.unit.NeoplasmUnitType;
import mindustry.type.unit.TankUnitType;
import mindustry.world.meta.BlockFlag;
import mindustry.world.meta.Env;

import project.content.units.GlowLegsUnitType;
import project.content.units.GlowErekirLegsUnitType;
import project.graphics.DistortionFx;
import project.graphics.FireFx;
import project.graphics.LaserFx;
import project.graphics.WarpShapes;
import project.abilities.WarpAbility;
import project.ShatteredStarMod;
import project.content.units.CarrierUnitType;
import project.modules.ModuleCurve;
import project.modules.ModuleSystem;
import project.modules.ModuleKind;
import project.modules.ModularUnitType;
import project.modules.UnitModule;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Lines.stroke;
import static mindustry.Vars.content;
import static mindustry.Vars.tilePayload;
import static mindustry.Vars.tilesize;

public class SSUnitType {

    // ============================================================
    //  数据库分类 tag
    // ============================================================
    /** 新赛普罗（-r 后缀的塞普罗系单位）分类。 */
    public static Item serpuloRTag;
    /** 埃里克尔（-r 后缀的埃里克尔系单位）分类。 */
    public static Item erekirRTag;

    // ============================================================
    //  声明区
    // ============================================================

    // 堡垒（mech T3，保留用于扭曲渲染测试）
    public static UnitType fortressR;
    // 塞普罗特供
    public static TankUnitType stellSerpulo;
    public static UnitType eludeSerpulo;
    public static GlowErekirLegsUnitType meruiSerpulo;
    // 异构
    public static UnitType daggerAT, novaAT, flareAT, eclipseR;
    // 测试
    public static MissileUnitType test1, test3;
    public static UnitType test2;

    // ============================================================
    //  load()
    // ============================================================
    public static void load() {
        // ★ 先建 tag
        serpuloRTag = new Item("ss-serpulo-r-tag", Color.valueOf("7d4dff")) {
            {
                hidden = true;
                alwaysUnlocked = true;
            }
        };

        erekirRTag = new Item("ss-erekir-r-tag", Color.valueOf("ffa665")) {
            {
                hidden = true;
                alwaysUnlocked = true;
            }
        };

        // ============ 保留单位加载 ============
        loadFortressTest();
        loadSerpuloExclusive();
        loadRemastered();
        loadAllotropes();
        loadTestUnits();

        // ★ 最后打 tag
        applyDatabaseTags();
    }

    // ============================================================
    //  只给 -r 后缀分类
    // ============================================================
    private static void applyDatabaseTags() {
        for (UnitType type : content.units()) {
            if (type == null || type.name == null) continue;
            if (!type.name.endsWith("-r")) continue;

            boolean isErekir =
            type instanceof ErekirUnitType
            || type instanceof TankUnitType
            || type instanceof GlowErekirLegsUnitType;

            if (isErekir) {
                type.databaseTag = erekirRTag.name;
            } else {
                type.databaseTag = serpuloRTag.name;
            }
        }
    }

    // 每个类别一个空方法，下面几个消息里逐个填
    // ============================================================
    //  堡垒（mech T3，保留用于扭曲渲染测试）
    // ============================================================
    private static void loadFortressTest() {
        // ============================================================
        //  fortress-r
        // ============================================================
        fortressR = new UnitType("fortress-r") {
            {
                constructor = MechUnit::create;
                speed = 0.5f;
                hitSize = 13f;
                rotateSpeed = 3f;
                targetAir = false;
                health = 900f;
                armor = 9f;
                mechFrontSway = 0.55f;
                stepSound = Sounds.mechStepSmall;

                stepSoundPitch = 0.8f;
                stepSoundVolume = 0.65f;

                weapons.add(new Weapon(ShatteredStarMod.name("artillery")) {
                        {
                            top = false;
                            y = 1f;
                            x = 9f;
                            reload = 60f;
                            recoil = 4f;
                            shake = 2f;
                            cooldownTime = 60f;
                            heatColor = Color.valueOf("FFA665");
                            ejectEffect = Fx.casing2;
                            shootSound = Sounds.shootArtillery;

                            bullet = new ArtilleryBulletType(4f, 20f, "shell") {
                                {
                                    // 命中只留原版爆炸：屏幕空间的扭曲在俯视角下会把地形
                                    // 连同敌方单位一起「拧」掉，一炮下去看不清打中了什么
                                    hitEffect = Fx.blastExplosion;
                                    // 落地单独加一圈扬尘。爆炸 32 帧是「光」，尘 80 帧是「土」，
                                    // 两者叠在一起这一炮才有重量，不像在半空炸开的烟花。
                                    // 只挂 despawnEffect 不挂 hitEffect——后者打中空中单位时
                                    // 也会触发，那时地上没有土可扬
                                    despawnEffect = FireFx.DUST;
                                    // 开火扬尘：炮口那一圈被气压贴着地面推出去的土。
                                    // 重炮的后坐（recoil 4、shake 2）已经把「炮很重」演出来了，
                                    // 但炮口本身是空的——加了这层，开火才和地面发生关系。
                                    // scale 1.6 按 hitSize 13 / 8 取，范围约 3.6 格
                                    shootEffect = FireFx.muzzleDust(1.6f);
                                    knockback = 0.8f;
                                    lifetime = 53.25f;
                                    maxRange = 240f;
                                    width = 14f;
                                    height = 14f;
                                    collides = true;
                                    collidesTiles = true;
                                    splashDamageRadius = 35f;
                                    splashDamage = 80f;
                                    backColor = Color.valueOf("f9c27a");
                                    frontColor = Color.valueOf("fff8e8");
                                }
                            };
                        }
                    });
            }
        };
    }
    private static void loadSerpuloExclusive() {
        // ============================================================
        //  stell-serpulo  (塞普罗特供 T1 坦克)
        // ============================================================
        stellSerpulo = new TankUnitType("stell-serpulo") {
            {
                constructor = TankUnit::create;
                hitSize = 12f;
                treadPullOffset = 3;
                speed = 0.75f;
                faceTarget = false;
                omniMovement = false;
                squareShape = true;
                rotateMoveFirst = false;
                rotateSpeed = 3.5f;
                health = 200f;
                armor = 1f;
                itemCapacity = 50;
                floorMultiplier = 0.95f;
                treadRects = new Rect[]{
                    new Rect(-20f, -25f, 14f, 51f) };

                tankMoveVolume = 0.32f;
                tankMoveSound = Sounds.tankMoveSmall;

                weapons.add(new Weapon(ShatteredStarMod.name("stell-serpulo-weapon")) {
                        {
                            shootSound = Sounds.shootStell;
                            layerOffset = 0.0001f;
                            reload = 50f;
                            shootY = 4.5f;
                            recoil = 1f;
                            rotate = true;
                            rotateSpeed = 2.2f;
                            mirror = false;
                            x = 0f;
                            y = -0.75f;
                            heatColor = Color.valueOf("ffa665");
                            cooldownTime = 30f;

                            bullet = new BasicBulletType(8f, 20) {
                                {
                                    sprite = "missile-large";
                                    smokeEffect = Fx.shootBigSmoke;
                                    shootEffect = Fx.shootBigColor;
                                    width = 5f;
                                    height = 7f;
                                    lifetime = 20f;
                                    hitSize = 4f;
                                    hitColor = backColor = trailColor = Color.valueOf("feb380");
                                    frontColor = Color.white;
                                    trailWidth = 1.7f;
                                    trailLength = 5;
                                    despawnEffect = hitEffect = Fx.hitBulletColor;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  elude-serpulo  (塞普罗特供 T1 悬浮飞艇)
        // ============================================================
        eludeSerpulo = new UnitType("elude-serpulo") {
            {
                constructor = ElevationMoveUnit::create;
                hovering = true;
                canDrown = false;
                shadowElevation = 0.1f;

                drag = 0.07f;
                speed = 1.8f;
                rotateSpeed = 5f;
                accel = 0.09f;
                health = 125f;
                armor = 0f;
                hitSize = 11f;
                engineOffset = 7f;
                engineSize = 2f;
                itemCapacity = 30;
                useEngineElevation = false;

                moveSound = Sounds.loopExtract;
                moveSoundVolume = 0.25f;
                moveSoundPitchMin = 0.7f;
                moveSoundPitchMax = 1.5f;

                abilities.add(new MoveEffectAbility() {
                        {
                            interval = 4f;
                            minVelocity = 0.5f;
                            effect = Fx.missileTrail;
                            color = Color.valueOf("ffbb64");
                            x = 0f;
                            y = -7f;
                            teamColor = true;
                        }
                    });

                // 两对 hover 装饰
                for (float f : new float[]{
                        -3f, 3f}) {
                    final float fy = f;
                    parts.add(new HoverPart() {
                            {
                                x = 3.9f;
                                y = fy;
                                mirror = true;
                                radius = 6f;
                                phase = 90f;
                                stroke = 2f;
                                layerOffset = -0.001f;
                                color = Color.valueOf("bf92f9");
                            }
                        });
                }

                weapons.add(new Weapon(ShatteredStarMod.name("elude-serpulo-weapon")) {
                        {
                            shootSound = Sounds.shootElude;
                            y = -2f;
                            x = 4f;
                            top = true;
                            mirror = true;
                            reload = 40f;
                            baseRotation = -35f;
                            shootCone = 360f;
                            cooldownTime = 30f;
                            heatColor = Color.valueOf("bf92f9");

                            shoot = new ShootSpread() {
                                {
                                    shots = 2;
                                    spread = 11f;
                                }
                            };

                            bullet = new BasicBulletType(5f, 4) {
                                {
                                    homingPower = 0.19f;
                                    homingDelay = 4f;
                                    width = 7f;
                                    height = 12f;
                                    lifetime = 30f;
                                    shootEffect = Fx.sparkShoot;
                                    smokeEffect = Fx.shootBigSmoke;
                                    hitColor = Color.valueOf("a47aff");
                                    backColor = Color.valueOf("a47aff");
                                    trailColor = Color.valueOf("a47aff");
                                    frontColor = Color.white;
                                    trailWidth = 1.5f;
                                    trailLength = 5;
                                    hitEffect = Fx.hitBulletColor;
                                    despawnEffect = Fx.hitBulletColor;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  merui-serpulo  (塞普罗特供 T1 多足蜘蛛)
        // ============================================================
        meruiSerpulo = new GlowErekirLegsUnitType("merui-serpulo") {
            {
                constructor = LegsUnit::create;
                speed = 0.72f;
                drag = 0.11f;
                hitSize = 9f;
                rotateSpeed = 3f;
                health = 240f;
                armor = 2f;
                legStraightness = 0.3f;
                stepShake = 0f;
                stepSound = Sounds.walkerStepTiny;
                stepSoundVolume = 0.4f;

                legCount = 6;
                legLength = 8f;
                lockLegBase = true;
                legContinuousMove = true;
                legExtension = -2f;
                legBaseOffset = 3f;
                legMaxLength = 1.1f;
                legMinLength = 0.2f;
                legLengthScl = 0.96f;
                legForwardScl = 1.1f;
                legGroupSize = 3;
                rippleScale = 0.2f;

                legMoveSpace = 1f;
                allowLegStep = true;
                hovering = true;
                legPhysicsLayer = false;

                shadowElevation = 0.1f;
                groundLayer = Layer.legUnit - 1f;
                targetAir = false;

                weapons.add(new Weapon(ShatteredStarMod.name("merui-serpulo-weapon")) {
                        {
                            shootSound = Sounds.shootMerui;
                            mirror = false;
                            showStatSprite = false;
                            x = 0f;
                            y = 0f;
                            shootY = 5f;
                            reload = 63f;
                            recoil = 0f;
                            cooldownTime = 42f;
                            heatColor = Color.valueOf("d1efff");

                            bullet = new ArtilleryBulletType(6f, 15) {
                                {
                                    collidesTiles = true;
                                    backColor = Color.valueOf("8ca9e8");
                                    hitColor = Color.valueOf("8ca9e8");
                                    frontColor = Color.white;
                                    knockback = 0.8f;
                                    lifetime = 23f;
                                    width = 9f;
                                    height = 9f;
                                    splashDamageRadius = 19f;
                                    splashDamage = 25f;

                                    trailLength = 27;
                                    trailWidth = 2.5f;
                                    trailEffect = Fx.none;
                                    trailColor = Color.valueOf("8ca9e8");
                                    trailInterp = Interp.slope;

                                    shrinkX = 0.6f;
                                    shrinkY = 0.2f;

                                    hitEffect = despawnEffect = new MultiEffect(
                                        Fx.hitSquaresColor,
                                        new WaveEffect() {
                                            {
                                                colorFrom = colorTo = Color.valueOf("8ca9e8");
                                                sizeTo = 21f;
                                                lifetime = 9f;
                                                strokeFrom = 2f;
                                            }
                                        }
                                    );
                                }
                            };
                        }
                    });
            }
        };
    }
    // ============================================================
    //  原版复刻（-r）
    // ============================================================
    /*
     * 贴图放在 assets/sprites/units/new/ 下，成对提供 xxx-r.png 与 xxx-r-cell.png：
     * 前者是本体，后者是队伍色描边层，由 UnitType.drawCell 自动加载，不用写代码。
     * 数据库分类由 applyDatabaseTags() 按名字后缀自动打，这里也不用手动挂标签。
     */
    private static void loadRemastered() {
        // ============================================================
        //  eclipse-r (原版复刻 · 日蚀)
        // ============================================================
        /*
         * 塞普罗 T5 轰炸机的原版复刻，归在 -r 系列，不是异构。
         *
         * 贴图直接用 new/eclipse-r.png + eclipse-r-cell.png：
         * 机体自带橙黄条纹，-cell 那层是队伍色描边，原版描边和队伍色都照常走
         * UnitType 自己的加载逻辑，不额外挂自发光层。
         * 机体、三门武器、飞行手感照搬原版（22000 血 / 22 护甲 / 0.54 速度 / hitSize 58）。
         *
         * 唯一的新东西是主动跃迁，参数按「大型单位」单独调，
         * 不复用星辉那套小飞机的数值：星辉 9 号机体、转速 5，0.5 秒能把机头掰过来，
         * 蓄力 15 帧起跳，本质是战术闪避；日蚀转 180° 要 3 秒、机身比星辉大六倍，
         * 照抄的话玩家按住 Alt 会先卡在转向里。所以换成慢蓄力、转向自适应加速、
         * 落点带气浪，做成「重型单位的长途投送」。
         */
        eclipseR = new UnitType("eclipse-r") {
            {
                speed = 0.54f;
                accel = 0.04f;
                drag = 0.04f;
                rotateSpeed = 1f;
                flying = true;
                lowAltitude = true;
                health = 22000f;
                engineOffset = 38f;
                engineSize = 7.3f;
                hitSize = 58f;
                armor = 22f;
                targetFlags = new BlockFlag[]{BlockFlag.reactor, BlockFlag.battery, BlockFlag.core, null};

                loopSound = Sounds.loopHover;

                // 副炮炮弹：原版的破片弹，三门武器共用
                BulletType fragBullet = new FlakBulletType(4f, 15) {
                    {
                        shootEffect = Fx.shootBig;
                        ammoMultiplier = 4f;
                        splashDamage = 65f;
                        splashDamageRadius = 25f;
                        collidesGround = true;
                        lifetime = 47f;

                        status = StatusEffects.blasted;
                        statusDuration = 60f;
                    }
                };

                weapons.add(
                    // 主炮：加大版激光
                    new Weapon(ShatteredStarMod.name("large-laser-mount")) {
                        {
                            shake = 4f;
                            shootY = 9f;
                            x = 18f;
                            y = 5f;
                            rotateSpeed = 2f;
                            reload = 45f;
                            recoil = 4f;
                            shootSound = Sounds.shootEclipse;
                            shadow = 20f;
                            rotate = true;

                            bullet = new LaserBulletType() {
                                {
                                    damage = 115f;
                                    sideAngle = 20f;
                                    sideWidth = 1.5f;
                                    sideLength = 80f;
                                    width = 25f;
                                    length = 230f;
                                    shootEffect = Fx.shockwave;
                                    colors = new Color[]{Color.valueOf("ec7458aa"), Color.valueOf("ff9c5a"), Color.white};
                                }
                            };
                        }
                    },
                    // 上副炮
                    new Weapon(ShatteredStarMod.name("large-artillery")) {
                        {
                            x = 11f;
                            y = 27f;
                            rotateSpeed = 2f;
                            reload = 9f;
                            shootSound = Sounds.shootCyclone;
                            shadow = 7f;
                            rotate = true;
                            recoil = 0.5f;
                            shootY = 7.25f;
                            bullet = fragBullet;
                        }
                    },
                    // 下副炮
                    new Weapon(ShatteredStarMod.name("large-artillery")) {
                        {
                            y = -13f;
                            x = 20f;
                            reload = 12f;
                            ejectEffect = Fx.casing1;
                            rotateSpeed = 7f;
                            shake = 1f;
                            shootSound = Sounds.shootCyclone;
                            rotate = true;
                            shadow = 12f;
                            shootY = 7.25f;
                            bullet = fragBullet;
                        }
                    });

                abilities.add(new WarpAbility() {
                    {
                        // —— 特效尺寸 ——
                        // 普通那套环是按 48px 的星辉调的，套到 320px 的日蚀上只有机身三分之一，
                        // 所以整组换成 4 倍的大号版本（WarpShapes 里的 *_LARGE）
                        warpBeginEffect = WarpShapes.BURST_LARGE;
                        warpEndEffect = WarpShapes.ARRIVE_LARGE;
                        warpPrepareEffect = WarpShapes.CHARGE_LARGE;
                        // 机体大，蓄力粒子得铺开在机身上，不能只从中心冒
                        warpPrepareEffectChance = 0.18f;
                        warpPrepareEffectSpread = 62f;
                        // —— 落点预示 ——
                        // 蓄力一开始就在目标坐标把 4 份棱形符印撑开（21 帧），外圈持续翻小碎片、
                        // 每 12 帧补一发内向扭曲；落地瞬间符印往回合拢。
                        // 形式照 VE 幻象 meta-spawn：撑开在跃迁前，机体出现的那一下只负责合拢
                        warpMarkSize = 180f;
                        warpMarkRadius = 220f;
                        warpTargetMote = WarpShapes.MARK_MOTE;
                        warpTargetDistortion = true;
                        warpMarkCollapse = WarpShapes.MARK_COLLAPSE;
                        trailEffect = WarpAbility.warpTrailEffectLarge;
                        trailParticleEffect = WarpAbility.warpParticleEffectLarge;

                        // —— 节奏 ——
                        // 数值参考 VE 的 lacuna/thunder（hitSize 68）：基础蓄力 2 秒、冷却 1.5 秒，
                        // 距离项基本不生效（它的基准给到 102400），等于「巨型单位一律蓄力这么久」。
                        // 日蚀取 110 帧（1.83 秒），保留一小段距离项（800px 加 60 帧），
                        // 让超远距离稍微贵一点，但不至于要等三秒才跳得出去。
                        warpBaseWarmup = 110f;
                        warpMinWarmup = 60f;
                        warpMinWarmupDistance = 6400f;
                        // 手动 5 秒 / 自动 2 秒：T5 要是 2.5 秒就能再跳一次，
                        // 它会变成一台会瞬移的轰炸机，正面战场没有解法
                        warpCooldown = 300f;
                        autoWarpCooldown = 120f;
                        // 距离封顶 1100px（137 格）：够跨过一条战线，又不至于从地图这头跳到那头
                        warpMaxDistance = 1100f;

                        // —— 手感 ——
                        // 强制转向，但不等你转到位：蓄力时把剩余角度摊到剩余帧上自适应提速，
                        // 蓄满一定已经对正，起跳前还会再硬对齐一次。
                        // 也就是说不管现在朝哪，跳出去一定是朝落点的
                        warpTurnToTarget = true;
                        // 不要求「先转到位才许跳」，理由见 WarpAbility.warpRequireFacing
                        warpRequireFacing = false;
                        // 三门炮塔全程自动索敌，要求「停火才能跃迁」等于永远按不出来；
                        // 改成允许边打边跳，代价是落地后所有炮塔压 36 帧装填
                        warpRequireIdleWeapons = false;
                        // 蓄力时轻微刹车，像在定锚；不刹的话两秒蓄力期机体能飘出去半个屏
                        warpChargeBrake = 0.985f;
                        warpShake = 22f;

                        // —— 落点气浪 ——
                        // 只推不伤：日蚀本身火力已经够 T5，再送免费 AOE 会让它变成一次性炸弹
                        warpImpactRadius = 130f;
                        warpImpactForce = 6f;

                        // —— 残影 ——
                        // 两批：4 份外扩 21 帧、4 份延迟 20 帧收回，结构照 VE 幻象的 meta-spawn。
                        // 影子按 320px 原贴图 1:1 画，不再缩边 —— 影子比本体小一圈会像远处的另一架飞机
                        ghostCount = 4;
                        ghostRadius = 210f;
                        ghostExpandTime = 21f;
                        ghostContractDelay = 20f;
                        ghostContractTime = 20f;
                        // 两份影子同时在场时会重叠，42% 是叠两层后仍能看清机体轮廓的上限
                        ghostAlpha = 0.42f;
                        ghostColor = Color.valueOf("ffe2bc");

                        // 大机体落地压一层 stAstroWarp3，份量跟体型对上
                        useHeavyLandingSound = true;
                    }
                });
            }
        };
    }
    private static void loadAllotropes() {
        // ============================================================
        //  陆军战斗：daggerAT (异构 · 尖刀)
        // ============================================================
        daggerAT = new ModularUnitType("dagger-at") {
            {
                constructor = MechUnit::create;
                health = 220f;
                armor = 1f;
                speed = 0.8f;
                hitSize = 8f;
                stepSoundVolume = 0.4f;

                // 异构三台的模块表——T1 尺度，模块血量按单位血量 1/10 上下排。
                // 基准：9 伤子弹 × ratio 0.15 = 1.35 点模块伤害。
                // 尖刀 220 血 1 甲大约挨 24 发，武器 20/(1.35×1.2)=12 发先哑，
                // 装甲 22/(1.35×0.9)=18 发，移动 24/1.35=18 发——都在死之前，
                // 挨到后半段能明确读出「这门炮打不动了」。
                modules(
                    new UnitModule("装甲", ModuleKind.armor, 22f)
                        .vulnerability(0.9f)
                        .curve(ModuleCurve.floored(0.3f, 0.5f)),
                    new UnitModule("武器", ModuleKind.weapon, 20f)
                        .vulnerability(1.2f),
                    new UnitModule("移动", ModuleKind.move, 24f)
                        .curve(ModuleCurve.plateau(0.5f))
                );

                outlineColor = Color.valueOf("202026");

                parts.add(new RegionPart("-glow") {
                        {
                            x = 0f;
                            y = 0f;
                            color = Color.valueOf("FFA665AA");
                            colorTo = Color.valueOf("FFA665AA");
                            outline = false;
                            blending = Blending.additive;
                            layerOffset = 0.001f;
                        }
                    });

                weapons.add(new Weapon(ShatteredStarMod.name("large-weapon-allotropes")) {
                        {
                            reload = 10f;
                            // 挂点和 shoot 偏移整套照抄原版 dagger（v160 UnitTypes$1$1）：
                            //   x = 4, y = 2, top = false, ejectEffect = casing1
                            //   子弹 width 7 / height 9 / lifetime 60（本 mod 只把速度 2.5→5、寿命 60→30）
                            // 原版没写 shootX / shootY，取的是 Weapon 的默认值 0 / 3。
                            // 这里显式写出来，是为了和原版对得上号，不再自己发明 4.5
                            x = 4f;
                            y = 2f;
                            rotate = false;
                            // shootX 横向偏移、shootY 沿炮管方向，两者相对挂点，单位是像素
                            shootX = 0f;
                            shootY = 3f;
                            top = false;
                            ejectEffect = Fx.casing1;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("FFA665");

                            shoot = new ShootPattern() {
                                {
                                    shots = 2;
                                    shotDelay = 5f;
                                }
                            };

                            bullet = new BasicBulletType(5f, 9) {
                                {
                                    width = 7f;
                                    height = 9f;
                                    lifetime = 30f;

                                    // 生成点验证过了：原版 Fx.shootSmall 三件套直接画在 at() 点上，
                                    // 游戏里正好落在枪口，说明子弹生成点本身就是枪口。
                                    // 自绘特效全部以这个点为原点绘制，不做任何回退。
                                    // 原版那几只留给需要朴素观感的场合，一行就能换回去
                                    shootEffect = FireFx.MUZZLE;
                                    smokeEffect = FireFx.MUZZLE_SMOKE;
                                    hitEffect = FireFx.HIT;
                                    // 弹道跟着机体走橙金，不再用原版那套冷黄
                                    frontColor = FireFx.CORE;
                                    backColor = FireFx.MAIN;
                                    lightColor = FireFx.MAIN;
                                    lightOpacity = 0.35f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  陆军辅助：novaAT (异构 · 新星)
        // ============================================================
        novaAT = new ModularUnitType("nova-at") {
            {
                constructor = MechUnit::create;
                canBoost = true;
                boostMultiplier = 2f;
                speed = 0.55f;
                hitSize = 8f;
                health = 200f;
                buildSpeed = 0.3f;
                armor = 2f;

                // 新星是辅助，模块表故意不给「移动」：它跑不快是设定的一部分，
                // 再让它被打瘸没有信息量。装甲的止跌取 0.6（比尖刀高），
                // 因为它只有 2 甲、又常常贴着队友站，线性降会死得太快。
                // 第三格是能力模块，管治疗场——报废之后 RepairFieldAbility
                // 直接不再 update，但治疗激光（武器模块）还能用，两条线分开废。
                modules(
                    new UnitModule("装甲", ModuleKind.armor, 18f)
                        .vulnerability(0.8f)
                        .curve(ModuleCurve.floored(0.3f, 0.6f)),
                    new UnitModule("武器", ModuleKind.weapon, 20f),
                    new UnitModule("能力", ModuleKind.ability, 26f)
                );

                abilities.add(new RepairFieldAbility() {
                        {
                            amount = 30f;
                            reload = 120f;
                            range = 60f;
                        }

                        // 能力模块报废就停手。整段逻辑还是原生那套，
                        // 只在入口问一句——不复制、不重写，将来原版改了也跟得上
                        @Override
                        public void update(Unit unit){
                            if(ModuleSystem.enabled(unit, ModuleKind.ability)) super.update(unit);
                        }
                    });

                outlineColor = Color.valueOf("202026");

                parts.add(new RegionPart("-glow") {
                        {
                            x = 0f;
                            y = 0f;
                            color = Color.valueOf("84F491FF");
                            colorTo = Color.valueOf("84F491FF");
                            outline = false;
                            blending = Blending.additive;
                            layerOffset = 0.001f;
                        }
                    });

                weapons.add(new Weapon(ShatteredStarMod.name("heal-weapon-allotropes")) {
                        {
                            reload = 7f;
                            x = 4.5f;
                            shootY = 2f;
                            top = false;
                            inaccuracy = 0f;
                            alternate = true;
                            ejectEffect = Fx.none;
                            recoil = 2f;
                            shootSound = Sounds.shootLaser;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("84F491");

                            parts.add(new RegionPart("-glow") {
                                    {
                                        x = 0f;
                                        y = 0f;
                                        color = Color.valueOf("84F491AA");
                                        colorTo = Color.valueOf("84F49133");
                                        outline = false;
                                        blending = Blending.additive;
                                        progress = PartProgress.reload;
                                        layerOffset = 0.001f;
                                    }
                                });

                            bullet = new LaserBoltBulletType(10.4f, 7) {
                                {
                                    frontColor = Color.white;
                                    backColor = Color.valueOf("98ffa9");
                                    healPercent = 5f;
                                    collidesTeam = true;
                                    lifetime = 15f;
                                    status = StatusEffects.electrified;
                                    statusDuration = 15f;

                                    // 发射脉冲 + 命中光斑，并将 smokeEffect 归零：
                                    // LaserBoltBulletType 构造里把 smokeEffect 设成了
                                    // Fx.hitLaser，等于每开一枪都在枪口播一遍「命中」，
                                    // 这是原版激光枪口看起来乱糟糟的根源。激光没有烟
                                    LaserFx.attach(this, 0.85f);
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  空军战斗：flareAT (异构 · 星辉)
        // ============================================================
        flareAT = new ModularUnitType("flare-at") {
            {
                flying = true;
                health = 150f;

                // 星辉只有 150 血，四个模块加起来 74，比尖刀的比例更薄——
                // 空军本来就该「一被咬住就掉零件」。移动模块管飞行速度，
                // 能力模块管跃迁：引擎被打烂就先失去保命手段，这个顺序比反过来合理。
                modules(
                    new UnitModule("装甲", ModuleKind.armor, 16f)
                        .vulnerability(0.9f)
                        .curve(ModuleCurve.floored(0.3f, 0.5f)),
                    new UnitModule("武器", ModuleKind.weapon, 18f)
                        .vulnerability(1.1f),
                    new UnitModule("移动", ModuleKind.move, 20f)
                        .curve(ModuleCurve.plateau(0.6f)),
                    new UnitModule("能力", ModuleKind.ability, 20f)
                );
                rotateSpeed = 5f;
                omniMovement = true;
                speed = 5f;
                accel = 0.08f;
                drag = 0.04f;
                engineOffset = 5.75f;
                engineSize = 2f;
                hitSize = 9f;
                itemCapacity = 20;
                circleTarget = true;
                circleTargetRadius = 60f;
                wreckSoundVolume = 0.7f;
                faceTarget = false;

                moveSound = Sounds.loopThruster;
                moveSoundPitchMin = 0.3f;
                moveSoundPitchMax = 1.5f;
                moveSoundVolume = 0.2f;

                outlineColor = Color.valueOf("202026");
                trailLength = 3;

                parts.add(new RegionPart("-glow") {
                        {
                            x = 0f;
                            y = 0f;
                            color = Color.valueOf("FFA665AA");
                            colorTo = Color.valueOf("FFA665AA");
                            outline = false;
                            blending = Blending.additive;
                            layerOffset = 0.001f;
                        }
                    });

                weapons.add(new Weapon() {
                        {
                            y = 0f;
                            x = 0f;
                            shootY = 1f;
                            layerOffset = -0.001f;
                            shootCone = 20f;
                            reload = 20f;
                            rotate = false;
                            rotateSpeed = 25f;
                            mirror = false;
                            ejectEffect = Fx.casing1;
                            shootSound = Sounds.shootSalvo;

                            shoot = new ShootSpread() {
                                {
                                    shots = 3;
                                    shotDelay = 5f;
                                }
                            };

                            bullet = new BasicBulletType(20f, 5) {
                                {
                                    buildingDamageMultiplier = 0.5f;
                                    width = 3f;
                                    height = 30f;
                                    lifetime = 7f;
                                    shootEffect = Fx.shootSmall;
                                    smokeEffect = Fx.shootSmallSmoke;
                                }
                            };
                        }
                    });

                // 主动跃迁：按住 Alt 蓄力，把星辉送到鼠标位置。
                // 蓄力时间随距离增长、要求先转到目标方向、结束后进入冷却，
                // 三点共同防止它变成无脑贴脸闪避。数值在 WarpAbility 里改。
                // 能力模块报废后整段蓄力逻辑不再跑，按住 Alt 不响应。
                abilities.add(new WarpAbility() {
                        @Override
                        public void update(Unit unit){
                            if(ModuleSystem.enabled(unit, ModuleKind.ability)) super.update(unit);
                        }
                    });
            }
        };

    }
    private static void loadTestUnits() {
        // ============================================================
        //  test-1  (测试导弹 T1)
        // ============================================================
        test1 = new MissileUnitType("test-1") {
            {
                allowedInPayloads = true;
                logicControllable = true;
                lifetime = 480f;
                engineSize = 3f;
                speed = 3f;
                armor = 5f;
                rotateSpeed = 2f;
                maxRange = -1f;
                health = 700f;
                lowAltitude = true;
                missileAccelTime = 0f;
                hidden = true;
                alwaysUnlocked = true;
                outlineColor = Color.valueOf("565666");
                engineOffset = 12f;
                engineLayer = Layer.effect;
                engineColor = Color.valueOf("ffbb64");
                trailLength = 10;
                trailColor = Color.valueOf("ffbb64");
                fogRadius = 0f;
                deathExplosionEffect = Fx.smokeCloud;
                lightColor = Color.valueOf("ffbb64");
                drawCell = true;
                envDisabled = 0;
                deathSound = Sounds.explosionAfflict;

                weapons.add(new Weapon(ShatteredStarMod.name("zenith-missiles")) {
                        {
                            reload = 50f;
                            x = 7f;
                            rotate = true;
                            shake = 1f;
                            inaccuracy = 5f;
                            velocityRnd = 0.2f;
                            shootSound = Sounds.shootMissileLong;

                            shoot.shots = 3;

                            bullet = new MissileBulletType(3f, 14) {
                                {
                                    width = 8f;
                                    height = 8f;
                                    shrinkY = 0f;
                                    drag = -0.003f;
                                    homingRange = 60f;
                                    scaleKeepVelocity = true;
                                    splashDamageRadius = 25f;
                                    splashDamage = 15f;
                                    lifetime = 75f;
                                    trailColor = Color.valueOf("d06b53");
                                    backColor = Color.valueOf("d06b53");
                                    frontColor = Color.valueOf("ffa665");
                                    hitEffect = Fx.blastExplosion;
                                    despawnEffect = Fx.blastExplosion;
                                    weaveScale = 6f;
                                    weaveMag = -1f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  test-3  (测试导弹 T3，比 test-1 更小更快)
        // ============================================================
        test3 = new MissileUnitType("test-3") {
            {
                allowedInPayloads = true;
                logicControllable = true;
                lifetime = 600f;
                engineSize = 3f;
                speed = 5f;
                armor = 0f;
                rotateSpeed = 2f;
                maxRange = -1f;
                health = 70f;
                lowAltitude = true;
                missileAccelTime = 0f;
                hidden = true;
                alwaysUnlocked = true;
                outlineColor = Color.valueOf("565666");
                engineOffset = 5.75f;
                engineLayer = Layer.effect;
                engineColor = Color.valueOf("ffbb64");
                trailLength = 10;
                trailColor = Color.valueOf("ffbb64");
                fogRadius = 0f;
                deathExplosionEffect = Fx.smokeCloud;
                lightColor = Color.valueOf("ffbb64");
                drawCell = true;
                envDisabled = 0;
                deathSound = Sounds.explosionAfflict;

                // 沿用 flare 的武器：高速小弹
                weapons.add(new Weapon() {
                        {
                            y = 0f;
                            x = 2f;
                            layerOffset = -0.001f;
                            shootCone = 360f;
                            reload = 2f;
                            alternate = true;
                            rotate = true;
                            rotateSpeed = 25f;
                            mirror = true;
                            ejectEffect = Fx.casing1;
                            shootSound = Sounds.shootSalvo;

                            bullet = new BasicBulletType(20f, 1) {
                                {
                                    buildingDamageMultiplier = 0.5f;
                                    width = 3f;
                                    height = 30f;
                                    lifetime = 7f;
                                    shootEffect = Fx.shootSmall;
                                    smokeEffect = Fx.shootSmallSmoke;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  test-2  (测试母机：发射 test-1 和 test-3)
        // ============================================================
        test2 = new UnitType("test-2") {
            {
                flying = true;
                health = 10000f;
                speed = 1.8f;
                armor = 1f;

                weapons.add(new Weapon() {
                        {
                            name = "none";
                            reload = 600f;
                            x = 0f;
                            y = 0f;
                            mirror = false;
                            ejectEffect = Fx.casing1;
                            shootSound = Sounds.shoot;

                            shoot = new ShootSpread() {
                                {
                                    shots = 2;
                                    shotDelay = 6f;
                                    spread = 15f;
                                }
                            };

                            bullet = new BulletType() {
                                {
                                    keepVelocity = false;
                                    speed = 0f;
                                    shootEffect = Fx.none;
                                    smokeEffect = Fx.shootSmallFlame;
                                    spawnUnit = test1;
                                }
                            };
                        }
                    });

                weapons.add(new Weapon() {
                        {
                            name = "none";
                            reload = 600f;
                            x = 0f;
                            y = 0f;
                            mirror = false;
                            ejectEffect = Fx.casing1;
                            shootSound = Sounds.shoot;

                            shoot = new ShootSpread() {
                                {
                                    shots = 3;
                                    shotDelay = 30f;
                                    spread = 4f;
                                }
                            };

                            bullet = new BulletType() {
                                {
                                    keepVelocity = false;
                                    speed = 0f;
                                    shootEffect = Fx.none;
                                    smokeEffect = Fx.shootSmallFlame;
                                    spawnUnit = test3;
                                }
                            };
                        }
                    });
            }
        };
    }
}