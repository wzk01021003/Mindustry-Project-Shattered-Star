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
import project.content.units.CarrierUnitType;

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
    public static UnitType daggerAT, novaAT, flareAT;
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

                weapons.add(new Weapon("artillery") {
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
                                    // 双层扭曲：内层 13 帧内爆「拧紧」，外层 32 帧锐环外扩。
                                    // 两者同时触发，靠寿命差自然形成先收后散的双段节奏，
                                    // 比单一预设更接近铁锈未来科技等离子炮的冲击感。
                                    // 顺序上外层优先：maxPerFrame 在低画质档仅 2 个槽位，
                                    // 双层占满时若被限流应保留主视觉（锐环外扩），
                                    // 丢掉内层只损失层次感，不会退化成「只有内吸」。
                                    hitEffect = new MultiEffect(
                                        Fx.blastExplosion,
                                        DistortionFx.fortressArtilleryImpact,
                                        DistortionFx.fortressArtilleryImplosion);
                                    // 炮弹飞抵最大射程未命中时同样播放扭曲，保持观感一致
                                    despawnEffect = new MultiEffect(
                                        DistortionFx.fortressArtilleryImpact,
                                        DistortionFx.fortressArtilleryImplosion);
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

                weapons.add(new Weapon("stell-serpulo-weapon") {
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

                weapons.add(new Weapon("elude-serpulo-weapon") {
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

                weapons.add(new Weapon("merui-serpulo-weapon") {
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
    private static void loadAllotropes() {
        // ============================================================
        //  陆军战斗：daggerAT (异构 · 尖刀)
        // ============================================================
        daggerAT = new UnitType("dagger-at") {
            {
                constructor = MechUnit::create;
                health = 220f;
                armor = 1f;
                speed = 0.8f;
                hitSize = 8f;
                stepSoundVolume = 0.4f;

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

                weapons.add(new Weapon("large-weapon-allotropes") {
                        {
                            reload = 10f;
                            x = 4f;
                            y = 2f;
                            rotate = false;
                            shootY = 4.5f;
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
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  陆军辅助：novaAT (异构 · 新星)
        // ============================================================
        novaAT = new UnitType("nova-at") {
            {
                constructor = MechUnit::create;
                canBoost = true;
                boostMultiplier = 2f;
                speed = 0.55f;
                hitSize = 8f;
                health = 200f;
                buildSpeed = 0.3f;
                armor = 2f;

                abilities.add(new RepairFieldAbility() {
                        {
                            amount = 30f;
                            reload = 120f;
                            range = 60f;
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

                weapons.add(new Weapon("heal-weapon-allotropes") {
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
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  空军战斗：flareAT (异构 · 星辉)
        // ============================================================
        flareAT = new UnitType("flare-at") {
            {
                flying = true;
                health = 150f;
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

                weapons.add(new Weapon("zenith-missiles") {
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