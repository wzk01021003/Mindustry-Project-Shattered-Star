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

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Lines.stroke;
import static mindustry.Vars.tilePayload;
import static mindustry.Vars.tilesize;

public class SSUnitType {

    // ============================================================
    //  声明区
    // ============================================================

    // 双足战斗
    public static UnitType daggerR, maceR, fortressR, scepterR, reignR;
    // 双足辅助
    public static UnitType novaR, pulsarR, quasarR, velaR;
    public static GlowLegsUnitType corvusR;
    // 爬虫
    public static UnitType crawlerR;
    public static GlowLegsUnitType atraxR, spiroctR, arkyidR, toxopidR;
    // 战斗空军
    public static UnitType flareR, horizonR, zenithR, antumbraR, eclipseR;
    // 辅助空军
    public static UnitType monoR, polyR, megaR, quadR, octR;
    // 战斗海军
    public static UnitType rissoR, minkeR, brydeR, seiR, omuraR;
    // 辅助海军
    public static UnitType retusaR, oxynoeR, cyerceR, aegiresR, navanaxR;
    // 埃里克尔坦克
    public static TankUnitType stellR, locusR, preceptR, vanquishR, conquerR;
    // 埃里克尔空军
    public static UnitType eludeR, avertR, obviateR, quellR, disruptR;
    // 埃里克尔蜘蛛
    public static GlowErekirLegsUnitType meruiR, cleroiR, anthicusR, tectaR, collarisR;
    // 导弹
    public static MissileUnitType scatheMissile, scatheMissilePhase, scatheMissileSurge;
    // 货运 / 装配
    public static ErekirUnitType manifoldR, assemblyDroneR;
    // 核心无人机
    public static UnitType alphaR, betaR, gammaR;
    public static ErekirUnitType evokeR, inciteR, emanateR;
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
    //  load() —— 按类别顺序填充
    // ============================================================
    public static void load() {
        loadSerpuloGroundCombat();
        // daggerR, maceR, fortressR, scepterR, reignR
        loadSerpuloGroundSupport();
        // novaR, pulsarR, quasarR, velaR, corvusR
        loadSerpuloCrawlers();
        // crawlerR, atraxR, spiroctR, arkyidR, toxopidR
        loadSerpuloAirCombat();
        // flareR, horizonR, zenithR, antumbraR, eclipseR
        loadSerpuloAirSupport();
        // monoR, polyR, megaR, quadR, octR
        loadSerpuloNavalCombat();
        // rissoR, minkeR, brydeR, seiR, omuraR
        loadSerpuloNavalSupport();
        // retusaR, oxynoeR, cyerceR, aegiresR, navanaxR
        loadErekirTanks();
        // stellR, locusR, preceptR, vanquishR, conquerR
        loadErekirAir();
        // eludeR, avertR, obviateR, quellR, disruptR
        loadErekirSpiders();
        // meruiR, cleroiR, anthicusR, tectaR, collarisR
        loadCargoAndAssembly();
        // manifoldR, assemblyDroneR
        loadCoreUnits();
        // alphaR, betaR, gammaR, evokeR, inciteR, emanateR
        loadSerpuloExclusive();
        // stellSerpulo, eludeSerpulo, meruiSerpulo
        loadAllotropes();
        // daggerAT, novaAT, flareAT
        loadTestUnits();
        // test1, test2, test3
    }

    // 每个类别一个空方法，下面几个消息里逐个填
    private static void loadSerpuloGroundCombat() {

        // ============================================================
        //  dagger-r
        // ============================================================
        daggerR = new UnitType("dagger-r") {
            {
                constructor = MechUnit::create;
                researchCostMultiplier = 0.5f;
                speed = 0.5f;
                hitSize = 8f;
                health = 150f;
                stepSoundVolume = 0.4f;

                weapons.add(new Weapon("large-weapon") {
                        {
                            reload = 10f;
                            x = 4f;
                            y = 2f;
                            shootY = 4.5f;
                            top = false;
                            rotate = false;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("FFA665");
                            ejectEffect = Fx.casing1;

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
        //  mace-r
        // ============================================================
        maceR = new UnitType("mace-r") {
            {
                constructor = MechUnit::create;
                speed = 0.5f;
                hitSize = 10f;
                health = 550f;
                armor = 4f;
                immunities.add(StatusEffects.burning);

                weapons.add(new Weapon("flamethrower") {
                        {
                            top = false;
                            shootSound = Sounds.shootFlame;
                            shootY = 6f;
                            reload = 11f;
                            recoil = 1f;
                            cooldownTime = 30f;
                            heatColor = Color.valueOf("FF624D");
                            ejectEffect = Fx.none;

                            bullet = new BulletType(4.2f, 37f) {
                                {
                                    ammoMultiplier = 3f;
                                    hitSize = 7f;
                                    lifetime = 13f;
                                    pierce = true;
                                    pierceBuilding = true;
                                    pierceCap = 2;
                                    statusDuration = 300f;
                                    shootEffect = Fx.shootSmallFlame;
                                    hitEffect = Fx.hitFlameSmall;
                                    despawnEffect = Fx.none;
                                    smokeEffect = Fx.shootBigSmoke;
                                    status = StatusEffects.burning;
                                    keepVelocity = false;
                                    hittable = false;
                                }
                            };
                        }
                    });
            }
        };

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
                                    hitEffect = Fx.blastExplosion;
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

        // ============================================================
        //  scepter-r
        // ============================================================
        scepterR = new UnitType("scepter-r") {
            {
                constructor = MechUnit::create;
                speed = 0.6f;
                hitSize = 22f;
                rotateSpeed = 2.1f;
                health = 9000f;
                armor = 20f;
                mechFrontSway = 1f;
                mechStepParticles = true;
                stepShake = 0.15f;
                singleTarget = true;
                drownTimeMultiplier = 1.5f;
                stepSound = Sounds.mechStep;
                stepSoundPitch = 0.9f;
                stepSoundVolume = 0.35f;

                abilities.add(new ShieldRegenFieldAbility(25f, 250f, 60f, 60f));

                weapons.add(new Weapon("scepter-weapon") {
                        {
                            top = false;
                            y = 1f;
                            x = 16f;
                            shootY = 8f;
                            reload = 9f;
                            recoil = 5f;
                            shake = 2f;
                            inaccuracy = 3f;
                            ejectEffect = Fx.casing3;
                            shootSound = Sounds.shootScepter;
                            shootSoundVolume = 0.95f;

                            shoot.shots = 3;
                            shoot.shotDelay = 4f;

                            bullet = new BasicBulletType(8f, 70) {
                                {
                                    width = 11f;
                                    height = 20f;
                                    lifetime = 27f;
                                    shrinkX = 0.4f;
                                    shrinkY = 0f;
                                    shootEffect = Fx.shootBig;
                                    hitEffect = Fx.blastExplosion;
                                    trailParam = 0.5f;
                                    lightning = 2;
                                    lightningLength = 6;
                                    lightningColor = Pal.surge;
                                    lightningDamage = 20;
                                    despawnSound = Sounds.shockBullet;
                                    bulletInterval = 4f;

                                    intervalBullet = new LightningBulletType() {
                                        {
                                            damage = 5f;
                                            lightningLength = 3;
                                            lightningLengthRand = 4;
                                            lightningColor = Pal.surge;
                                            hitEffect = Fx.hitLancerLow;
                                        }
                                    };
                                }
                            };
                        }
                    });

                BasicBulletType smallBullet = new BasicBulletType(12f, 20) {
                    {
                        width = 4.5f;
                        height = 35f;
                        lifetime = 25f;
                        shrinkX = 0.6f;
                        shrinkY = 0f;
                        shrinkInterp = Interp.slope;
                        trailColor = Color.valueOf("f9c27a");
                        trailEffect = Fx.bulletSparkSmokeTrailSmall;
                        trailSpread = 12f;
                        shootEffect = Fx.shootScepterSecondary;
                        hitEffect = Fx.hitScepterSecondary;
                    }
                };

                weapons.add(new Weapon("scepter-mount") {
                        {
                            reload = 12f;
                            x = 8.5f;
                            y = 6f;
                            rotate = true;
                            rotateSpeed = 3f;
                            ejectEffect = Fx.casing1;
                            shootSound = Sounds.shootScepterSecondary;
                            bullet = smallBullet;
                        }
                    });

                weapons.add(new Weapon("scepter-mount") {
                        {
                            reload = 15f;
                            x = 8.5f;
                            y = -7f;
                            rotate = true;
                            rotateSpeed = 3f;
                            ejectEffect = Fx.casing1;
                            shootSound = Sounds.shootScepterSecondary;
                            bullet = smallBullet;
                        }
                    });
            }
        };

        // ============================================================
        //  reign-r
        // ============================================================
        reignR = new UnitType("reign-r") {
            {
                constructor = MechUnit::create;
                speed = 0.6f;
                hitSize = 30f;
                rotateSpeed = 1.65f;
                health = 24000f;
                armor = 30f;
                mechStepParticles = true;
                stepShake = 0.75f;
                drownTimeMultiplier = 1.6f;
                mechFrontSway = 1.9f;
                mechSideSway = 0.6f;
                stepSound = Sounds.mechStepHeavy;
                stepSoundPitch = 0.9f;
                stepSoundVolume = 0.45f;

                weapons.add(new Weapon("reign-weapon") {
                        {
                            top = false;
                            y = 1f;
                            x = 21.5f;
                            shootY = 11f;
                            reload = 30f;
                            recoil = 5f;
                            shake = 2f;
                            cooldownTime = 30f;
                            heatColor = Color.valueOf("FFA665");
                            ejectEffect = Fx.casing4;
                            shootSound = Sounds.shootReign;

                            shoot.shots = 3;
                            shoot.shotDelay = 4f;

                            bullet = new BasicBulletType(13f, 90) {
                                {
                                    pierce = true;
                                    pierceCap = 10;
                                    width = 14f;
                                    height = 33f;
                                    lifetime = 15f;
                                    shootEffect = Fx.shootBig;
                                    fragVelocityMin = 0.4f;
                                    hitEffect = Fx.blastExplosion;
                                    splashDamage = 9f;
                                    splashDamageRadius = 13f;
                                    fragBullets = 5;
                                    fragLifeMin = 0f;
                                    fragRandomSpread = 30f;
                                    despawnSound = Sounds.explosion;

                                    fragBullet = new BasicBulletType(9f, 15) {
                                        {
                                            width = 10f;
                                            height = 10f;
                                            pierce = true;
                                            pierceBuilding = true;
                                            pierceCap = 3;
                                            lifetime = 20f;
                                            hitEffect = Fx.flakExplosion;
                                            splashDamage = 20f;
                                            splashDamageRadius = 10f;
                                        }
                                    };
                                }
                            };
                        }
                    });
            }
        };
    }
    private static void loadSerpuloGroundSupport() {
        // ============================================================
        //  nova-r
        // ============================================================
        novaR = new UnitType("nova-r") {
            {
                constructor = MechUnit::create;
                canBoost = true;
                boostMultiplier = 2f;
                speed = 0.55f;
                hitSize = 8f;
                health = 200f;
                buildSpeed = 0.3f;
                armor = 1f;

                abilities.add(new RepairFieldAbility(20f, 60f * 2, 100f) {
                        {
                            sameTypeHealMult = 0.15f;
                            maxTargets = 6;
                            smartHeal = true;
                            smartDowntime = 60 * 4f;
                        }
                    });

                weapons.add(new Weapon("heal-weapon") {
                        {
                            top = false;
                            shootY = 2f;
                            reload = 15f;
                            x = 4.5f;
                            alternate = true;
                            inaccuracy = 0f;
                            recoil = 2f;
                            ejectEffect = Fx.none;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("84F491");
                            shootSound = Sounds.shootLaser;

                            bullet = new LaserBoltBulletType(10.4f, 13) {
                                {
                                    lifetime = 15f;
                                    healPercent = 5f;
                                    collidesTeam = true;
                                    backColor = Color.valueOf("98ffa9");
                                    frontColor = Color.white;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  pulsar-r
        // ============================================================
        pulsarR = new UnitType("pulsar-r") {
            {
                constructor = MechUnit::create;
                canBoost = true;
                boostMultiplier = 1.6f;
                speed = 0.7f;
                hitSize = 11f;
                health = 320f;
                buildSpeed = 0.5f;
                armor = 4f;
                riseSpeed = descentSpeed = 0.07f;

                mineTier = 2;
                mineSpeed = 3f;

                abilities.add(new ShieldRegenFieldAbility(20f, 40f, 60f * 5, 60f));

                weapons.add(new Weapon("heal-shotgun-weapon") {
                        {
                            top = false;
                            x = 5f;
                            shake = 2.2f;
                            y = 0.5f;
                            shootY = 2.5f;
                            reload = 6f;
                            inaccuracy = 0f;
                            recoil = 2.5f;
                            ejectEffect = Fx.none;
                            cooldownTime = 10f;
                            heatColor = Color.valueOf("84F491");
                            shootSound = Sounds.shootPulsar;

                            shoot.shots = 5;
                            shoot.shotDelay = 0f;

                            bullet = new LightningBulletType() {
                                {
                                    lightningColor = hitColor = Color.valueOf("98ffa9");
                                    damage = 1f;
                                    lightningLength = 8;
                                    lightningLengthRand = 7;
                                    shootEffect = Fx.shootHeal;
                                    healPercent = 2f;

                                    lightningType = new BulletType(0.0001f, 0f) {
                                        {
                                            lifetime = Fx.lightning.lifetime;
                                            hitEffect = Fx.hitLancer;
                                            despawnEffect = Fx.none;
                                            status = StatusEffects.electrified;
                                            statusDuration = 6f;
                                            hittable = false;
                                            healPercent = 1.6f;
                                            collidesTeam = true;
                                        }
                                    };
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  quasar-r
        // ============================================================
        quasarR = new UnitType("quasar-r") {
            {
                constructor = MechUnit::create;
                mineTier = 3;
                boostMultiplier = 2f;
                health = 640f;
                buildSpeed = 1.1f;
                canBoost = true;
                armor = 9f;
                mechLandShake = 2f;
                riseSpeed = descentSpeed = 0.05f;

                mechFrontSway = 0.55f;
                stepSound = Sounds.mechStepSmall;
                stepSoundPitch = 0.9f;
                stepSoundVolume = 0.6f;

                speed = 0.5f;
                hitSize = 13f;
                mineSpeed = 4f;
                drawShields = false;

                abilities.add(new ForceFieldAbility(60f, 0.4f, 500f, 60f * 6));

                weapons.add(new Weapon("beam-weapon") {
                        {
                            top = false;
                            shake = 2f;
                            shootY = 4f;
                            x = 6.5f;
                            reload = 13.5f;
                            recoil = 4f;
                            cooldownTime = 25f;
                            heatColor = Color.valueOf("84F491");
                            shootSound = Sounds.shootLancer;

                            bullet = new LaserBulletType() {
                                {
                                    damage = 11.5f;
                                    recoil = 0f;
                                    sideAngle = 45f;
                                    sideWidth = 1f;
                                    sideLength = 70f;
                                    healPercent = 10f;
                                    collidesTeam = true;
                                    length = 150f;
                                    colors = new Color[]{
                                        Color.valueOf("98ffa928"),
                                        Color.valueOf("98ffa9"),
                                        Color.white
                                    };
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  vela-r
        // ============================================================
        velaR = new UnitType("vela-r") {
            {
                constructor = MechUnit::create;
                hitSize = 24f;
                rotateSpeed = 1.8f;
                mechFrontSway = 1f;
                buildSpeed = 3f;
                mechStepParticles = true;
                stepShake = 0.15f;
                drownTimeMultiplier = 1.3f;

                speed = 0.44f;
                boostMultiplier = 2.4f;
                engineOffset = 12f;
                engineSize = 6f;
                lowAltitude = true;
                riseSpeed = descentSpeed = 0.02f;

                health = 8200f;
                armor = 16f;
                canBoost = true;
                mechLandShake = 4f;
                immunities.add(StatusEffects.burning);

                singleTarget = true;
                stepSound = Sounds.mechStep;
                stepSoundPitch = 0.9f;
                stepSoundVolume = 0.25f;

                weapons.add(new Weapon("vela-weapon") {
                        {
                            mirror = false;
                            top = false;
                            shake = 4f;
                            shootY = 14f;
                            x = y = 0f;

                            shoot.firstShotDelay = Fx.greenLaserChargeSmall.lifetime - 1f;
                            parentizeEffects = true;

                            reload = 155f;
                            recoil = 0f;
                            chargeSound = Sounds.chargeVela;
                            shootSound = Sounds.beamPlasma;
                            initialShootSound = Sounds.shootBeamPlasma;
                            continuous = true;
                            cooldownTime = 200f;

                            bullet = new ContinuousLaserBulletType() {
                                {
                                    damage = 35f;
                                    length = 180f;
                                    hitEffect = Fx.hitMeltHeal;
                                    drawSize = 420f;
                                    lifetime = 160f;
                                    shake = 1f;
                                    despawnEffect = Fx.smokeCloud;
                                    smokeEffect = Fx.none;

                                    chargeEffect = Fx.greenLaserChargeSmall;

                                    incendChance = 0.1f;
                                    incendSpread = 5f;
                                    incendAmount = 1;

                                    healPercent = 1f;
                                    collidesTeam = true;

                                    colors = new Color[]{
                                        Pal.heal.cpy().a(.2f),
                                        Pal.heal.cpy().a(.5f),
                                        Pal.heal.cpy().mul(1.2f),
                                        Color.white
                                    };
                                }
                            };

                            shootStatus = StatusEffects.slow;
                            shootStatusDuration = bullet.lifetime + shoot.firstShotDelay;
                        }
                    });

                weapons.add(new RepairBeamWeapon("repair-beam-weapon-center-large") {
                        {
                            x = 44 / 4f;
                            y = -30f / 4f;
                            shootY = 6f;
                            beamWidth = 0.8f;
                            repairSpeed = 1.4f;

                            bullet = new BulletType() {
                                {
                                    maxRange = 120f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  corvus-r
        // ============================================================
        corvusR = new GlowLegsUnitType("corvus-r") {
            {
                constructor = LegsUnit::create;
                hitSize = 29f;
                health = 18000f;
                armor = 14f;
                stepShake = 1.5f;
                rotateSpeed = 1.5f;
                drownTimeMultiplier = 1.6f;

                stepSound = Sounds.walkerStep;
                stepSoundVolume = 1.1f;
                stepSoundPitch = 0.9f;

                legCount = 4;
                legLength = 14f;
                legBaseOffset = 11f;
                legMoveSpace = 1.5f;
                legForwardScl = 0.58f;
                hovering = true;
                shadowElevation = 0.2f;
                groundLayer = Layer.legUnit;

                speed = 0.3f;
                drawShields = false;

                weapons.add(new Weapon("corvus-weapon") {
                        {
                            shootSound = Sounds.shootCorvus;
                            chargeSound = Sounds.chargeCorvus;
                            soundPitchMin = 1f;
                            top = false;
                            mirror = false;
                            shake = 14f;
                            shootY = 5f;
                            x = y = 0;
                            reload = 350f;
                            recoil = 0f;
                            cooldownTime = 350f;

                            shootStatusDuration = 60f * 2f;
                            shootStatus = StatusEffects.unmoving;
                            shoot.firstShotDelay = Fx.greenLaserCharge.lifetime;
                            parentizeEffects = true;

                            bullet = new LaserBulletType() {
                                {
                                    length = 460f;
                                    damage = 560f;
                                    width = 75f;
                                    lifetime = 65f;

                                    lightningSpacing = 35f;
                                    lightningLength = 5;
                                    lightningDelay = 1.1f;
                                    lightningLengthRand = 15;
                                    lightningDamage = 50;
                                    lightningAngleRand = 40f;
                                    largeHit = true;
                                    lightColor = lightningColor = Pal.heal;

                                    chargeEffect = Fx.greenLaserCharge;

                                    healPercent = 25f;
                                    collidesTeam = true;

                                    sideAngle = 15f;
                                    sideWidth = 0f;
                                    sideLength = 0f;
                                    colors = new Color[]{
                                        Pal.heal.cpy().a(0.4f),
                                        Pal.heal,
                                        Color.white
                                    };
                                }
                            };
                        }
                    });
            }
        };
    }
    private static void loadSerpuloCrawlers() {
        // ============================================================
        //  crawler-r
        // ============================================================
        crawlerR = new UnitType("crawler-r") {
            {
                constructor = MechUnit::create;
                researchCostMultiplier = 0.5f;
                aiController = SuicideAI::new;

                speed = 1f;
                hitSize = 8f;
                health = 150f;
                mechSideSway = 0.25f;
                range = 40f;
                faceTarget = false;
                stepSound = Sounds.walkerStepTiny;
                stepSoundVolume = 0.2f;

                weapons.add(new Weapon() {
                        {
                            shootOnDeath = true;
                            targetUnderBlocks = false;
                            reload = 24f;
                            shootCone = 180f;
                            minWarmup = 0.5f;
                            shootWarmupSpeed = 0.05f;
                            ejectEffect = Fx.none;
                            shootSound = Sounds.explosionCrawler;
                            shootSoundVolume = 0.4f;
                            x = 0f;
                            shootY = 0f;
                            mirror = false;

                            parts.add(new RegionPart("-glow-heat") {
                                    {
                                        mirror = false;
                                        outline = false;
                                        color = Color.valueOf("BF92F900");
                                        colorTo = Color.valueOf("BF92F9");
                                        blending = Blending.additive;
                                    }
                                });
                            parts.add(new RegionPart("-glow") {
                                    {
                                        mirror = false;
                                        outline = false;
                                        color = Color.valueOf("BF92F980");
                                        blending = Blending.additive;
                                    }
                                });

                            bullet = new BulletType() {
                                {
                                    collidesTiles = false;
                                    collides = false;
                                    rangeOverride = 25f;
                                    hitEffect = Fx.pulverize;
                                    speed = 0f;
                                    splashDamageRadius = 44f;
                                    instantDisappear = true;
                                    splashDamage = 80f;
                                    buildingDamageMultiplier = 0.68f;
                                    killShooter = true;
                                    hittable = false;
                                    collidesAir = true;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  atrax-r
        // ============================================================
        atraxR = new GlowLegsUnitType("atrax-r") {
            {
                constructor = LegsUnit::create;
                speed = 0.6f;
                drag = 0.4f;
                hitSize = 13f;
                rotateSpeed = 3f;
                targetAir = false;
                health = 600f;
                armor = 3f;
                immunities = ObjectSet.with(StatusEffects.burning, StatusEffects.melting);

                stepSound = Sounds.walkerStepSmall;
                stepSoundPitch = 1f;
                stepSoundVolume = 0.25f;

                legCount = 4;
                legLength = 9f;
                legForwardScl = 0.6f;
                legMoveSpace = 1.4f;
                hovering = true;
                shadowElevation = 0.2f;
                groundLayer = Layer.legUnit - 1f;

                weapons.add(new Weapon("atrax-weapon") {
                        {
                            top = false;
                            shootY = 3f;
                            reload = 9f;
                            recoil = 1f;
                            x = 7f;
                            ejectEffect = Fx.none;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("BF92F9");
                            shootSound = Sounds.shootAtrax;

                            bullet = new LiquidBulletType(Liquids.slag) {
                                {
                                    damage = 13;
                                    speed = 5f;
                                    drag = 0.009f;
                                    lifetime = 28.5f;
                                    collidesAir = false;
                                    shootEffect = Fx.shootSmall;
                                    statusDuration = 240f;
                                    puddleSize = 6f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  spiroct-r
        // ============================================================
        spiroctR = new GlowLegsUnitType("spiroct-r") {
            {
                constructor = LegsUnit::create;
                speed = 0.7f;
                drag = 0.4f;
                hitSize = 15f;
                rotateSpeed = 3f;
                health = 1000f;
                armor = 9f;

                legCount = 6;
                legLength = 13f;
                legForwardScl = 0.8f;
                legMoveSpace = 1.4f;
                legBaseOffset = 2f;
                hovering = true;
                shadowElevation = 0.3f;
                groundLayer = Layer.legUnit;

                stepSound = Sounds.walkerStepSmall;
                stepSoundPitch = 0.7f;
                stepSoundVolume = 0.35f;

                weapons.add(new Weapon("spiroct-weapon") {
                        {
                            shootY = 4f;
                            reload = 14f;
                            recoil = 2f;
                            rotate = true;
                            ejectEffect = Fx.none;
                            cooldownTime = 15f;
                            heatColor = Color.valueOf("BF92F9");
                            shootSound = Sounds.shootSap;
                            x = 8.5f;
                            y = -1.5f;

                            bullet = new SapBulletType() {
                                {
                                    sapStrength = 0.5f;
                                    length = 75f;
                                    damage = 23;
                                    shootEffect = Fx.shootSmall;
                                    hitColor = color = Color.valueOf("bf92f9");
                                    despawnEffect = Fx.none;
                                    width = 0.54f;
                                    lifetime = 35f;
                                    knockback = -1.24f;
                                }
                            };
                        }
                    });

                weapons.add(new Weapon("mount-purple-weapon") {
                        {
                            reload = 18f;
                            rotate = true;
                            x = 4f;
                            y = 3f;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("BF92F9");
                            shootSound = Sounds.shootSap;

                            bullet = new SapBulletType() {
                                {
                                    sapStrength = 0.8f;
                                    length = 40f;
                                    damage = 18;
                                    shootEffect = Fx.shootSmall;
                                    hitColor = color = Color.valueOf("bf92f9");
                                    despawnEffect = Fx.none;
                                    width = 0.4f;
                                    lifetime = 25f;
                                    knockback = -0.65f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  arkyid-r
        // ============================================================
        arkyidR = new GlowLegsUnitType("arkyid-r") {
            {
                constructor = LegsUnit::create;
                drag = 0.1f;
                speed = 0.65f;
                hitSize = 23f;
                health = 8000f;
                armor = 14f;
                rotateSpeed = 2.7f;

                legCount = 6;
                legMoveSpace = 1f;
                legPairOffset = 3;
                legLength = 30f;
                legExtension = -15;
                legBaseOffset = 10f;
                stepShake = 1f;
                legLengthScl = 0.96f;
                rippleScale = 2f;
                legSpeed = 0.2f;
                stepSound = Sounds.walkerStep;
                stepSoundVolume = 0.85f;
                stepSoundPitch = 1.1f;
                legSplashDamage = 32;
                legSplashRange = 30;
                hovering = true;
                shadowElevation = 0.65f;
                groundLayer = Layer.legUnit;

                SapBulletType sapper = new SapBulletType() {
                    {
                        sapStrength = 0.85f;
                        length = 55f;
                        damage = 40;
                        shootEffect = Fx.shootSmall;
                        hitColor = color = Color.valueOf("bf92f9");
                        despawnEffect = Fx.none;
                        width = 0.55f;
                        lifetime = 30f;
                        knockback = -1f;
                    }
                };

                weapons.add(new Weapon("spiroct-weapon") {
                        {
                            reload = 9f;
                            x = 4f;
                            y = 8f;
                            rotate = true;
                            cooldownTime = 15f;
                            heatColor = Color.valueOf("BF92F9");
                            bullet = sapper;
                            shootSound = Sounds.shootSap;
                        }
                    });
                weapons.add(new Weapon("spiroct-weapon") {
                        {
                            reload = 14f;
                            x = 9f;
                            y = 6f;
                            rotate = true;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("BF92F9");
                            bullet = sapper;
                            shootSound = Sounds.shootSap;
                        }
                    });
                weapons.add(new Weapon("spiroct-weapon") {
                        {
                            reload = 22f;
                            x = 14f;
                            y = 0f;
                            rotate = true;
                            cooldownTime = 30f;
                            heatColor = Color.valueOf("BF92F9");
                            bullet = sapper;
                            shootSound = Sounds.shootSap;
                        }
                    });
                weapons.add(new Weapon("large-purple-mount") {
                        {
                            y = -7f;
                            x = 9f;
                            shootY = 7f;
                            reload = 45f;
                            shake = 3f;
                            rotateSpeed = 2f;
                            rotate = true;
                            shadow = 8f;
                            recoil = 3f;
                            ejectEffect = Fx.casing1;
                            cooldownTime = 45f;
                            heatColor = Color.valueOf("BF92F9");
                            shootSound = Sounds.shootArtillerySap;

                            bullet = new ArtilleryBulletType(4f, 12) {
                                {
                                    hitEffect = Fx.sapExplosion;
                                    despawnSound = Sounds.explosionArtilleryShock;
                                    knockback = 0.8f;
                                    lifetime = 35f;
                                    width = 19f;
                                    height = 19f;
                                    collidesTiles = true;
                                    ammoMultiplier = 4f;
                                    splashDamage = 65f;
                                    splashDamageRadius = 70f;
                                    lightning = 3;
                                    lightningLength = 10;
                                    lightningColor = Color.valueOf("bf92f9");
                                    status = StatusEffects.sapped;
                                    statusDuration = 600f;
                                    backColor = Color.valueOf("6d56bf");
                                    frontColor = Color.valueOf("bf92f9");
                                    smokeEffect = Fx.shootBigSmoke2;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  toxopid-r
        // ============================================================
        toxopidR = new GlowLegsUnitType("toxopid-r") {
            {
                constructor = LegsUnit::create;
                drag = 0.1f;
                speed = 0.65f;
                hitSize = 26f;
                health = 22000f;
                armor = 22f;
                rotateSpeed = 1.9f;

                legCount = 8;
                legMoveSpace = 0.8f;
                legPairOffset = 3;
                legLength = 75f;
                legExtension = -20;
                legBaseOffset = 8f;
                stepShake = 1f;
                legLengthScl = 0.93f;
                rippleScale = 3f;
                legSpeed = 0.19f;
                legSplashDamage = 80;
                legSplashRange = 60;
                hovering = true;
                shadowElevation = 0.95f;
                groundLayer = Layer.legUnit;

                weapons.add(new Weapon("large-purple-mount") {
                        {
                            y = -5f;
                            x = 11f;
                            shootY = 7f;
                            reload = 30f;
                            shake = 4f;
                            rotateSpeed = 2f;
                            rotate = true;
                            shadow = 12f;
                            recoil = 3f;
                            ejectEffect = Fx.casing1;
                            cooldownTime = 60f;
                            heatColor = Color.valueOf("BF92F9");
                            shootSound = Sounds.shootToxopidShotgun;
                            shootSoundVolume = 0.8f;

                            shoot = new ShootSpread(3, 17f);

                            bullet = new ShrapnelBulletType() {
                                {
                                    length = 75f;
                                    damage = 110f;
                                    width = 25f;
                                    knockback = -2f;
                                    serrations = 4;
                                    serrationLenScl = 3f;
                                    serrationSpaceOffset = 60f;
                                    serrationFadeOffset = 0f;
                                    serrationWidth = 5f;
                                    fromColor = Color.valueOf("bf92f9");
                                    toColor = Color.valueOf("6d56bf");
                                    shootEffect = Fx.sparkShoot;
                                    smokeEffect = Fx.sparkShoot;
                                }
                            };
                        }
                    });

                weapons.add(new Weapon("toxopid-cannon") {
                        {
                            y = -14f;
                            x = 0f;
                            shootY = 22f;
                            shake = 10f;
                            recoil = 10f;
                            rotate = true;
                            rotateSpeed = 1f;
                            rotationLimit = 80f;
                            shadow = 30f;
                            mirror = false;
                            reload = 210f;
                            cooldownTime = 180f;
                            heatColor = Color.valueOf("BF92F9");
                            shootSound = Sounds.shootArtillerySapBig;

                            bullet = new ArtilleryBulletType(6f, 50) {
                                {
                                    despawnSound = Sounds.explosionArtilleryShockBig;
                                    hitEffect = Fx.sapExplosion;
                                    knockback = 0.8f;
                                    lifetime = 40f;
                                    width = 25f;
                                    height = 25f;
                                    collidesTiles = true;
                                    collides = true;
                                    ammoMultiplier = 4f;
                                    splashDamage = 75f;
                                    splashDamageRadius = 80f;
                                    lightning = 5;
                                    lightningLength = 20;
                                    lightningColor = Color.valueOf("bf92f9");
                                    status = StatusEffects.sapped;
                                    statusDuration = 600f;
                                    hitShake = 10f;
                                    lightRadius = 40f;
                                    lightColor = Color.valueOf("665c9f");
                                    lightOpacity = 0.6f;
                                    smokeEffect = Fx.shootBigSmoke2;
                                    fragBullets = 9;
                                    fragLifeMin = 0.3f;
                                    backColor = Color.valueOf("6d56bf");
                                    frontColor = Color.valueOf("bf92f9");

                                    fragBullet = new ArtilleryBulletType(4.6f, 30) {
                                        {
                                            despawnSound = Sounds.explosionArtilleryShock;
                                            hitEffect = Fx.sapExplosion;
                                            knockback = 0.8f;
                                            lifetime = 45f;
                                            width = 20f;
                                            height = 20f;
                                            collidesTiles = false;
                                            splashDamage = 40f;
                                            splashDamageRadius = 70f;
                                            lightning = 2;
                                            lightningLength = 5;
                                            lightningColor = Color.valueOf("bf92f9");
                                            status = StatusEffects.sapped;
                                            statusDuration = 600f;
                                            hitShake = 5f;
                                            lightRadius = 30f;
                                            lightColor = Color.valueOf("665c9f");
                                            lightOpacity = 0.5f;
                                            backColor = Color.valueOf("6d56bf");
                                            frontColor = Color.valueOf("bf92f9");
                                            smokeEffect = Fx.shootBigSmoke2;
                                        }
                                    };
                                }
                            };
                        }
                    });
            }
        };
    }
    private static void loadSerpuloAirCombat() {
        // ============================================================
        //  flare-r
        // ============================================================
        flareR = new UnitType("flare-r") {
            {
                researchCostMultiplier = 0.5f;
                speed = 4f;
                accel = 0.08f;
                drag = 0.04f;
                flying = true;
                health = 90f;
                engineOffset = 5.75f;
                targetFlags = new BlockFlag[]{
                    BlockFlag.generator, null};
                hitSize = 9f;
                itemCapacity = 10;
                circleTarget = true;
                omniMovement = true;
                rotateSpeed = 4f;
                circleTargetRadius = 60f;
                wreckSoundVolume = 0.7f;
                faceTarget = false;

                moveSound = Sounds.loopThruster;
                moveSoundPitchMin = 0.3f;
                moveSoundPitchMax = 1.5f;
                moveSoundVolume = 0.2f;

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
        //  horizon-r
        // ============================================================
        horizonR = new UnitType("horizon-r") {
            {
                health = 340f;
                speed = 1.65f;
                accel = 0.08f;
                drag = 0.03f;
                flying = true;
                hitSize = 11f;
                targetAir = false;
                engineOffset = 7.8f;
                range = 140f;
                faceTarget = false;
                autoDropBombs = true;
                armor = 3f;
                itemCapacity = 40;
                targetFlags = new BlockFlag[]{
                    BlockFlag.factory, null};
                circleTarget = true;
                omniMovement = false;
                rotateSpeed = 4.5f;
                circleTargetRadius = 40f;

                moveSound = Sounds.loopThruster;
                moveSoundPitchMin = 0.6f;
                moveSoundVolume = 0.4f;

                weapons.add(new Weapon() {
                        {
                            minShootVelocity = 1f;
                            x = 3f;
                            shootY = 0f;
                            reload = 12f;
                            shootCone = 360f;
                            ejectEffect = Fx.none;
                            inaccuracy = 15f;
                            ignoreRotation = true;
                            shootSound = Sounds.shootHorizon;
                            soundPitchMax = 1.2f;

                            bullet = new BombBulletType(13.5f, 25f) {
                                {
                                    width = 10f;
                                    height = 14f;
                                    hitEffect = Fx.flakExplosion;
                                    shootEffect = Fx.none;
                                    smokeEffect = Fx.none;
                                    status = StatusEffects.blasted;
                                    statusDuration = 60f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  zenith-r
        // ============================================================
        zenithR = new UnitType("zenith-r") {
            {
                health = 700f;
                speed = 1.7f;
                accel = 0.04f;
                drag = 0.016f;
                flying = true;
                range = 140f;
                hitSize = 20f;
                lowAltitude = true;
                forceMultiTarget = true;
                armor = 5f;

                targetFlags = new BlockFlag[]{
                    BlockFlag.launchPad, BlockFlag.storage, BlockFlag.battery, null};
                engineOffset = 12f;
                engineSize = 3f;

                weapons.add(new Weapon("zenith-missiles") {
                        {
                            reload = 50f;
                            x = 7f;
                            rotate = true;
                            shake = 1f;
                            inaccuracy = 5f;
                            velocityRnd = 0.2f;
                            shootSound = Sounds.shootMissileLong;
                            cooldownTime = 80f;
                            heatColor = Color.valueOf("FFA665");

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
        //  antumbra-r
        // ============================================================
        antumbraR = new UnitType("antumbra-r") {
            {
                speed = 1f;
                accel = 0.04f;
                drag = 0.04f;
                rotateSpeed = 1.9f;
                flying = true;
                lowAltitude = true;
                health = 6500f;
                armor = 17f;
                engineOffset = 21f;
                engineSize = 5.3f;
                hitSize = 46f;
                targetFlags = new BlockFlag[]{
                    BlockFlag.generator, BlockFlag.core, null};

                loopSound = Sounds.loopHover;

                abilities.add(new ShieldArcAbility() {
                        {
                            radius = 45f;
                            angle = 80f;
                            regen = 0.75f;
                            cooldown = 480f;
                            max = 1000f;
                            y = -10f;
                            width = 8f;
                            whenShooting = true;
                        }
                    });

                // 两个导弹武器共用一颗弹
                MissileBulletType missiles = new MissileBulletType(7f, 18) {
                    {
                        width = 8f;
                        height = 8f;
                        shrinkY = 0f;
                        drag = -0.01f;
                        splashDamageRadius = 20f;
                        splashDamage = 40f;
                        ammoMultiplier = 4f;
                        lifetime = 25f;
                        hitEffect = Fx.blastExplosion;
                        despawnEffect = Fx.blastExplosion;
                        status = StatusEffects.blasted;
                        statusDuration = 60f;
                        weaveScale = 6f;
                        weaveMag = -1f;
                    }
                };

                weapons.add(
                    new Weapon("zenith-missiles") {
                        {
                            x = 17f;
                            y = 8f;
                            reload = 20f;
                            ejectEffect = Fx.casing1;
                            rotateSpeed = 8f;
                            shootSound = Sounds.shootMissile;
                            rotate = true;
                            shadow = 6f;
                            cooldownTime = 30f;
                            heatColor = Color.valueOf("FFA665");
                            bullet = missiles;

                            shoot = new ShootSpread() {
                                {
                                    shots = 2;
                                    shotDelay = 0f;
                                    spread = 8f;
                                }
                            };
                        }
                    },
                    new Weapon("zenith-missiles") {
                        {
                            x = 17f;
                            y = -8f;
                            reload = 40f;
                            ejectEffect = Fx.casing1;
                            rotateSpeed = 8f;
                            shootSound = Sounds.shootMissile;
                            rotate = true;
                            shadow = 6f;
                            cooldownTime = 60f;
                            heatColor = Color.valueOf("FFA665");

                            bullet = new MissileBulletType(7f, 18) {
                                {
                                    width = 8f;
                                    height = 8f;
                                    shrinkY = 0f;
                                    drag = -0.01f;
                                    splashDamageRadius = 20f;
                                    splashDamage = 30f;
                                    ammoMultiplier = 4f;
                                    lifetime = 25f;
                                    hitEffect = Fx.blastExplosion;
                                    despawnEffect = Fx.blastExplosion;
                                    status = StatusEffects.blasted;
                                    statusDuration = 60f;
                                    weaveScale = 6f;
                                    weaveMag = -1f;
                                }
                            };

                            shoot.shots = 4;
                            shoot.shotDelay = 5f;
                        }
                    },
                    new Weapon("large-bullet-mount") {
                        {
                            y = 2f;
                            x = 10f;
                            shootY = 10f;
                            reload = 12f;
                            shake = 1f;
                            rotateSpeed = 2f;
                            ejectEffect = Fx.casing1;
                            shootSound = Sounds.shootSpectre;
                            rotate = true;
                            shadow = 8f;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("FFA665");

                            bullet = new BasicBulletType(14f, 55) {
                                {
                                    width = 12f;
                                    height = 18f;
                                    lifetime = 12.5f;
                                    shootEffect = Fx.shootBig;
                                }
                            };
                        }
                    }
                );
            }
        };

        // ============================================================
        //  eclipse-r
        // ============================================================
        eclipseR = new UnitType("eclipse-r") {
            {
                speed = 0.6f;
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
                targetFlags = new BlockFlag[]{
                    BlockFlag.reactor, BlockFlag.battery, BlockFlag.core, null};

                loopSound = Sounds.loopHover;

                FlakBulletType fragBullet = new FlakBulletType(8f, 30) {
                    {
                        shootEffect = Fx.shootBig;
                        ammoMultiplier = 4f;
                        splashDamage = 45f;
                        splashDamageRadius = 25f;
                        collidesGround = true;
                        lifetime = 23.5f;
                        status = StatusEffects.blasted;
                        statusDuration = 60f;
                        hitEffect = Fx.flakExplosion;
                    }
                };

                weapons.add(
                    new Weapon("large-laser-mount") {
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
                            cooldownTime = 60f;
                            heatColor = Color.valueOf("FFA665");

                            bullet = new LaserBulletType() {
                                {
                                    damage = 150f;
                                    sideAngle = 20f;
                                    sideWidth = 1.5f;
                                    sideLength = 80f;
                                    width = 25f;
                                    length = 230f;
                                    shootEffect = Fx.shockwave;
                                    colors = new Color[]{
                                        Color.valueOf("ec7458aa"),
                                        Color.valueOf("ec7458"),
                                        Color.valueOf("ff9c5a"),
                                        Color.white
                                    };
                                }
                            };
                        }
                    },
                    new Weapon("large-artillery") {
                        {
                            x = 11f;
                            y = 27f;
                            rotateSpeed = 2f;
                            reload = 4f;
                            shootSound = Sounds.shootCyclone;
                            shadow = 7f;
                            rotate = true;
                            recoil = 0.5f;
                            shootY = 7.25f;
                            cooldownTime = 15f;
                            heatColor = Color.valueOf("FFA665");

                            bullet = fragBullet;
                        }
                    },
                    new Weapon("large-artillery") {
                        {
                            y = -13f;
                            x = 20f;
                            reload = 6f;
                            ejectEffect = Fx.casing1;
                            rotateSpeed = 7f;
                            shake = 1f;
                            shootSound = Sounds.shootCyclone;
                            rotate = true;
                            shadow = 12f;
                            shootY = 7.25f;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("FFA665");

                            bullet = new FlakBulletType(8f, 30) {
                                {
                                    shootEffect = Fx.shootBig;
                                    ammoMultiplier = 4f;
                                    splashDamage = 65f;
                                    splashDamageRadius = 25f;
                                    collidesGround = true;
                                    lifetime = 23.5f;
                                    status = StatusEffects.blasted;
                                    statusDuration = 60f;
                                    hitEffect = Fx.flakExplosion;
                                }
                            };
                        }
                    }
                );
            }
        };
    }
    private static void loadSerpuloAirSupport() {
        // ============================================================
        //  mono-r
        // ============================================================
        monoR = new UnitType("mono-r") {
            {
                defaultCommand = UnitCommand.mineCommand;
                flying = true;
                drag = 0.06f;
                accel = 0.12f;
                speed = 1.5f;
                health = 100f;
                engineSize = 1.8f;
                engineOffset = 5.7f;
                range = 50f;
                isEnemy = false;
                controlSelectGlobal = false;
                wreckSoundVolume = deathSoundVolume = 0.7f;

                mineTier = 1;
                mineSpeed = 4.5f;
            }
        };

        // ============================================================
        //  poly-r
        // ============================================================
        polyR = new UnitType("poly-r") {
            {
                defaultCommand = UnitCommand.rebuildCommand;
                flying = true;
                drag = 0.05f;
                speed = 2.6f;
                rotateSpeed = 15f;
                accel = 0.1f;
                range = 130f;
                health = 400f;
                buildSpeed = 0.4f;
                engineOffset = 6.5f;
                hitSize = 9f;
                lowAltitude = true;

                mineTier = 2;
                mineSpeed = 4f;
                wreckSoundVolume = 0.9f;

                abilities.add(new RepairFieldAbility() {
                        {
                            amount = 5f;
                            reload = 60f * 8f;
                            range = 50f;
                        }
                    });

                weapons.add(new Weapon("poly-weapon") {
                        {
                            top = false;
                            y = -2.5f;
                            x = 3.75f;
                            reload = 30f;
                            ejectEffect = Fx.none;
                            recoil = 2f;
                            shootSound = Sounds.shootMissilePlasmaShort;
                            cooldownTime = 50f;
                            heatColor = Color.valueOf("84F491");
                            velocityRnd = 0.5f;
                            inaccuracy = 15f;
                            alternate = true;

                            bullet = new FlakBulletType(5f, 6) {
                                {
                                    sprite = "missile-large";
                                    collidesGround = true;
                                    collidesAir = true;
                                    explodeRange = 40f;
                                    width = 6f;
                                    height = 6f;
                                    shrinkY = 0f;
                                    drag = -0.003f;
                                    homingRange = 60f;
                                    keepVelocity = false;
                                    lightRadius = 60f;
                                    lightOpacity = 0.7f;
                                    lightColor = Color.valueOf("98ffa9");
                                    despawnSound = Sounds.explosion;
                                    splashDamageRadius = 30f;
                                    splashDamage = 6f;
                                    lifetime = 30f;
                                    backColor = Color.valueOf("98ffa9");
                                    frontColor = Color.white;
                                    weaveScale = 8f;
                                    weaveMag = 1f;
                                    trailColor = Color.valueOf("98ffa9");
                                    trailWidth = 2f;
                                    trailLength = 25;
                                    healPercent = 2.8f;
                                    collidesTeam = true;
                                    fragBullets = 2;
                                    fragVelocityMin = 0.3f;

                                    fragBullet = new MissileBulletType(3.9f, 6) {
                                        {
                                            homingPower = 0.2f;
                                            weaveMag = 4;
                                            weaveScale = 4;
                                            lifetime = 10f;
                                            keepVelocity = false;
                                            shootEffect = Fx.shootHeal;
                                            smokeEffect = Fx.hitLaser;
                                            splashDamage = 6f;
                                            splashDamageRadius = 20f;
                                            frontColor = Color.white;
                                            hitSound = Sounds.none;
                                            lightColor = Color.valueOf("98ffa9");
                                            lightRadius = 40f;
                                            lightOpacity = 0.7f;
                                            trailColor = Color.valueOf("98ffa9");
                                            trailWidth = 2.5f;
                                            trailLength = 20;
                                            trailChance = -1f;
                                            healPercent = 2.8f;
                                            collidesTeam = true;
                                            backColor = Color.valueOf("98ffa9");
                                            despawnEffect = Fx.none;
                                            hitEffect = Fx.hitLaserBlast;
                                        }
                                    };
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  mega-r
        // ============================================================
        megaR = new UnitType("mega-r") {
            {
                constructor = PayloadUnit::create;
                defaultCommand = UnitCommand.repairCommand;
                mineTier = 3;
                mineSpeed = 4.5f;
                health = 460f;
                armor = 3f;
                speed = 2.5f;
                accel = 0.06f;
                drag = 0.017f;
                lowAltitude = true;
                flying = true;
                engineOffset = 10.5f;
                faceTarget = false;
                hitSize = 16.05f;
                engineSize = 3f;
                payloadCapacity = (2 * 2) * tilePayload;
                buildSpeed = 2.6f;
                isEnemy = false;

                weapons.add(
                    new Weapon("heal-weapon-mount") {
                        {
                            shootSound = Sounds.shootLaser;
                            reload = 24f;
                            x = 8f;
                            y = -6f;
                            rotate = true;
                            cooldownTime = 35f;
                            heatColor = Color.valueOf("84F491");

                            bullet = new LaserBoltBulletType(10.4f, 10) {
                                {
                                    lifetime = 17.5f;
                                    healPercent = 5.5f;
                                    collidesTeam = true;
                                    backColor = Color.valueOf("98ffa9");
                                    frontColor = Color.white;
                                }
                            };
                        }
                    },
                    new Weapon("heal-weapon-mount") {
                        {
                            shootSound = Sounds.shootLaser;
                            reload = 15f;
                            x = 4f;
                            y = 5f;
                            rotate = true;
                            cooldownTime = 25f;
                            heatColor = Color.valueOf("84F491");

                            bullet = new LaserBoltBulletType(10.4f, 8) {
                                {
                                    lifetime = 17.5f;
                                    healPercent = 3f;
                                    collidesTeam = true;
                                    backColor = Color.valueOf("98ffa9");
                                    frontColor = Color.white;
                                }
                            };
                        }
                    }
                );
            }
        };

        // ============================================================
        //  quad-r
        // ============================================================
        quadR = new UnitType("quad-r") {
            {
                constructor = PayloadUnit::create;
                armor = 10f;
                health = 6000f;
                speed = 1.2f;
                rotateSpeed = 2f;
                accel = 0.05f;
                drag = 0.017f;
                lowAltitude = false;
                flying = true;
                autoDropBombs = true;
                circleTarget = true;
                engineOffset = 13f;
                engineSize = 7f;
                faceTarget = false;
                hitSize = 36f;
                payloadCapacity = (3 * 3) * tilePayload;
                buildSpeed = 2.5f;
                buildBeamOffset = 23f;
                range = 140f;
                targetAir = false;
                targetFlags = new BlockFlag[]{
                    BlockFlag.battery, BlockFlag.factory, null};

                loopSound = Sounds.loopHover;

                weapons.add(new Weapon() {
                        {
                            x = y = 0f;
                            mirror = false;
                            reload = 55f;
                            minShootVelocity = 0.01f;
                            soundPitchMin = 1f;
                            shootSound = Sounds.shootQuad;

                            bullet = new BasicBulletType() {
                                {
                                    sprite = "large-bomb";
                                    width = height = 120/4f;
                                    maxRange = 30f;
                                    ignoreRotation = true;
                                    backColor = Pal.heal;
                                    frontColor = Color.white;
                                    mixColorTo = Color.white;
                                    hitSound = Sounds.explosionQuad;
                                    hitSoundVolume = 0.9f;
                                    shootCone = 180f;
                                    ejectEffect = Fx.none;
                                    hitShake = 4f;
                                    collidesAir = false;
                                    lifetime = 70f;
                                    despawnEffect = Fx.greenBomb;
                                    hitEffect = Fx.massiveExplosion;
                                    keepVelocity = false;
                                    spin = 2f;
                                    shrinkX = shrinkY = 0.7f;
                                    speed = 0f;
                                    collides = false;
                                    healPercent = 15f;
                                    splashDamage = 220f;
                                    splashDamageRadius = 80f;
                                    damage = splashDamage * 0.7f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  oct-r
        // ============================================================
        octR = new UnitType("oct-r") {
            {
                constructor = PayloadUnit::create;
                aiController = DefenderAI::new;
                armor = 20f;
                health = 24000f;
                speed = 0.8f;
                rotateSpeed = 1f;
                accel = 0.04f;
                drag = 0.018f;
                flying = true;
                engineOffset = 46f;
                engineSize = 7.8f;
                faceTarget = false;
                hitSize = 66f;
                payloadCapacity = (5.5f * 5.5f) * tilePayload;
                buildSpeed = 4f;
                drawShields = false;
                lowAltitude = true;
                buildBeamOffset = 43f;
                mineTier = 4;
                mineSpeed = 10f;

                loopSound = Sounds.loopHover;

                abilities.add(
                    new ForceFieldAbility(140f, 4f, 7000f, 60f * 8, 8, 0f) {
                        {
                            breakSound = Sounds.shieldBreak;
                        }
                    },
                    new RepairFieldAbility(150f, 60f, 140f),
                    new ShieldRegenFieldAbility(100f, 1250f, 240f, 140f)
                );

                // 4 个点防御炮塔
                for (float[] pos : new float[][]{
                        {
                            25f, 16f },
                        {
                            -25f, 16f },
                        {
                            25f, -32f },
                        {
                            -25f, -32f }
                    }) {
                    final float px = pos[0], py = pos[1];
                    weapons.add(new PointDefenseWeapon("point-defense-mount") {
                            {
                                x = px;
                                y = py;
                                rotate = true;
                                mirror = false;
                                recoil = 0f;
                                rotateSpeed = 24f;
                                reload = 4f;
                                targetInterval = 0f;
                                targetSwitchInterval = 0f;

                                bullet = new BulletType() {
                                    {
                                        speed = 24f;
                                        lifetime = 6.6f;
                                        shootEffect = Fx.sparkShoot;
                                        hitEffect = Fx.pointHit;
                                        maxRange = 140f;
                                        damage = 60f;
                                    }
                                };
                            }
                        });
                }
            }
        };
    }
    private static void loadSerpuloNavalCombat() {
        // ============================================================
        //  risso-r
        // ============================================================
        rissoR = new UnitType("risso-r") {
            {
                constructor = UnitWaterMove::create;
                speed = 1.1f;
                drag = 0.13f;
                hitSize = 10f;
                health = 280f;
                armor = 2f;
                accel = 0.4f;
                rotateSpeed = 3.3f;
                faceTarget = false;

                trailLength = 20;
                waveTrailX = 4f;
                trailScl = 1.3f;

                moveSoundVolume = 0.4f;
                moveSound = Sounds.shipMove;

                weapons.add(new Weapon("mount-weapon") {
                        {
                            reload = 13f;
                            x = 4f;
                            shootY = 4f;
                            y = 1.5f;
                            rotate = true;
                            ejectEffect = Fx.casing1;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("FFA665");

                            bullet = new BasicBulletType(5f, 9) {
                                {
                                    width = 7f;
                                    height = 9f;
                                    lifetime = 30f;
                                    ammoMultiplier = 2;
                                }
                            };
                        }
                    });

                weapons.add(new Weapon("missiles-mount") {
                        {
                            mirror = false;
                            reload = 25f;
                            x = 0f;
                            y = -5f;
                            rotate = true;
                            ejectEffect = Fx.casing1;
                            shootSound = Sounds.shootMissileShort;
                            cooldownTime = 40f;
                            heatColor = Color.valueOf("FFA665");

                            bullet = new MissileBulletType(5.4f, 12, "missile") {
                                {
                                    keepVelocity = true;
                                    width = 8f;
                                    height = 8f;
                                    shrinkY = 0f;
                                    drag = -0.003f;
                                    homingRange = 60f;
                                    splashDamageRadius = 25f;
                                    splashDamage = 10f;
                                    lifetime = 32.5f;
                                    trailColor = Color.gray;
                                    backColor = Color.valueOf("f9c27a");
                                    frontColor = Color.valueOf("fff8e8");
                                    hitEffect = Fx.blastExplosion;
                                    despawnEffect = Fx.blastExplosion;
                                    weaveScale = 8f;
                                    weaveMag = 2f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  minke-r
        // ============================================================
        minkeR = new UnitType("minke-r") {
            {
                constructor = UnitWaterMove::create;
                health = 600f;
                speed = 0.9f;
                drag = 0.15f;
                hitSize = 13f;
                armor = 4f;
                accel = 0.3f;
                rotateSpeed = 2.6f;
                faceTarget = false;

                moveSoundVolume = 0.55f;
                moveSoundPitchMin = moveSoundPitchMax = 0.9f;
                moveSound = Sounds.shipMove;

                trailLength = 20;
                waveTrailX = 5.5f;
                waveTrailY = -4f;
                trailScl = 1.9f;

                weapons.add(new Weapon("mount-weapon") {
                        {
                            reload = 10f;
                            x = 5f;
                            y = 3.5f;
                            rotate = true;
                            rotateSpeed = 5f;
                            inaccuracy = 8f;
                            ejectEffect = Fx.casing1;
                            shootSound = Sounds.shootDuo;
                            cooldownTime = 10f;
                            heatColor = Color.valueOf("FFA665");

                            bullet = new FlakBulletType(8.4f, 3) {
                                {
                                    collidesGround = true;
                                    ammoMultiplier = 4f;
                                    speed = 8.4f;
                                    lifetime = 26.1f;
                                    width = 6f;
                                    height = 8f;
                                    splashDamage = 20.5f * 1.5f;
                                    splashDamageRadius = 15f;
                                    hitEffect = Fx.flakExplosion;
                                    shootEffect = Fx.shootSmall;
                                }
                            };
                        }
                    });

                weapons.add(new Weapon("artillery-mount") {
                        {
                            reload = 30f;
                            x = 5f;
                            y = -5f;
                            rotate = true;
                            inaccuracy = 2f;
                            rotateSpeed = 2f;
                            shake = 1.5f;
                            ejectEffect = Fx.casing2;
                            shootSound = Sounds.shootArtillerySmall;
                            cooldownTime = 25f;
                            heatColor = Color.valueOf("FFA665");

                            bullet = new ArtilleryBulletType(6f, 20f, "shell") {
                                {
                                    hitEffect = Fx.flakExplosion;
                                    knockback = 0.8f;
                                    lifetime = 36.75f;
                                    collidesTiles = false;
                                    splashDamageRadius = 22.5f;
                                    splashDamage = 40f;
                                    width = 11f;
                                    height = 11f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  bryde-r
        // ============================================================
        brydeR = new UnitType("bryde-r") {
            {
                constructor = UnitWaterMove::create;
                health = 910f;
                speed = 0.85f;
                accel = 0.2f;
                rotateSpeed = 1.8f;
                drag = 0.17f;
                hitSize = 20f;
                armor = 7f;
                faceTarget = false;

                moveSoundVolume = 0.7f;
                moveSoundPitchMin = moveSoundPitchMax = 0.77f;
                moveSound = Sounds.shipMove;

                trailLength = 22;
                waveTrailX = 7f;
                waveTrailY = -9f;
                trailScl = 1.5f;

                abilities.add(new ShieldRegenFieldAbility(20f, 40f, 60f * 4, 60f));

                weapons.add(new Weapon("large-artillery") {
                        {
                            reload = 65f;
                            mirror = false;
                            x = 0f;
                            y = -3.5f;
                            rotateSpeed = 1.7f;
                            rotate = true;
                            shootY = 7f;
                            shake = 5f;
                            recoil = 4f;
                            shadow = 12f;
                            inaccuracy = 3f;
                            ejectEffect = Fx.casing3;
                            shootSound = Sounds.shootArtillery;
                            cooldownTime = 100f;
                            heatColor = Color.valueOf("FFA665");

                            bullet = new ArtilleryBulletType(6.4f, 15) {
                                {
                                    trailMult = 0.8f;
                                    hitEffect = Fx.massiveExplosion;
                                    knockback = 1.5f;
                                    lifetime = 42f;
                                    height = 15.5f;
                                    width = 15f;
                                    collidesTiles = false;
                                    splashDamageRadius = 40f;
                                    splashDamage = 70f;
                                    backColor = Color.valueOf("e58956");
                                    frontColor = Color.valueOf("ffd2ae");
                                    trailEffect = Fx.artilleryTrail;
                                    trailSize = 6f;
                                    hitShake = 4f;
                                    shootEffect = Fx.shootBig2;
                                    status = StatusEffects.blasted;
                                    statusDuration = 60f;
                                }
                            };
                        }
                    });

                weapons.add(new Weapon("missiles-mount") {
                        {
                            reload = 20f;
                            x = 8.5f;
                            y = -9f;
                            shadow = 6f;
                            rotateSpeed = 4f;
                            rotate = true;
                            inaccuracy = 5f;
                            velocityRnd = 0.1f;
                            shootSound = Sounds.shootMissileShort;
                            ejectEffect = Fx.none;
                            cooldownTime = 30f;
                            heatColor = Color.valueOf("FFA665");

                            shoot.shots = 2;
                            shoot.shotDelay = 3f;

                            bullet = new MissileBulletType(5.4f, 12) {
                                {
                                    width = 8f;
                                    height = 8f;
                                    shrinkY = 0f;
                                    drag = -0.003f;
                                    homingRange = 60f;
                                    keepVelocity = false;
                                    splashDamageRadius = 25f;
                                    splashDamage = 10f;
                                    lifetime = 35f;
                                    trailColor = Color.gray;
                                    backColor = Color.valueOf("e58956");
                                    frontColor = Color.valueOf("ffd2ae");
                                    hitEffect = Fx.blastExplosion;
                                    despawnEffect = Fx.blastExplosion;
                                    weaveScale = 8f;
                                    weaveMag = 1f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  sei-r
        // ============================================================
        seiR = new UnitType("sei-r") {
            {
                constructor = UnitWaterMove::create;
                health = 11000f;
                armor = 12f;

                speed = 0.73f;
                drag = 0.17f;
                hitSize = 39f;
                accel = 0.2f;
                rotateSpeed = 1.3f;
                faceTarget = false;

                moveSoundVolume = 1f;
                moveSound = Sounds.shipMoveBig;
                moveSoundPitchMin = moveSoundPitchMax = 0.95f;

                trailLength = 50;
                waveTrailX = 18f;
                waveTrailY = -21f;
                trailScl = 3f;

                weapons.add(new Weapon("sei-launcher") {
                        {
                            x = 0f;
                            y = 0f;
                            rotate = true;
                            rotateSpeed = 4f;
                            mirror = false;
                            shadow = 20f;
                            shootY = 4.5f;
                            recoil = 4f;
                            reload = 45f;
                            velocityRnd = 0.4f;
                            inaccuracy = 7f;
                            ejectEffect = Fx.none;
                            shake = 1f;
                            shootSound = Sounds.shootMissileLong;

                            shoot = new ShootAlternate() {
                                {
                                    shots = 6;
                                    shotDelay = 1.5f;
                                    spread = 4f;
                                    barrels = 3;
                                }
                            };

                            bullet = new MissileBulletType(8.4f, 42) {
                                {
                                    homingPower = 0.12f;
                                    width = 8f;
                                    height = 8f;
                                    shrinkX = shrinkY = 0f;
                                    drag = -0.003f;
                                    homingRange = 80f;
                                    keepVelocity = false;
                                    splashDamageRadius = 35f;
                                    splashDamage = 45f;
                                    lifetime = 31f;
                                    trailColor = Color.valueOf("f9c27a");
                                    backColor = Color.valueOf("f9c27a");
                                    frontColor = Color.valueOf("fff8e8");
                                    hitEffect = Fx.blastExplosion;
                                    despawnEffect = Fx.blastExplosion;
                                    weaveScale = 8f;
                                    weaveMag = 2f;
                                }
                            };
                        }
                    });

                weapons.add(new Weapon("large-bullet-mount") {
                        {
                            reload = 60f;
                            cooldownTime = 90f;
                            x = 70f/4f;
                            y = -66f/4f;
                            rotateSpeed = 4f;
                            rotate = true;
                            shootY = 7f;
                            shake = 2f;
                            recoil = 3f;
                            shadow = 12f;
                            ejectEffect = Fx.casing3;
                            shootSound = Sounds.shootSpectre;

                            shoot.shots = 3;
                            shoot.shotDelay = 4f;

                            inaccuracy = 1f;

                            bullet = new BasicBulletType(14f, 57) {
                                {
                                    width = 13f;
                                    height = 19f;
                                    shootEffect = Fx.shootBig;
                                    lifetime = 17.5f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  omura-r
        // ============================================================
        omuraR = new UnitType("omura-r") {
            {
                constructor = UnitWaterMove::create;
                health = 22000f;
                speed = 0.62f;
                drag = 0.18f;
                hitSize = 58f;
                armor = 16f;
                accel = 0.19f;
                rotateSpeed = 0.9f;
                faceTarget = false;

                moveSoundVolume = 1.1f;
                moveSound = Sounds.shipMoveBig;
                moveSoundPitchMin = moveSoundPitchMax = 0.9f;

                trailLength = 70;
                waveTrailX = 23f;
                waveTrailY = -32f;
                trailScl = 3.5f;

                weapons.add(new Weapon("omura-cannon") {
                        {
                            reload = 110f;
                            cooldownTime = 90f;
                            mirror = false;
                            x = 0f;
                            y = -3.5f;
                            rotateSpeed = 1.4f;
                            rotate = true;
                            shootY = 23f;
                            shake = 6f;
                            recoil = 10.5f;
                            shadow = 50f;
                            shootSound = Sounds.shootOmura;
                            ejectEffect = Fx.none;

                            bullet = new RailBulletType() {
                                {
                                    shootEffect = Fx.railShoot;
                                    length = 500f;
                                    pointEffectSpace = 60f;
                                    pierceEffect = Fx.railHit;
                                    pointEffect = Fx.railTrail;
                                    hitEffect = Fx.massiveExplosion;
                                    smokeEffect = Fx.shootBig2;
                                    damage = 1250f;
                                    pierceDamageFactor = 0.5f;
                                }
                            };
                        }
                    });

                // 两个防空炮塔
                for (float[] pos : new float[][]{
                        {
                            19.25f, -31.75f },
                        {
                            -19.25f, -31.75f }
                    }) {
                    final float px = pos[0], py = pos[1];
                    weapons.add(new Weapon("large-artillery") {
                            {
                                x = px;
                                y = py;
                                rotateSpeed = 5f;
                                reload = 4f;
                                shootSound = Sounds.shootCyclone;
                                shadow = 7f;
                                rotate = true;
                                recoil = 0.5f;
                                shootY = 7.25f;
                                cooldownTime = 15f;
                                heatColor = Color.valueOf("FFA665");
                                autoTarget = true;
                                controllable = false;
                                targetInterval = 0f;
                                targetSwitchInterval = 0f;
                                mirror = false;

                                bullet = new FlakBulletType(7f, 15) {
                                    {
                                        shootEffect = Fx.shootBig;
                                        ammoMultiplier = 4;
                                        splashDamage = 65f;
                                        splashDamageRadius = 25f;
                                        collidesGround = false;
                                        lifetime = 24f;
                                        status = StatusEffects.blasted;
                                        statusDuration = 60f;
                                    }
                                };
                            }
                        });
                }
            }
        };
    }
    private static void loadSerpuloNavalSupport() {
        // ============================================================
        //  retusa-r
        // ============================================================
        retusaR = new UnitType("retusa-r") {
            {
                constructor = UnitWaterMove::create;
                speed = 0.9f;
                drag = 0.14f;
                hitSize = 11f;
                health = 270f;
                accel = 0.4f;
                rotateSpeed = 5f;
                trailLength = 20;
                waveTrailX = 5f;
                trailScl = 1.3f;
                faceTarget = false;
                range = 100f;
                armor = 3f;

                moveSoundVolume = 0.4f;
                moveSound = Sounds.shipMove;

                buildSpeed = 1.5f;
                rotateToBuilding = false;

                weapons.add(new RepairBeamWeapon("repair-beam-weapon-center") {
                        {
                            x = 0f;
                            y = -5.5f;
                            shootY = 6f;
                            beamWidth = 0.8f;
                            mirror = false;
                            repairSpeed = 0.75f;

                            bullet = new BulletType() {
                                {
                                    maxRange = 120f;
                                }
                            };
                        }
                    });

                weapons.add(new Weapon("retusa-weapon") {
                        {
                            shootSound = Sounds.shootLaser;
                            reload = 22f;
                            x = 4.5f;
                            y = -3.5f;
                            rotateSpeed = 5f;
                            mirror = true;
                            rotate = true;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("84F491");

                            bullet = new LaserBoltBulletType(5.2f, 12) {
                                {
                                    lifetime = 30f;
                                    healPercent = 5.5f;
                                    collidesTeam = true;
                                    backColor = Pal.heal;
                                    frontColor = Color.white;
                                }
                            };
                        }
                    });

                weapons.add(new Weapon() {
                        {
                            mirror = false;
                            rotate = true;
                            reload = 90f;
                            x = y = shootX = shootY = 0f;
                            shootSound = Sounds.shootRetusa;
                            rotateSpeed = 180f;
                            shootSoundVolume = 0.9f;

                            shoot.shots = 3;
                            shoot.shotDelay = 7f;

                            bullet = new BasicBulletType() {
                                {
                                    sprite = "mine-bullet";
                                    width = height = 8f;
                                    layer = Layer.scorch;
                                    shootEffect = smokeEffect = Fx.none;

                                    maxRange = 50f;
                                    ignoreRotation = true;
                                    healPercent = 4f;

                                    backColor = Pal.heal;
                                    frontColor = Color.white;
                                    mixColorTo = Color.white;

                                    hitSound = Sounds.explosionPlasmaSmall;
                                    underwater = true;

                                    ejectEffect = Fx.none;
                                    hitSize = 22f;

                                    collidesAir = false;

                                    lifetime = 87f;

                                    hitEffect = new MultiEffect(Fx.blastExplosion, Fx.greenCloud);
                                    keepVelocity = false;

                                    shrinkX = shrinkY = 0f;

                                    inaccuracy = 2f;
                                    weaveMag = 5f;
                                    weaveScale = 4f;
                                    speed = 0.7f;
                                    drag = -0.017f;
                                    homingPower = 0.05f;
                                    collideFloor = true;
                                    trailColor = Pal.heal;
                                    trailWidth = 3f;
                                    trailLength = 8;

                                    splashDamage = 40f;
                                    splashDamageRadius = 32f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  oxynoe-r
        // ============================================================
        oxynoeR = new UnitType("oxynoe-r") {
            {
                constructor = UnitWaterMove::create;
                health = 560f;
                speed = 0.83f;
                drag = 0.14f;
                hitSize = 14f;
                armor = 4f;
                accel = 0.4f;
                rotateSpeed = 4f;
                faceTarget = false;

                moveSoundVolume = 0.55f;
                moveSoundPitchMin = moveSoundPitchMax = 0.9f;
                moveSound = Sounds.shipMove;

                trailLength = 22;
                waveTrailX = 5.5f;
                waveTrailY = -4f;
                trailScl = 1.9f;

                abilities.add(new StatusFieldAbility(StatusEffects.overclock, 60f * 6, 60f * 6f, 60f));

                buildSpeed = 2f;
                rotateToBuilding = false;

                weapons.add(new Weapon("plasma-mount-weapon") {
                        {
                            reload = 5f;
                            x = 4.5f;
                            y = 6.5f;
                            rotate = true;
                            rotateSpeed = 5f;
                            inaccuracy = 10f;
                            shootCone = 30f;
                            ejectEffect = Fx.casing1;
                            shootSound = Sounds.shootFlamePlasma;
                            shootSoundVolume = 0.9f;
                            cooldownTime = 20f;
                            heatColor = Color.valueOf("84F491");

                            bullet = new BulletType(3.4f, 23f) {
                                {
                                    healPercent = 1.5f;
                                    collidesTeam = true;
                                    ammoMultiplier = 3f;
                                    hitSize = 7f;
                                    lifetime = 18f;
                                    pierce = true;
                                    collidesAir = false;
                                    statusDuration = 60f * 4;
                                    hitEffect = Fx.hitFlamePlasma;
                                    ejectEffect = Fx.none;
                                    despawnEffect = Fx.none;
                                    status = StatusEffects.burning;
                                    keepVelocity = false;
                                    hittable = false;
                                    shootEffect = Fx.shootSmallFlame;
                                }
                            };
                        }
                    });

                weapons.add(new PointDefenseWeapon("point-defense-mount") {
                        {
                            mirror = false;
                            x = 0f;
                            y = 1f;
                            reload = 9f;
                            targetInterval = 10f;
                            targetSwitchInterval = 15f;

                            bullet = new BulletType() {
                                {
                                    shootEffect = Fx.sparkShoot;
                                    hitEffect = Fx.pointHit;
                                    maxRange = 100f;
                                    damage = 17f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  cyerce-r
        // ============================================================
        cyerceR = new UnitType("cyerce-r") {
            {
                constructor = UnitWaterMove::create;
                health = 870f;
                speed = 0.86f;
                accel = 0.22f;
                rotateSpeed = 2.6f;
                drag = 0.16f;
                hitSize = 20f;
                armor = 6f;
                faceTarget = false;

                moveSoundVolume = 0.7f;
                moveSoundPitchMin = moveSoundPitchMax = 0.77f;
                moveSound = Sounds.shipMove;

                trailLength = 23;
                waveTrailX = 9f;
                waveTrailY = -9f;
                trailScl = 2f;

                buildSpeed = 2f;
                rotateToBuilding = false;

                weapons.add(new RepairBeamWeapon("repair-beam-weapon-center") {
                        {
                            x = 11f;
                            y = -10f;
                            shootY = 6f;
                            beamWidth = 0.8f;
                            repairSpeed = 0.7f;

                            bullet = new BulletType() {
                                {
                                    maxRange = 130f;
                                }
                            };
                        }
                    });

                weapons.add(new Weapon("plasma-missile-mount") {
                        {
                            reload = 60f;
                            x = 9f;
                            y = 3f;
                            shadow = 5f;
                            rotateSpeed = 4f;
                            rotate = true;
                            inaccuracy = 1f;
                            velocityRnd = 0.1f;
                            shootSound = Sounds.shootMissilePlasma;
                            ejectEffect = Fx.none;
                            cooldownTime = 40f;
                            heatColor = Color.valueOf("84F491");

                            bullet = new FlakBulletType(2.5f, 25) {
                                {
                                    sprite = "missile-large";
                                    collidesGround = collidesAir = true;
                                    explodeRange = 40f;
                                    width = height = 12f;
                                    shrinkY = 0f;
                                    drag = -0.003f;
                                    homingRange = 60f;
                                    keepVelocity = false;
                                    lightRadius = 60f;
                                    lightOpacity = 0.7f;
                                    lightColor = Pal.heal;

                                    splashDamageRadius = 30f;
                                    splashDamage = 25f;

                                    lifetime = 80f;
                                    backColor = Pal.heal;
                                    frontColor = Color.white;

                                    hitEffect = new ExplosionEffect() {
                                        {
                                            lifetime = 28f;
                                            waveStroke = 6f;
                                            waveLife = 10f;
                                            waveRadBase = 7f;
                                            waveColor = Pal.heal;
                                            waveRad = 30f;
                                            smokes = 6;
                                            smokeColor = Color.white;
                                            sparkColor = Pal.heal;
                                            sparks = 6;
                                            sparkRad = 35f;
                                            sparkStroke = 1.5f;
                                            sparkLen = 4f;
                                        }
                                    };

                                    weaveScale = 8f;
                                    weaveMag = 1f;

                                    trailColor = Pal.heal;
                                    trailWidth = 4.5f;
                                    trailLength = 29;

                                    fragBullets = 7;
                                    fragVelocityMin = 0.3f;

                                    fragBullet = new MissileBulletType(3.9f, 11) {
                                        {
                                            homingPower = 0.2f;
                                            weaveMag = 4;
                                            weaveScale = 4;
                                            lifetime = 60f;
                                            keepVelocity = false;
                                            shootEffect = Fx.shootHeal;
                                            smokeEffect = Fx.hitLaser;
                                            splashDamage = 13f;
                                            splashDamageRadius = 20f;
                                            frontColor = Color.white;
                                            hitSound = Sounds.none;

                                            lightColor = Pal.heal;
                                            lightRadius = 40f;
                                            lightOpacity = 0.7f;

                                            trailColor = Pal.heal;
                                            trailWidth = 2.5f;
                                            trailLength = 20;
                                            trailChance = -1f;

                                            healPercent = 2.8f;
                                            collidesTeam = true;
                                            backColor = Pal.heal;

                                            despawnEffect = Fx.none;
                                            hitEffect = new ExplosionEffect() {
                                                {
                                                    lifetime = 20f;
                                                    waveStroke = 2f;
                                                    waveColor = Pal.heal;
                                                    waveRad = 12f;
                                                    smokeSize = 0f;
                                                    smokeSizeBase = 0f;
                                                    sparkColor = Pal.heal;
                                                    sparks = 9;
                                                    sparkRad = 35f;
                                                    sparkLen = 4f;
                                                    sparkStroke = 1.5f;
                                                }
                                            };
                                        }
                                    };
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  aegires-r
        // ============================================================
        aegiresR = new UnitType("aegires-r") {
            {
                constructor = UnitWaterMove::create;
                health = 12000f;
                armor = 12f;

                speed = 0.7f;
                drag = 0.17f;
                hitSize = 44f;
                accel = 0.2f;
                rotateSpeed = 1.4f;
                faceTarget = false;

                moveSoundVolume = 1f;
                moveSound = Sounds.shipMoveBig;
                moveSoundPitchMin = moveSoundPitchMax = 0.95f;

                clipSize = 250f;

                trailLength = 50;
                waveTrailX = 18f;
                waveTrailY = -17f;
                trailScl = 3.2f;

                buildSpeed = 3f;
                rotateToBuilding = false;
                range = maxRange = 180f;

                abilities.add(new EnergyFieldAbility(40f, 65f, 180f) {
                        {
                            statusDuration = 60f * 6f;
                            maxTargets = 25;
                            healPercent = 1.5f;
                            sameTypeHealMult = 0.5f;
                        }
                    });

                for (float mountY : new float[]{
                        -18f, 14f}) {
                    final float my = mountY;
                    weapons.add(new PointDefenseWeapon("point-defense-mount") {
                            {
                                x = 12.5f;
                                y = my;
                                reload = 4f;
                                targetInterval = 8f;
                                targetSwitchInterval = 8f;

                                bullet = new BulletType() {
                                    {
                                        shootEffect = Fx.sparkShoot;
                                        hitEffect = Fx.pointHit;
                                        maxRange = 180f;
                                        damage = 30f;
                                    }
                                };
                            }
                        });
                }
            }
        };

        // ============================================================
        //  navanaxR  (海王 · 重写版)
        //  注意：plasma-missile 内嵌在方法内，整个单位只需在 load() 里
        //        调用一次 new navanaxR() 即可自动注册两个单位。
        // ============================================================
        navanaxR = new UnitType("navanax-r") {
            {
                // ============ 基础 ============
                constructor = UnitWaterMove::create;
                health = 20000f;
                speed = 0.65f;
                drag = 0.17f;
                hitSize = 58f;
                armor = 20f;
                accel = 0.2f;
                rotateSpeed = 1.1f;
                faceTarget = false;

                moveSoundVolume = 1.1f;
                moveSound = Sounds.shipMoveBig;
                moveSoundPitchMin = moveSoundPitchMax = 0.9f;

                trailLength = 70;
                waveTrailX = 23f;
                waveTrailY = -32f;
                trailScl = 3.5f;

                buildSpeed = 3.5f;
                rotateToBuilding = false;

                // 内嵌 plasma-missile 单位
                MissileUnitType plasmaMissile = new MissileUnitType("plasma-missile") {
                    {
                        allowedInPayloads = true;
                        logicControllable = true;
                        flying = true;
                        lowAltitude = true;
                        speed = 5f;
                        lifetime = 210f;
                        health = 240f;
                        armor = 4f;
                        hitSize = 10f;
                        rotateSpeed = 4f;
                        maxRange = 440f;
                        missileAccelTime = 50f;
                        engineSize = 3.1f;
                        engineOffset = 10f;
                        engineColor = Color.valueOf("98ffa9");
                        engineLayer = Layer.effect;
                        trailLength = 18;
                        trailColor = Color.valueOf("98ffa9");
                        outlineColor = Color.valueOf("565666");
                        fogRadius = 6f;

                        deathExplosionEffect = Fx.none;
                        deathSound = Sounds.explosionMissile;
                        loopSound = Sounds.loopMissileTrail;
                        loopSoundVolume = 0.6f;

                        targetAir = false;
                        targetUnderBlocks = false;
                        hidden = true;

                        weapons.add(new Weapon() {
                                {
                                    shootOnDeath = true;
                                    reload = 1f;
                                    shootCone = 360f;
                                    shootSound = Sounds.explosion;

                                    bullet = new ExplosionBulletType(100f, 44f) {
                                        {
                                            damage = 30f;
                                            lifetime = 12f;
                                            hitShake = 4f;
                                            lightRadius = 50f;
                                            suppressionRange = 80f;
                                            suppressionDuration = 120f;
                                            suppressionEffectChance = 1f;
                                            lightColor = Color.valueOf("98ffa9");
                                            status = StatusEffects.electrified;
                                            statusDuration = 360f;
                                            makeFire = false;
                                            scaledSplashDamage = false;

                                            shootEffect = new MultiEffect(
                                                new ExplosionEffect() {
                                                    {
                                                        lifetime = 30f;
                                                        waveStroke = 3f;
                                                        waveRad = 44f;
                                                        waveColor = Color.valueOf("98ffa9");
                                                        smokeColor = Color.valueOf("98ffa988");
                                                        smokeRad = 50f;
                                                        smokes = 6;
                                                        sparkColor = Color.white;
                                                        sparks = 8;
                                                        sparkLen = 6f;
                                                    }
                                                },
                                                new WaveEffect() {
                                                    {
                                                        colorFrom = Color.valueOf("98ffa9");
                                                        colorTo = Color.white;
                                                        sizeFrom = 2f;
                                                        sizeTo = 50f;
                                                        strokeFrom = 3f;
                                                        strokeTo = 0f;
                                                        lifetime = 20f;
                                                    }
                                                },
                                                new ParticleEffect() {
                                                    {
                                                        particles = 20;
                                                        colorFrom = Color.valueOf("98ffa9");
                                                        colorTo = Color.white;
                                                        sizeFrom = 4f;
                                                        sizeTo = 0f;
                                                        lifetime = 25f;
                                                        length = 15f;
                                                        cone = 360f;
                                                    }
                                                }
                                            );
                                        }
                                    };
                                }
                            });

                        abilities.add(new MoveEffectAbility() {
                                {
                                    effect = Fx.missileTrailSmoke;
                                    interval = 7f;
                                    minVelocity = 0.5f;
                                    color = Color.valueOf("ffd37f");
                                    rotation = 180f;
                                    y = -9f;
                                }
                            });
                    }
                };

                // EMP 主炮
                weapons.add(new Weapon("emp-cannon-mount") {
                        {
                            rotate = true;
                            x = 17.5f;
                            y = -6.5f;
                            reload = 30f;
                            shake = 3f;
                            rotateSpeed = 3f;
                            shadow = 30f;
                            shootY = 7f;
                            recoil = 4f;
                            cooldownTime = 30f;
                            shootSound = Sounds.shootNavanax;

                            bullet = new EmpBulletType() {
                                {
                                    float rad = 104f;

                                    scaleLife = true;
                                    lightOpacity = 0.7f;
                                    unitDamageScl = 0.8f;
                                    healPercent = 20f;
                                    timeIncrease = 3f;
                                    timeDuration = 60f * 20f;
                                    powerDamageScl = 3f;
                                    damage = 40f;
                                    hitColor = lightColor = Pal.heal;
                                    lightRadius = 70f;
                                    shootEffect = Fx.hitEmpSpark;
                                    smokeEffect = Fx.shootBigSmoke2;
                                    lifetime = 30f;
                                    sprite = "circle-bullet";
                                    backColor = Pal.heal;
                                    frontColor = Color.white;
                                    width = height = 12f;
                                    shrinkY = 0f;
                                    speed = 10f;
                                    trailLength = 20;
                                    trailWidth = 6f;
                                    trailColor = Pal.heal;
                                    trailInterval = 3f;
                                    splashDamage = 50f;
                                    splashDamageRadius = rad;
                                    hitShake = 4f;
                                    trailRotation = true;
                                    status = StatusEffects.electrified;
                                    hitSound = Sounds.explosionNavanax;

                                    trailEffect = new MultiEffect(
                                        new ParticleEffect() {
                                            {
                                                particles = 3;
                                                colorFrom = Pal.heal;
                                                colorTo = Color.white;
                                                sizeFrom = 3f;
                                                sizeTo = 0f;
                                                lifetime = 16f;
                                                length = 10f;
                                                cone = 30f;
                                            }
                                        },
                                        new WaveEffect() {
                                            {
                                                colorFrom = Pal.heal;
                                                colorTo = Color.white;
                                                sizeFrom = 2f;
                                                sizeTo = 8f;
                                                strokeFrom = 2f;
                                                strokeTo = 0f;
                                                lifetime = 16f;
                                            }
                                        }
                                    );

                                    hitEffect = new MultiEffect(
                                        new Effect(50f, 100f, e -> {
                                                e.scaled(7f, b -> {
                                                        color(Pal.heal, b.fout());
                                                        Fill.circle(e.x, e.y, 100f);
                                                    });
                                                color(Pal.heal);
                                                stroke(e.fout() * 3f);
                                                Lines.circle(e.x, e.y, 100f);
                                                int points = 10;
                                                float offset = Mathf.randomSeed(e.id, 360f);
                                                for (int i = 0; i < points; i++) {
                                                    float angle = i * 360f / points + offset;
                                                    Drawf.tri(e.x + Angles.trnsx(angle, 100f),
                                                        e.y + Angles.trnsy(angle, 100f),
                                                        6f, 50f * e.fout(), angle);
                                                }
                                                Fill.circle(e.x, e.y, 12f * e.fout());
                                                color();
                                                Fill.circle(e.x, e.y, 6f * e.fout());
                                                Drawf.light(e.x, e.y, 100f * 1.6f, Pal.heal, e.fout());
                                            }),
                                        new DistortionFx(150f, 1.5f, 40f, DistortionFx.TYPE_INWARD, 1f, 0f, Interp.pow3In)
                                    );
                                }
                            };
                        }
                    });

                // 2 个等离子速射机枪
                for (float sign : Mathf.signs) {
                    final float sx = sign;
                    weapons.add(new Weapon("plasma-laser-mount") {
                            {
                                x = 21f * sx;
                                y = 12.5f;
                                rotate = true;
                                mirror = false;
                                autoTarget = true;
                                controllable = false;
                                targetInterval = 0f;
                                targetSwitchInterval = 0f;
                                rotateSpeed = 5f;
                                reload = 1f;
                                recoil = 0.5f;
                                shootY = 0f;
                                shootSound = Sounds.shootLaser;
                                heatColor = Color.valueOf("ff3300");
                                cooldownTime = 60f;
                                minWarmup = 0.5f;
                                shootWarmupSpeed = 0.05f;

                                shoot = new ShootBarrel() {
                                    {
                                        barrels = new float[]{
                                            -3, 6, 0,
                                            0, 3, 0,
                                            3, 6, 0
                                        };
                                    }
                                };

                                bullet = new LaserBoltBulletType(12f, 25f) {
                                    {
                                        lifetime = 20f;
                                        width = 2f;
                                        height = 10f;
                                        pierce = true;
                                        pierceCap = 2;
                                        pierceBuilding = true;
                                        armorMultiplier = 0.75f;
                                        backColor = Pal.heal;
                                        frontColor = Color.white;
                                        hitEffect = Fx.hitLaser;
                                        despawnEffect = Fx.hitLaser;

                                        shootEffect = new MultiEffect(
                                            Fx.shootSmall,
                                            new ParticleEffect() {
                                                {
                                                    particles = 5;
                                                    colorFrom = Pal.heal;
                                                    colorTo = Color.white;
                                                    sizeFrom = 3f;
                                                    sizeTo = 0f;
                                                    lifetime = 12f;
                                                    length = 6f;
                                                    cone = 20f;
                                                }
                                            },
                                            new WaveEffect() {
                                                {
                                                    colorFrom = Pal.heal;
                                                    colorTo = Color.white;
                                                    sizeFrom = 2f;
                                                    sizeTo = 8f;
                                                    strokeFrom = 2f;
                                                    strokeTo = 0f;
                                                    lifetime = 8f;
                                                }
                                            }
                                        );
                                    }
                                };
                            }
                        });
                }

                // 2 个等离子导弹
                for (float sign : Mathf.signs) {
                    final float sx = sign;
                    weapons.add(new Weapon("plasma-missile-mount") {
                            {
                                x = 21f * sx;
                                y = -29.5f;
                                rotate = true;
                                rotateSpeed = 3f;
                                mirror = false;
                                reload = 480f;
                                recoil = 2f;
                                shootY = 5f;
                                shootSound = Sounds.shootMissileLarge;
                                shootCone = 15f;

                                shoot = new ShootBarrel() {
                                    {
                                        barrels = new float[]{
                                            0, 1, 0
                                        };
                                        firstShotDelay = 90f;
                                    }
                                };

                                bullet = new BulletType() {
                                    {
                                        speed = 0f;
                                        keepVelocity = false;
                                        collidesAir = false;
                                        spawnUnit = plasmaMissile;

                                        shootEffect = new MultiEffect(
                                            Fx.shootBigSmoke,
                                            new ParticleEffect() {
                                                {
                                                    particles = 15;
                                                    colorFrom = Color.valueOf("ffd37f");
                                                    colorTo = Color.white;
                                                    sizeFrom = 4f;
                                                    sizeTo = 0f;
                                                    lifetime = 20f;
                                                    length = 15f;
                                                    cone = 25f;
                                                }
                                            }
                                        );
                                        smokeEffect = Fx.none;

                                        chargeEffect = new ParticleEffect() {
                                            {
                                                particles = 8;
                                                colorFrom = Color.valueOf("ffffff88");
                                                colorTo = Color.valueOf("98ffa900");
                                                sizeFrom = 4f;
                                                sizeTo = 12f;
                                                lifetime = 30f;
                                                length = 20f;
                                                baseLength = 5f;
                                                cone = 30f;
                                                interp = Interp.circleOut;
                                            }
                                        };
                                    }
                                };
                            }
                        });
                }

                // 压制场
                abilities.add(new SuppressionFieldAbility() {
                        {
                            orbRadius = 5f;
                            particleSize = 3f;
                            y = -10f;
                            particles = 10;
                            color = particleColor = effectColor = Pal.heal;
                        }
                    });
            }
        };
    }
    private static void loadErekirTanks() {
        // ============================================================
        //  stell-r  (TankUnitType)
        // ============================================================
        stellR = new TankUnitType("stell-r") {
            {
                constructor = TankUnit::create;
                hitSize = 12f;
                treadPullOffset = 3;
                speed = 0.75f;
                rotateSpeed = 3.5f;
                health = 850f;
                armor = 6f;
                itemCapacity = 0;
                floorMultiplier = 0.95f;
                treadRects = new Rect[]{
                    new Rect(12f - 32f, 7f - 32f, 14f, 51f)};

                tankMoveVolume *= 0.32f;
                tankMoveSound = Sounds.tankMoveSmall;

                weapons.add(new Weapon("stell-weapon") {
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
                            heatColor = Color.valueOf("f9350f");
                            cooldownTime = 30f;

                            bullet = new BasicBulletType(8f, 40) {
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
        //  locus-r
        // ============================================================
        locusR = new TankUnitType("locus-r") {
            {
                constructor = TankUnit::create;
                hitSize = 18f;
                treadPullOffset = 5;
                speed = 0.7f;
                rotateSpeed = 2.6f;
                health = 2100f;
                armor = 8f;
                itemCapacity = 0;
                floorMultiplier = 0.8f;
                treadRects = new Rect[]{
                    new Rect(17f - 96f/2f, 10f - 96f/2f, 19f, 76f)};
                crushFragile = true;

                tankMoveVolume *= 0.55f;
                tankMoveSound = Sounds.tankMove;

                weapons.add(new Weapon("locus-weapon") {
                        {
                            shootSound = Sounds.shootLocus;
                            layerOffset = 0.0001f;
                            reload = 18f;
                            shootY = 10f;
                            recoil = 1f;
                            rotate = true;
                            rotateSpeed = 1.4f;
                            mirror = false;
                            shootCone = 2f;
                            x = 0f;
                            y = 0f;
                            heatColor = Color.valueOf("f9350f");
                            cooldownTime = 30f;

                            shoot = new ShootAlternate(3.5f);

                            bullet = new RailBulletType() {
                                {
                                    length = 160f;
                                    damage = 48f;
                                    hitColor = Color.valueOf("feb380");
                                    hitEffect = endEffect = Fx.hitBulletColor;
                                    pierceDamageFactor = 0.8f;
                                    smokeEffect = Fx.colorSpark;

                                    endEffect = new Effect(14f, e -> {
                                            color(e.color);
                                            Drawf.tri(e.x, e.y, e.fout() * 1.5f, 5f, e.rotation);
                                        });

                                    shootEffect = Fx.shootBigColor;
                                    lineEffect = Fx.none;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  precept-r
        // ============================================================
        preceptR = new TankUnitType("precept-r") {
            {
                constructor = TankUnit::create;
                hitSize = 24f;
                treadPullOffset = 5;
                speed = 0.64f;
                rotateSpeed = 1.5f;
                health = 5000f;
                armor = 11f;
                itemCapacity = 0;
                floorMultiplier = 0.65f;
                drownTimeMultiplier = 1.2f;
                immunities.addAll(StatusEffects.burning, StatusEffects.melting);
                treadRects = new Rect[]{
                    new Rect(16f - 60f, 48f - 70f, 30f, 75f),
                    new Rect(44f - 60f, 17f - 70f, 17f, 60f)
                };
                crushFragile = true;

                weapons.add(new Weapon("precept-weapon") {
                        {
                            shootSound = Sounds.explosionDull;
                            layerOffset = 0.0001f;
                            reload = 80f;
                            shootY = 16f;
                            recoil = 3f;
                            rotate = true;
                            rotateSpeed = 1.625f;
                            mirror = false;
                            shootCone = 2f;
                            x = 0f;
                            y = -1f;
                            heatColor = Color.valueOf("f9350f");
                            cooldownTime = 30f;

                            bullet = new BasicBulletType(7f, 120) {
                                {
                                    sprite = "missile-large";
                                    width = 7.5f;
                                    height = 13f;
                                    lifetime = 28f;
                                    hitSize = 6f;
                                    pierceCap = 2;
                                    pierce = true;
                                    pierceBuilding = true;
                                    hitColor = backColor = trailColor = Color.valueOf("feb380");
                                    frontColor = Color.white;
                                    trailWidth = 2.8f;
                                    trailLength = 8;
                                    hitEffect = despawnEffect = Fx.blastExplosion;
                                    shootEffect = Fx.shootTitan;
                                    smokeEffect = Fx.shootSmokeTitan;
                                    splashDamageRadius = 20f;
                                    splashDamage = 50f;

                                    trailEffect = Fx.hitSquaresColor;
                                    trailRotation = true;
                                    trailInterval = 3f;

                                    fragBullets = 4;

                                    fragBullet = new BasicBulletType(5f, 35) {
                                        {
                                            sprite = "missile-large";
                                            width = 5f;
                                            height = 7f;
                                            lifetime = 15f;
                                            hitSize = 4f;
                                            pierceCap = 3;
                                            pierce = true;
                                            pierceBuilding = true;
                                            hitColor = backColor = trailColor = Color.valueOf("feb380");
                                            frontColor = Color.white;
                                            trailWidth = 1.7f;
                                            trailLength = 3;
                                            drag = 0.01f;
                                            despawnEffect = hitEffect = Fx.hitBulletColor;
                                        }
                                    };
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  vanquish-r
        // ============================================================
        vanquishR = new TankUnitType("vanquish-r") {
            {
                constructor = TankUnit::create;
                hitSize = 28f;
                treadPullOffset = 4;
                speed = 0.63f;
                health = 11000f;
                armor = 20f;
                itemCapacity = 0;
                crushDamage = 13f / 5f;
                floorMultiplier = 0.5f;
                drownTimeMultiplier = 1.25f;
                immunities.addAll(StatusEffects.burning, StatusEffects.melting);
                crushFragile = true;
                treadRects = new Rect[]{
                    new Rect(22f - 154f/2f, 16f - 154f/2f, 28f, 130f)};

                tankMoveVolume *= 1.25f;
                tankMoveSound = Sounds.tankMoveHeavy;

                weapons.add(new Weapon("vanquish-weapon") {
                        {
                            shootSound = Sounds.shootTank;
                            layerOffset = 0.0001f;
                            reload = 80f;
                            shootY = 71f / 4f;
                            shake = 5f;
                            recoil = 4f;
                            rotate = true;
                            rotateSpeed = 1f;
                            mirror = false;
                            x = 0f;
                            y = 0f;
                            shadow = 28f;
                            heatColor = Color.valueOf("f9350f");
                            cooldownTime = 80f;

                            bullet = new BasicBulletType(8f, 150f) {
                                {
                                    sprite = "missile-large";
                                    width = 9.5f;
                                    height = 18f;
                                    lifetime = 16f;
                                    hitSize = 6f;
                                    shootEffect = Fx.shootTitan;
                                    smokeEffect = Fx.shootSmokeTitan;
                                    pierceCap = 2;
                                    pierce = true;
                                    pierceBuilding = true;

                                    hitColor = backColor = trailColor = Color.valueOf("feb380");
                                    frontColor = Color.white;
                                    trailWidth = 3.1f;
                                    trailLength = 8;
                                    hitEffect = despawnEffect = Fx.blastExplosion;
                                    splashDamageRadius = 20f;
                                    splashDamage = 50f;
                                    maxRange = 190f;

                                    fragOnHit = false;
                                    pierceFragCap = 1;
                                    fragRandomSpread = 0f;
                                    fragSpread = 10f;
                                    fragBullets = 5;
                                    fragVelocityMin = 1f;
                                    despawnSound = Sounds.explosionDull;

                                    fragBullet = new BasicBulletType(8f, 35f) {
                                        {
                                            sprite = "missile-large";
                                            width = 8f;
                                            height = 16f;
                                            lifetime = 10f;
                                            hitSize = 4f;
                                            hitColor = backColor = trailColor = Color.valueOf("feb380");
                                            frontColor = Color.white;
                                            trailWidth = 2.8f;
                                            trailLength = 6;
                                            hitEffect = despawnEffect = Fx.blastExplosion;
                                            splashDamageRadius = 10f;
                                            splashDamage = 20f;
                                        }
                                    };
                                }
                            };
                        }
                    });

                // 两个副炮
                for (float fy : new float[]{
                        34f / 4f, -36f / 4f}) {
                    final float fyf = fy;
                    weapons.add(new Weapon("vanquish-point-weapon") {
                            {
                                reload = 22f;
                                x = 48f / 4f;
                                y = fyf;
                                shootY = 5.5f;
                                recoil = 2f;
                                rotate = true;
                                rotateSpeed = 2f;
                                shootSound = Sounds.shootStell;

                                bullet = new BasicBulletType(12f, 50f) {
                                    {
                                        sprite = "missile-large";
                                        width = 6.5f;
                                        height = 11f;
                                        shrinkY = 0f;
                                        shrinkX = 0.2f;
                                        lifetime = 15f;
                                        shootEffect = Fx.sparkShoot;
                                        smokeEffect = Fx.shootBigSmoke;
                                        hitColor = backColor = trailColor = Color.valueOf("feb380");
                                        frontColor = Color.white;
                                        trailWidth = 2.5f;
                                        trailLength = 5;
                                        hitEffect = Fx.blastExplosion;
                                        despawnEffect = Fx.hitBulletColor;
                                    }
                                };
                            }
                        });
                }
            }
        };

        // ============================================================
        //  conquer-r
        // ============================================================
        conquerR = new TankUnitType("conquer-r") {
            {
                constructor = TankUnit::create;
                hitSize = 46f;
                treadPullOffset = 1;
                speed = 0.48f;
                health = 22000f;
                armor = 26f;
                crushDamage = 25f / 5f;
                rotateSpeed = 0.8f;
                floorMultiplier = 0.3f;
                immunities.addAll(StatusEffects.burning, StatusEffects.melting);

                tankMoveVolume *= 1.5f;
                tankMoveSound = Sounds.tankMoveHeavy;
                crushFragile = true;

                float xo = 231f / 2f, yo = 231f / 2f;
                treadRects = new Rect[]{
                    new Rect(27f - xo, 152f - yo, 56f, 73f),
                    new Rect(24f - xo, 51f - 9f - yo, 29f, 17f),
                    new Rect(59f - xo, 18f - 9f - yo, 39f, 19f)
                };

                weapons.add(new Weapon("conquer-weapon") {
                        {
                            shootSound = Sounds.shootConquer;
                            layerOffset = 0.1f;
                            reload = 100f;
                            shootY = 32.5f;
                            shake = 5f;
                            recoil = 5f;
                            rotate = true;
                            rotateSpeed = 0.6f;
                            mirror = false;
                            x = 0f;
                            y = -2f;
                            shadow = 50f;
                            heatColor = Color.valueOf("f9350f");
                            shootWarmupSpeed = 0.06f;
                            cooldownTime = 110f;
                            minWarmup = 0.9f;

                            bullet = new BasicBulletType(8f, 360f) {
                                {
                                    sprite = "missile-large";
                                    width = 12f;
                                    height = 20f;
                                    lifetime = 35f;
                                    hitSize = 6f;

                                    smokeEffect = Fx.shootSmokeTitan;
                                    pierceCap = 3;
                                    pierce = true;
                                    pierceBuilding = true;
                                    hitColor = backColor = trailColor = Color.valueOf("feb380");
                                    frontColor = Color.white;
                                    trailWidth = 4f;
                                    trailLength = 9;
                                    hitEffect = despawnEffect = Fx.massiveExplosion;

                                    shootEffect = new ExplosionEffect() {
                                        {
                                            lifetime = 40f;
                                            waveStroke = 4f;
                                            waveColor = sparkColor = trailColor;
                                            waveRad = 15f;
                                            smokeSize = 5f;
                                            smokes = 8;
                                            smokeSizeBase = 0f;
                                            smokeColor = trailColor;
                                            sparks = 8;
                                            sparkRad = 40f;
                                            sparkLen = 4f;
                                            sparkStroke = 3f;
                                        }
                                    };

                                    // 6 对子子弹
                                    int count = 6;
                                    for (int j = 0; j < count; j++) {
                                        int s = j;
                                        for (int i : Mathf.signs) {
                                            float fin = 0.05f + (j + 1) / (float) count;
                                            float spd = speed;
                                            float life = lifetime / Mathf.lerp(fin, 1f, 0.5f);
                                            boolean show = j == 0 && i > 0;
                                            spawnBullets.add(new BasicBulletType(spd * fin, 60) {
                                                    {
                                                        drag = 0.002f;
                                                        width = 12f;
                                                        height = 11f;
                                                        lifetime = life + 5f;
                                                        weaveRandom = false;
                                                        hitSize = 5f;
                                                        pierceCap = 2;
                                                        pierce = true;
                                                        showStats = show;
                                                        pierceBuilding = true;
                                                        hitColor = backColor = trailColor = Color.valueOf("feb380");
                                                        frontColor = Color.white;
                                                        trailWidth = 2.5f;
                                                        trailLength = 7;
                                                        weaveScale = (3f + s / 2f) / 1.2f;
                                                        weaveMag = i * (4f - fin * 2f);

                                                        splashDamage = 65f;
                                                        splashDamageRadius = 30f;
                                                        despawnEffect = Fx.blastExplosion;
                                                    }
                                                });
                                        }
                                    }
                                }
                            };
                        }
                    });
            }
        };
    }
    private static void loadErekirAir() {
        // ============================================================
        //  elude-r  (悬浮单位)
        // ============================================================
        eludeR = new UnitType("elude-r") {
            {
                constructor = ElevationMoveUnit::create;
                hovering = true;
                canDrown = false;
                shadowElevation = 0.1f;

                drag = 0.07f;
                speed = 1.8f;
                rotateSpeed = 5f;
                accel = 0.09f;
                health = 600f;
                armor = 1f;
                hitSize = 11f;
                engineOffset = 7f;
                engineSize = 2f;
                itemCapacity = 0;
                useEngineElevation = false;

                moveSound = Sounds.loopExtract;
                moveSoundVolume = 0.25f;
                moveSoundPitchMin = 0.7f;
                moveSoundPitchMax = 1.5f;

                abilities.add(new MoveEffectAbility(0f, -7f, Pal.sapBulletBack, Fx.missileTrailShort, 4f) {
                        {
                            teamColor = true;
                        }
                    });

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

                weapons.add(new Weapon("elude-weapon") {
                        {
                            shootSound = Sounds.shootElude;
                            y = -2f;
                            x = 4f;
                            top = true;
                            mirror = true;
                            reload = 40f;
                            baseRotation = -35f;
                            shootCone = 360f;

                            shoot = new ShootSpread(2, 11f);

                            bullet = new BasicBulletType(5f, 16) {
                                {
                                    homingPower = 0.19f;
                                    homingDelay = 4f;
                                    width = 7f;
                                    height = 12f;
                                    lifetime = 30f;
                                    shootEffect = Fx.sparkShoot;
                                    smokeEffect = Fx.shootBigSmoke;
                                    hitColor = backColor = trailColor = Pal.suppress;
                                    frontColor = Color.white;
                                    trailWidth = 1.5f;
                                    trailLength = 5;
                                    hitEffect = despawnEffect = Fx.hitBulletColor;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  avert-r  (战机)
        // ============================================================
        avertR = new UnitType("avert-r") {
            {
                lowAltitude = false;
                flying = true;
                drag = 0.08f;
                speed = 2f;
                rotateSpeed = 8f;
                accel = 0.09f;
                health = 1100f;
                armor = 3f;
                hitSize = 12f;
                engineSize = 0f;
                fogRadius = 25f;
                itemCapacity = 0;

                setEnginesMirror(
                    new UnitEngine(35f / 4f, -38f / 4f, 3f, 315f),
                    new UnitEngine(39f / 4f, -16f / 4f, 3f, 315f)
                );

                weapons.add(new Weapon("avert-weapon") {
                        {
                            shootSound = Sounds.shootAvert;
                            reload = 35f;
                            x = 0f;
                            y = 6.5f;
                            shootY = 5f;
                            recoil = 1f;
                            top = false;
                            layerOffset = -0.01f;
                            rotate = false;
                            mirror = false;
                            shoot = new ShootHelix();

                            bullet = new BasicBulletType(5f, 22f / 0.75f) {
                                {
                                    width = 7f;
                                    height = 12f;
                                    lifetime = 18f;
                                    buildingDamageMultiplier = 0.599999f;
                                    blockArmorMultiplier = 0.5f;
                                    shootEffect = Fx.sparkShoot;
                                    smokeEffect = Fx.shootBigSmoke;
                                    hitColor = backColor = trailColor = Pal.suppress;
                                    frontColor = Color.white;
                                    trailWidth = 1.5f;
                                    trailLength = 5;
                                    hitEffect = despawnEffect = new MultiEffect(Fx.hitSquaresColor, Fx.squareWaveEffect);

                                    fragOnDespawn = false;
                                    fragBullets = 2;

                                    fragBullet = new BasicBulletType(3f, 10) {
                                        {
                                            width = 5f;
                                            height = 8f;
                                            lifetime = 14f;
                                            fragVelocityMax = 1f;
                                            fragVelocityMin = 0.7f;
                                            hitColor = backColor = trailColor = Pal.suppress;
                                            frontColor = Color.white;
                                            trailWidth = 1.2f;
                                            trailLength = 4;
                                            hitEffect = despawnEffect = Fx.hitBulletColor;
                                        }
                                    };
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  obviate-r  (战机)
        // ============================================================
        obviateR = new UnitType("obviate-r") {
            {
                flying = true;
                drag = 0.08f;
                speed = 1.8f;
                rotateSpeed = 2.5f;
                accel = 0.09f;
                health = 2300f;
                armor = 6f;
                hitSize = 25f;
                engineSize = 4.3f;
                engineOffset = 54f / 4f;
                fogRadius = 25f;
                itemCapacity = 0;
                lowAltitude = true;

                setEnginesMirror(
                    new UnitEngine(38f / 4f, -46f / 4f, 3.1f, 315f)
                );

                parts.add(new RegionPart("-blade") {
                        {
                            moveRot = -10f;
                            moveX = -1f;
                            moves.add(new PartMove(PartProgress.reload, 2f, 1f, -5f));
                            progress = PartProgress.warmup;
                            mirror = true;

                            children.add(new RegionPart("-side") {
                                    {
                                        moveX = 2f;
                                        moveY = -2f;
                                        progress = PartProgress.warmup;
                                        under = true;
                                        mirror = true;
                                        moves.add(new PartMove(PartProgress.reload, -2f, 2f, 0f));
                                    }
                                });
                        }
                    });

                weapons.add(new Weapon() {
                        {
                            shootSound = Sounds.explosionObviate;
                            x = 0f;
                            y = -2f;
                            shootY = 0f;
                            reload = 140f;
                            mirror = false;
                            minWarmup = 0.95f;
                            shake = 3f;
                            cooldownTime = reload - 10f;

                            bullet = new BasicBulletType() {
                                {
                                    shoot = new ShootHelix() {
                                        {
                                            mag = 1f;
                                            scl = 5f;
                                        }
                                    };

                                    shootEffect = new MultiEffect(Fx.shootTitan, new WaveEffect() {
                                            {
                                                colorTo = Pal.sapBulletBack;
                                                sizeTo = 26f;
                                                lifetime = 14f;
                                                strokeFrom = 4f;
                                            }
                                        });
                                    smokeEffect = Fx.shootSmokeTitan;
                                    hitColor = Pal.sapBullet;
                                    despawnSound = Sounds.explosionArtilleryShock;

                                    sprite = "large-orb";
                                    trailEffect = Fx.missileTrail;
                                    trailInterval = 3f;
                                    trailParam = 4f;
                                    speed = 3f;
                                    damage = 75f;
                                    lifetime = 60f;
                                    width = height = 15f;
                                    backColor = Pal.sapBulletBack;
                                    frontColor = Pal.sapBullet;
                                    shrinkX = shrinkY = 0f;
                                    trailColor = Pal.sapBulletBack;
                                    trailLength = 12;
                                    trailWidth = 2.2f;

                                    despawnEffect = hitEffect = new ExplosionEffect() {
                                        {
                                            waveColor = Pal.sapBullet;
                                            smokeColor = Color.gray;
                                            sparkColor = Pal.sap;
                                            waveStroke = 4f;
                                            waveRad = 40f;
                                        }
                                    };

                                    intervalBullet = new LightningBulletType() {
                                        {
                                            damage = 16f;
                                            collidesAir = false;
                                            ammoMultiplier = 1f;
                                            lightningColor = Pal.sapBullet;
                                            lightningLength = 3;
                                            lightningLengthRand = 6;
                                            buildingDamageMultiplier = 0.25f;

                                            lightningType = new BulletType(0.0001f, 0f) {
                                                {
                                                    lifetime = Fx.lightning.lifetime;
                                                    hitEffect = Fx.hitLancer;
                                                    despawnEffect = Fx.none;
                                                    status = StatusEffects.shocked;
                                                    statusDuration = 10f;
                                                    hittable = false;
                                                    lightColor = Color.white;
                                                    buildingDamageMultiplier = 0.25f;
                                                }
                                            };
                                        }
                                    };

                                    bulletInterval = 4f;

                                    lightningColor = Pal.sapBullet;
                                    lightningDamage = 17f;
                                    lightning = 8;
                                    lightningLength = 2;
                                    lightningLengthRand = 8;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  quell-r  (战机 - 导弹)
        // ============================================================
        quellR = new UnitType("quell-r") {
            {
                constructor = PayloadUnit::create;
                lowAltitude = false;
                flying = true;
                drag = 0.06f;
                speed = 1.1f;
                rotateSpeed = 3.2f;
                accel = 0.1f;
                health = 6000f;
                armor = 4f;
                hitSize = 36f;
                payloadCapacity = Mathf.sqr(4f) * tilePayload;
                targetAir = false;
                envDisabled = 0;

                engineSize = 4.8f;
                engineOffset = 61f / 4f;
                range = 4.3f * 60f * 1.4f;

                loopSoundVolume = 0.85f;
                loopSound = Sounds.loopHover;

                abilities.add(new SuppressionFieldAbility() {
                        {
                            reload = 60f * 8f;
                            orbRadius = 5.3f;
                            y = 1f;
                        }
                    });

                weapons.add(new Weapon("quell-weapon") {
                        {
                            shootSound = Sounds.shootMissileSmall;
                            x = 51f / 4f;
                            y = 5f / 4f;
                            rotate = true;
                            rotateSpeed = 2f;
                            reload = 55f;
                            layerOffset = -0.001f;
                            recoil = 1f;
                            rotationLimit = 60f;

                            bullet = new BasicBulletType(4.3f, 70f, "missile-large") {
                                {
                                    shootEffect = Fx.shootBig;
                                    smokeEffect = Fx.shootBigSmoke2;
                                    shake = 1f;
                                    lifetime = 60f * 0.496f;
                                    rangeOverride = 361.2f;
                                    followAimSpeed = 5f;

                                    width = 12f;
                                    height = 22f;
                                    hitSize = 7f;
                                    hitColor = backColor = trailColor = Pal.sapBulletBack;
                                    trailWidth = 3f;
                                    trailLength = 12;
                                    hitEffect = despawnEffect = Fx.hitBulletColor;

                                    keepVelocity = false;
                                    collidesGround = true;
                                    collidesAir = false;

                                    fragRandomSpread = 0f;
                                    fragBullets = 1;
                                    fragVelocityMin = 1f;
                                    fragOffsetMax = 1f;

                                    fragBullet = new BulletType() {
                                        {
                                            speed = 0f;
                                            keepVelocity = false;
                                            collidesAir = false;

                                            spawnUnit = new MissileUnitType("quell-missile") {
                                                {
                                                    targetAir = false;
                                                    speed = 4.3f;
                                                    maxRange = 6f;
                                                    lifetime = 60f * (1.4f - 0.496f);
                                                    outlineColor = Pal.darkOutline;
                                                    engineColor = trailColor = Pal.sapBulletBack;
                                                    engineLayer = Layer.effect;
                                                    health = 45;
                                                    loopSoundVolume = 0.1f;

                                                    weapons.add(new Weapon() {
                                                            {
                                                                shootSound = Sounds.none;
                                                                shootCone = 360f;
                                                                mirror = false;
                                                                reload = 1f;
                                                                shootOnDeath = true;
                                                                shootOnDeathEffect = Fx.massiveExplosion;
                                                                bullet = new ExplosionBulletType(110f, 25f) {
                                                                    {
                                                                        shootEffect = new WrapEffect(Fx.shootQuellPulse, Pal.suppress);
                                                                        collidesAir = false;
                                                                    }
                                                                };
                                                            }
                                                        });
                                                }
                                            };
                                        }
                                    };
                                }
                            };
                        }
                    });

                setEnginesMirror(
                    new UnitEngine(62f / 4f, -60f / 4f, 3.9f, 315f),
                    new UnitEngine(72f / 4f, -29f / 4f, 3f, 315f)
                );
            }
        };

        // ============================================================
        //  disrupt-r  (战机 - 压制)
        // ============================================================
        disruptR = new UnitType("disrupt-r") {
            {
                constructor = PayloadUnit::create;
                lowAltitude = false;
                flying = true;
                drag = 0.07f;
                speed = 1f;
                rotateSpeed = 2f;
                accel = 0.1f;
                health = 12000f;
                armor = 9f;
                hitSize = 46f;
                payloadCapacity = Mathf.sqr(6f) * tilePayload;
                targetAir = false;
                envDisabled = 0;

                engineSize = 6f;
                engineOffset = 25.25f;

                loopSound = Sounds.loopHover;

                float orbRad = 5f, partRad = 3f;
                int parts = 10;

                abilities.add(new SuppressionFieldAbility() {
                        {
                            reload = 60f * 15f;
                            range = 320f;
                            orbRadius = orbRad;
                            particleSize = partRad;
                            y = 10f;
                            particles = parts;
                        }
                    });

                for (int i : Mathf.signs) {
                    final int fi = i;
                    abilities.add(new SuppressionFieldAbility() {
                            {
                                orbRadius = orbRad;
                                particleSize = partRad;
                                y = -32f / 4f;
                                x = 43f * fi / 4f;
                                particles = parts;
                                active = false;
                            }
                        });
                }

                weapons.add(new Weapon("disrupt-weapon") {
                        {
                            shootSound = Sounds.shootMissileLarge;
                            shootSoundVolume = 0.6f;
                            x = 78f / 4f;
                            y = -10f / 4f;
                            mirror = true;
                            rotate = true;
                            rotateSpeed = 0.4f;
                            reload = 70f;
                            layerOffset = -20f;
                            recoil = 1f;
                            rotationLimit = 22f;
                            minWarmup = 0.95f;
                            shootWarmupSpeed = 0.1f;
                            shootY = 2f;
                            shootCone = 40f;

                            shoot.shots = 3;
                            shoot.shotDelay = 5f;
                            inaccuracy = 28f;

                            parts.add(new RegionPart("-blade") {
                                    {
                                        heatProgress = PartProgress.warmup;
                                        progress = PartProgress.warmup.blend(PartProgress.reload, 0.15f);
                                        heatColor = Color.valueOf("9c50ff");
                                        x = 5f / 4f;
                                        y = 0f;
                                        moveRot = -33f;
                                        moveY = -1f;
                                        moveX = -1f;
                                        under = true;
                                        mirror = true;
                                    }
                                });

                            bullet = new BulletType() {
                                {
                                    shootEffect = Fx.sparkShoot;
                                    smokeEffect = Fx.shootSmokeTitan;
                                    hitColor = Pal.suppress;
                                    shake = 1f;
                                    speed = 0f;
                                    keepVelocity = false;
                                    collidesAir = false;

                                    spawnUnit = new MissileUnitType("disrupt-missile") {
                                        {
                                            targetAir = false;
                                            speed = 4.6f;
                                            maxRange = 5f;
                                            outlineColor = Pal.darkOutline;
                                            health = 70;
                                            homingDelay = 10f;
                                            lowAltitude = true;
                                            engineSize = 3f;
                                            engineColor = trailColor = Pal.sapBulletBack;
                                            engineLayer = Layer.effect;
                                            deathExplosionEffect = Fx.none;
                                            loopSoundVolume = 0.1f;

                                            parts.add(new ShapePart() {
                                                    {
                                                        layer = Layer.effect;
                                                        circle = true;
                                                        y = -0.25f;
                                                        radius = 1.5f;
                                                        color = Pal.suppress;
                                                        colorTo = Color.white;
                                                        progress = PartProgress.life.curve(Interp.pow5In);
                                                    }
                                                });

                                            parts.add(new RegionPart("-fin") {
                                                    {
                                                        mirror = true;
                                                        progress = PartProgress.life.mul(3f).curve(Interp.pow5In);
                                                        moveRot = 32f;
                                                        rotation = -6f;
                                                        moveY = 1.5f;
                                                        x = 3f / 4f;
                                                        y = -6f / 4f;
                                                    }
                                                });

                                            weapons.add(new Weapon() {
                                                    {
                                                        shootCone = 360f;
                                                        mirror = false;
                                                        reload = 1f;
                                                        shootOnDeath = true;
                                                        shootOnDeathEffect = Fx.massiveExplosion;
                                                        bullet = new ExplosionBulletType(140f, 25f) {
                                                            {
                                                                collidesAir = false;
                                                                suppressionRange = 140f;
                                                                shootEffect = new ExplosionEffect() {
                                                                    {
                                                                        lifetime = 50f;
                                                                        waveStroke = 5f;
                                                                        waveLife = 12f;
                                                                        waveColor = Pal.sap.cpy().mul(1.8f);
                                                                        sparkColor = smokeColor = Pal.suppress;
                                                                        waveRad = 40f;
                                                                        smokeSize = 4f;
                                                                        smokes = 7;
                                                                        smokeSizeBase = 0f;
                                                                        sparks = 10;
                                                                        sparkRad = 40f;
                                                                        sparkLen = 6f;
                                                                        sparkStroke = 2f;
                                                                    }
                                                                };
                                                            }
                                                        };
                                                    }
                                                });
                                        }
                                    };
                                }
                            };
                        }
                    });

                setEnginesMirror(
                    new UnitEngine(95f / 4f, -56f / 4f, 5f, 330f),
                    new UnitEngine(89f / 4f, -95f / 4f, 4f, 315f)
                );
            }
        };
    }
    private static void loadErekirSpiders() {
        // ============================================================
        //  merui-r
        // ============================================================
        meruiR = new GlowErekirLegsUnitType("merui-r") {
            {
                constructor = LegsUnit::create;
                speed = 0.72f;
                drag = 0.11f;
                hitSize = 9f;
                rotateSpeed = 3f;
                health = 680f;
                armor = 4f;
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

                weapons.add(new Weapon("merui-weapon") {
                        {
                            shootSound = Sounds.shootMerui;
                            mirror = false;
                            showStatSprite = false;
                            x = 0f;
                            y = 1f;
                            shootY = 4f;
                            reload = 63f;
                            cooldownTime = 42f;
                            heatColor = Pal.turretHeat;

                            bullet = new ArtilleryBulletType(3f, 40) {
                                {
                                    shootEffect = new MultiEffect(Fx.shootSmallColor, new Effect(9, e -> {
                                                color(Color.white, e.color, e.fin());
                                                stroke(0.7f + e.fout());
                                                Lines.square(e.x, e.y, e.fin() * 5f, e.rotation + 45f);
                                                Drawf.light(e.x, e.y, 23f, e.color, e.fout() * 0.7f);
                                            }));

                                    collidesTiles = true;
                                    backColor = hitColor = Pal.techBlue;
                                    frontColor = Color.white;

                                    knockback = 0.8f;
                                    lifetime = 46f;
                                    width = height = 9f;
                                    splashDamageRadius = 19f;
                                    splashDamage = 30f;

                                    trailLength = 27;
                                    trailWidth = 2.5f;
                                    trailEffect = Fx.none;
                                    trailColor = backColor;
                                    trailInterp = Interp.slope;

                                    shrinkX = 0.6f;
                                    shrinkY = 0.2f;

                                    hitEffect = despawnEffect = new MultiEffect(
                                        Fx.hitSquaresColor,
                                        new WaveEffect() {
                                            {
                                                colorFrom = colorTo = Pal.techBlue;
                                                sizeTo = splashDamageRadius + 2f;
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

        // ============================================================
        //  cleroi-r
        // ============================================================
        cleroiR = new GlowErekirLegsUnitType("cleroi-r") {
            {
                constructor = LegsUnit::create;
                speed = 0.6f;
                drag = 0.1f;
                hitSize = 14f;
                rotateSpeed = 3f;
                health = 1100f;
                armor = 5f;
                stepShake = 0f;
                stepSound = Sounds.walkerStepSmall;

                legCount = 4;
                legLength = 14f;
                lockLegBase = true;
                legContinuousMove = true;
                legExtension = -3f;
                legBaseOffset = 5f;
                legMaxLength = 1.1f;
                legMinLength = 0.2f;
                legLengthScl = 0.95f;
                legForwardScl = 0.7f;

                legMoveSpace = 1f;
                hovering = true;
                shadowElevation = 0.2f;
                groundLayer = Layer.legUnit - 1f;

                // 5 个 spine 装饰
                for (int i = 0; i < 5; i++) {
                    int fi = i;
                    parts.add(new RegionPart("-spine") {
                            {
                                y = 21f / 4f - 45f / 4f * fi / 4f;
                                moveX = 21f / 4f + Mathf.slope(fi / 4f) * 1.25f;
                                moveRot = 10f - fi * 14f;
                                float fin = fi / 4f;
                                progress = PartProgress.reload.inv().mul(1.3f).add(0.1f).sustain(fin * 0.34f, 0.14f, 0.14f);
                                layerOffset = -0.001f;
                                mirror = true;
                            }
                        });
                }

                weapons.add(new Weapon("cleroi-weapon") {
                        {
                            shootSound = Sounds.shootCleroi;
                            x = 14f / 4f;
                            y = 33f / 4f;
                            reload = 33f;
                            layerOffset = -0.002f;
                            alternate = false;
                            heatColor = Color.red;
                            cooldownTime = 25f;
                            smoothReloadSpeed = 0.15f;
                            recoil = 2f;

                            bullet = new BasicBulletType(3.5f, 30) {
                                {
                                    backColor = trailColor = hitColor = Pal.techBlue;
                                    frontColor = Color.white;
                                    width = 7.5f;
                                    height = 10f;
                                    lifetime = 40f;
                                    trailWidth = 2f;
                                    trailLength = 4;
                                    shake = 1f;

                                    trailEffect = Fx.missileTrail;
                                    trailParam = 1.8f;
                                    trailInterval = 6f;

                                    splashDamageRadius = 30f;
                                    splashDamage = 43f;

                                    despawnSound = Sounds.explosionCleroi;

                                    hitEffect = despawnEffect = new MultiEffect(
                                        Fx.hitBulletColor,
                                        new WaveEffect() {
                                            {
                                                colorFrom = colorTo = Pal.techBlue;
                                                sizeTo = splashDamageRadius + 3f;
                                                lifetime = 9f;
                                                strokeFrom = 3f;
                                            }
                                        }
                                    );

                                    shootEffect = new MultiEffect(Fx.shootBigColor, new Effect(9, e -> {
                                                color(Color.white, e.color, e.fin());
                                                stroke(0.7f + e.fout());
                                                Lines.square(e.x, e.y, e.fin() * 5f, e.rotation + 45f);
                                                Drawf.light(e.x, e.y, 23f, e.color, e.fout() * 0.7f);
                                            }));

                                    smokeEffect = Fx.shootSmokeSquare;
                                    ammoMultiplier = 2;
                                }
                            };
                        }
                    });

                weapons.add(new PointDefenseWeapon("cleroi-point-defense") {
                        {
                            x = 16f / 4f;
                            y = -20f / 4f;
                            reload = 9f;
                            targetInterval = 9f;
                            targetSwitchInterval = 12f;
                            recoil = 0.5f;

                            bullet = new BulletType() {
                                {
                                    shootSound = Sounds.shootLaser;
                                    shootEffect = Fx.sparkShoot;
                                    hitEffect = Fx.pointHit;
                                    maxRange = 100f;
                                    damage = 38f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  anthicus-r
        // ============================================================
        anthicusR = new GlowErekirLegsUnitType("anthicus-r") {
            {
                constructor = LegsUnit::create;
                speed = 0.65f;
                drag = 0.1f;
                hitSize = 21f;
                rotateSpeed = 3f;
                health = 2700f;
                armor = 7f;
                fogRadius = 40f;
                stepShake = 0f;
                stepSound = Sounds.walkerStepSmall;
                stepSoundPitch = 0.78f;

                legCount = 6;
                legLength = 18f;
                legGroupSize = 3;
                lockLegBase = true;
                legContinuousMove = true;
                legExtension = -3f;
                legBaseOffset = 7f;
                legMaxLength = 1.1f;
                legMinLength = 0.2f;
                legLengthScl = 0.95f;
                legForwardScl = 0.9f;

                legMoveSpace = 1f;
                hovering = true;
                shadowElevation = 0.2f;
                groundLayer = Layer.legUnit - 1f;

                // 3 对 blade 装饰
                for (int j = 0; j < 3; j++) {
                    int i = j;
                    parts.add(new RegionPart("-blade") {
                            {
                                layerOffset = -0.01f;
                                heatLayerOffset = 0.005f;
                                x = 2f;
                                moveX = 6f + i * 1.9f;
                                moveY = 8f + -4f * i;
                                moveRot = 40f - i * 25f;
                                mirror = true;
                                progress = PartProgress.warmup.delay(i * 0.2f);
                                heatProgress = p -> Mathf.absin(Time.time + i * 14f, 7f, 1f);
                                heatColor = Pal.techBlue;
                            }
                        });
                }

                weapons.add(new Weapon("anthicus-weapon") {
                        {
                            shootSound = Sounds.shootMissileLarge;
                            shootSoundVolume = 0.5f;
                            x = 29f / 4f;
                            y = -11f / 4f;
                            shootY = 1.5f;
                            showStatSprite = false;
                            reload = 130f;
                            layerOffset = 0.01f;
                            heatColor = Color.red;
                            cooldownTime = 60f;
                            smoothReloadSpeed = 0.15f;
                            shootWarmupSpeed = 0.05f;
                            minWarmup = 0.9f;
                            rotationLimit = 70f;
                            rotateSpeed = 2f;
                            inaccuracy = 20f;
                            shootStatus = StatusEffects.slow;
                            alwaysShootWhenMoving = true;
                            rotate = true;

                            shoot = new ShootPattern() {
                                {
                                    shots = 2;
                                    shotDelay = 6f;
                                }
                            };

                            // 两个 blade 装饰
                            parts.add(new RegionPart("-blade") {
                                    {
                                        mirror = true;
                                        moveRot = -25f;
                                        under = true;
                                        moves.add(new PartMove(PartProgress.reload, 1f, 0f, 0f));
                                        heatColor = Color.red;
                                        cooldownTime = 60f;
                                    }
                                });

                            parts.add(new RegionPart("-blade") {
                                    {
                                        mirror = true;
                                        moveRot = -50f;
                                        moveY = -2f;
                                        moves.add(new PartMove(PartProgress.reload.shorten(0.5f), 1f, 0f, -15f));
                                        under = true;
                                        heatColor = Color.red;
                                        cooldownTime = 60f;
                                    }
                                });

                            bullet = new BulletType() {
                                {
                                    shootEffect = new MultiEffect(
                                        Fx.shootBigColor,
                                        new Effect(9, e -> {
                                                color(Color.white, e.color, e.fin());
                                                stroke(0.7f + e.fout());
                                                Lines.square(e.x, e.y, e.fin() * 5f, e.rotation + 45f);
                                                Drawf.light(e.x, e.y, 23f, e.color, e.fout() * 0.7f);
                                            }),
                                        new WaveEffect() {
                                            {
                                                colorFrom = colorTo = Pal.techBlue;
                                                sizeTo = 15f;
                                                lifetime = 12f;
                                                strokeFrom = 3f;
                                            }
                                        }
                                    );

                                    smokeEffect = Fx.shootBigSmoke2;
                                    shake = 2f;
                                    speed = 0f;
                                    keepVelocity = false;
                                    inaccuracy = 2f;

                                    spawnUnit = new MissileUnitType("anthicus-missile") {
                                        {
                                            trailColor = engineColor = Pal.techBlue;
                                            engineSize = 1.75f;
                                            engineLayer = Layer.effect;
                                            speed = 3.35f;
                                            maxRange = 6f;
                                            lifetime = 60f * 1.66f;
                                            outlineColor = Pal.darkOutline;
                                            health = 55f;
                                            lowAltitude = true;

                                            parts.add(new FlarePart() {
                                                    {
                                                        progress = PartProgress.life.slope().curve(Interp.pow2In);
                                                        radius = 0f;
                                                        radiusTo = 35f;
                                                        stroke = 3f;
                                                        rotation = 45f;
                                                        y = -5f;
                                                        followRotation = true;
                                                    }
                                                });

                                            weapons.add(new Weapon() {
                                                    {
                                                        shootSound = Sounds.none;
                                                        shootCone = 360f;
                                                        mirror = false;
                                                        reload = 1f;
                                                        shootOnDeath = true;
                                                        shootOnDeathEffect = Fx.massiveExplosion;

                                                        bullet = new ExplosionBulletType(140f, 25f) {
                                                            {
                                                                shootEffect = new MultiEffect(
                                                                    new WrapEffect(Fx.dynamicSpikes, Pal.techBlue, 24f),
                                                                    new WaveEffect() {
                                                                        {
                                                                            colorFrom = colorTo = Pal.techBlue;
                                                                            sizeTo = 40f;
                                                                            lifetime = 12f;
                                                                            strokeFrom = 4f;
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
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  tecta-r
        // ============================================================
        tectaR = new GlowErekirLegsUnitType("tecta-r") {
            {
                constructor = LegsUnit::create;
                drag = 0.1f;
                speed = 0.6f;
                hitSize = 30f;
                health = 6500f;
                armor = 5f;

                lockLegBase = true;
                legContinuousMove = true;
                legGroupSize = 3;
                legStraightness = 0.4f;
                baseLegStraightness = 0.5f;
                legMaxLength = 1.3f;

                stepSound = Sounds.walkerStep;
                stepSoundVolume = 1f;
                stepSoundPitch = 1f;

                abilities.add(new ShieldArcAbility() {
                        {
                            region = "tecta-shield";
                            radius = 45f;
                            angle = 82f;
                            regen = 45f / 60f;
                            cooldown = 60f * 8f;
                            max = 2500f;
                            y = -20f;
                            width = 8f;
                            whenShooting = false;
                            chanceDeflect = 1f;
                        }
                    });

                rotateSpeed = 2.1f;

                legCount = 6;
                legLength = 15f;
                legForwardScl = 0.45f;
                legMoveSpace = 1.4f;
                rippleScale = 2f;
                stepShake = 0.5f;
                legExtension = -5f;
                legBaseOffset = 5f;

                legSplashDamage = 32;
                legSplashRange = 30;
                drownTimeMultiplier = 0.5f;

                hovering = true;
                shadowElevation = 0.4f;
                groundLayer = Layer.legUnit;

                weapons.add(new Weapon("tecta-weapon") {
                        {
                            shootSound = Sounds.shootMalign;
                            mirror = true;
                            top = false;
                            x = 62f / 4f;
                            y = 1f;
                            shootY = 47f / 4f;
                            recoil = 3f;
                            reload = 40f;
                            shake = 3f;
                            cooldownTime = 40f;

                            shoot.shots = 3;
                            inaccuracy = 3f;
                            velocityRnd = 0.33f;
                            heatColor = Color.red;

                            bullet = new MissileBulletType(4.2f, 51) {
                                {
                                    homingPower = 0.2f;
                                    weaveMag = 4;
                                    weaveScale = 4;
                                    lifetime = 55f;
                                    shootEffect = Fx.shootBig2;
                                    smokeEffect = Fx.shootSmokeTitan;
                                    splashDamage = 60f;
                                    splashDamageRadius = 30f;
                                    frontColor = Color.white;
                                    hitSound = Sounds.none;
                                    width = height = 10f;

                                    lightColor = trailColor = backColor = Pal.techBlue;
                                    lightRadius = 40f;
                                    lightOpacity = 0.7f;

                                    trailWidth = 2.8f;
                                    trailLength = 20;
                                    trailChance = -1f;
                                    despawnSound = Sounds.explosionDull;

                                    despawnEffect = Fx.none;
                                    hitEffect = new ExplosionEffect() {
                                        {
                                            lifetime = 20f;
                                            waveStroke = 2f;
                                            waveColor = sparkColor = trailColor;
                                            waveRad = 12f;
                                            smokeSize = 0f;
                                            smokeSizeBase = 0f;
                                            sparks = 10;
                                            sparkRad = 35f;
                                            sparkLen = 4f;
                                            sparkStroke = 1.5f;
                                        }
                                    };
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  collaris-r
        // ============================================================
        collarisR = new GlowErekirLegsUnitType("collaris-r") {
            {
                constructor = LegsUnit::create;
                drag = 0.1f;
                speed = 1.1f;
                hitSize = 44f;
                health = 18000f;
                armor = 9f;
                rotateSpeed = 1.6f;
                lockLegBase = true;
                legContinuousMove = true;
                legStraightness = 0.6f;
                baseLegStraightness = 0.5f;

                stepSound = Sounds.walkerStep;
                stepSoundVolume = 1.1f;
                stepSoundPitch = 0.9f;

                legCount = 8;
                legLength = 30f;
                legForwardScl = 2.1f;
                legMoveSpace = 1.05f;
                rippleScale = 1.2f;
                stepShake = 0.5f;
                legGroupSize = 2;
                legExtension = -6f;
                legBaseOffset = 19f;
                legStraightLength = 0.9f;
                legMaxLength = 1.2f;

                legSplashDamage = 32;
                legSplashRange = 32;
                drownTimeMultiplier = 0.5f;

                hovering = true;
                shadowElevation = 0.4f;
                groundLayer = Layer.legUnit;

                targetAir = false;
                alwaysShootWhenMoving = true;

                weapons.add(new Weapon("collaris-weapon") {
                        {
                            shootSound = Sounds.shootCollaris;
                            mirror = true;
                            rotationLimit = 30f;
                            rotateSpeed = 0.4f;
                            rotate = true;

                            x = 48f / 4f;
                            y = -28f / 4f;
                            shootY = 64f / 4f;
                            recoil = 4f;
                            reload = 130f;
                            cooldownTime = reload * 1.2f;
                            shake = 7f;
                            layerOffset = 0.02f;
                            shadow = 10f;

                            shootStatus = StatusEffects.slow;
                            shootStatusDuration = reload + 170f;

                            shoot.shots = 1;
                            heatColor = Color.red;

                            for (int i = 0; i < 5; i++) {
                                int fi = i;
                                parts.add(new RegionPart("-blade") {
                                        {
                                            under = true;
                                            layerOffset = -0.001f;
                                            heatColor = Pal.techBlue;
                                            heatProgress = PartProgress.heat.add(0.2f).min(PartProgress.warmup);
                                            progress = PartProgress.warmup.blend(PartProgress.reload, 0.1f);
                                            x = 13.5f / 4f;
                                            y = 10f / 4f - fi * 2f;
                                            moveY = 1f - fi * 1f;
                                            moveX = fi * 0.3f;
                                            moveRot = -45f - fi * 17f;

                                            moves.add(new PartMove(
                                                    PartProgress.reload.inv().mul(1.8f).inv().curve(fi / 5f, 0.2f),
                                                    0f, 0f, 36f));
                                        }
                                    });
                            }

                            bullet = new ArtilleryBulletType(5.5f, 260) {
                                {
                                    collidesTiles = collides = true;
                                    lifetime = 60f;
                                    shootEffect = Fx.shootBigColor;
                                    smokeEffect = Fx.shootSmokeSquareBig;
                                    frontColor = Color.white;
                                    trailEffect = new MultiEffect(Fx.artilleryTrail, Fx.artilleryTrailSmoke);
                                    hitSound = Sounds.none;
                                    width = 18f;
                                    height = 24f;
                                    rangeOverride = 385f;

                                    lightColor = trailColor = hitColor = backColor = Pal.techBlue;
                                    lightRadius = 40f;
                                    lightOpacity = 0.7f;

                                    trailWidth = 4.5f;
                                    trailLength = 19;
                                    trailChance = -1f;

                                    despawnEffect = Fx.none;
                                    despawnSound = Sounds.explosionDull;

                                    hitEffect = despawnEffect = new ExplosionEffect() {
                                        {
                                            lifetime = 50f;
                                            waveStroke = 5f;
                                            waveColor = sparkColor = trailColor;
                                            waveRad = 45f;
                                            smokeSize = 0f;
                                            smokeSizeBase = 0f;
                                            sparks = 10;
                                            sparkRad = 25f;
                                            sparkLen = 8f;
                                            sparkStroke = 3f;
                                        }
                                    };

                                    splashDamage = 120f;
                                    splashDamageRadius = 36f;

                                    fragBullets = 15;
                                    fragVelocityMin = 0.5f;
                                    fragRandomSpread = 130f;
                                    fragLifeMin = 0.3f;
                                    despawnShake = 5f;

                                    fragBullet = new BasicBulletType(5.5f, 37) {
                                        {
                                            pierceCap = 2;
                                            pierceBuilding = true;

                                            homingPower = 0.09f;
                                            homingRange = 150f;

                                            lifetime = 40f;
                                            shootEffect = Fx.shootBigColor;
                                            smokeEffect = Fx.shootSmokeSquareBig;
                                            frontColor = Color.white;
                                            hitSound = Sounds.none;
                                            width = 12f;
                                            height = 20f;

                                            lightColor = trailColor = hitColor = backColor = Pal.techBlue;
                                            lightRadius = 40f;
                                            lightOpacity = 0.7f;

                                            trailWidth = 2.2f;
                                            trailLength = 7;
                                            trailChance = -1f;

                                            collidesAir = false;

                                            despawnEffect = Fx.none;
                                            splashDamage = 35f;
                                            splashDamageRadius = 30f;

                                            hitEffect = despawnEffect = new MultiEffect(
                                                new ExplosionEffect() {
                                                    {
                                                        lifetime = 30f;
                                                        waveStroke = 2f;
                                                        waveColor = sparkColor = trailColor;
                                                        waveRad = 5f;
                                                        smokeSize = 0f;
                                                        smokeSizeBase = 0f;
                                                        sparks = 5;
                                                        sparkRad = 20f;
                                                        sparkLen = 6f;
                                                        sparkStroke = 2f;
                                                    }
                                                },
                                                Fx.blastExplosion
                                            );
                                        }
                                    };
                                }
                            };
                        }
                    });
            }
        };
    }
    private static void loadCargoAndAssembly() {
        // ============================================================
        //  manifold-r  (货运无人机)
        //  由 unit-cargo-loader 自动派出，负责埃里克尔的自动物流。
        //  玩家看不见、无法控制，只在物流系统中出现。
        // ============================================================
        manifoldR = new ErekirUnitType("manifold-r") {
            {
                constructor = BuildingTetherPayloadUnit::create;
                controller = u -> new CargoAI();
                isEnemy = false;
                allowedInPayloads = false;
                logicControllable = false;
                playerControllable = false;
                envDisabled = 0;
                payloadCapacity = 0f;

                lowAltitude = false;
                flying = true;
                drag = 0.06f;
                speed = 3.5f;
                rotateSpeed = 9f;
                accel = 0.1f;
                itemCapacity = 100;
                health = 200f;
                hitSize = 11f;
                engineSize = 2.3f;
                engineOffset = 6.5f;
                hidden = true;
                targetable = false;

                setEnginesMirror(
                    new UnitEngine(24f / 4f, -24f / 4f, 2.3f, 315f)
                );
            }
        };

        // ============================================================
        //  assembly-drone-r  (装配无人机)
        //  由 UnitAssembler 自动派出，负责向组装厂运送部件。
        //  内部单位，不显示在数据库中。
        // ============================================================
        assemblyDroneR = new ErekirUnitType("assembly-drone-r") {
            {
                constructor = BuildingTetherPayloadUnit::create;
                controller = u -> new AssemblerAI();

                flying = true;
                drag = 0.06f;
                accel = 0.11f;
                speed = 1.3f;
                health = 90f;
                engineSize = 2f;
                engineOffset = 6.5f;
                payloadCapacity = 0f;
                targetable = false;
                bounded = false;

                outlineColor = Pal.darkOutline;
                isEnemy = false;
                hidden = true;
                useUnitCap = false;
                logicControllable = false;
                playerControllable = false;
                allowedInPayloads = false;
                createWreck = false;
                envEnabled = Env.any;
                envDisabled = Env.none;
            }
        };
    }
    private static void loadCoreUnits() {
        // ============================================================
        //  alpha-r  (塞普罗 T1 核心无人机)
        // ============================================================
        alphaR = new UnitType("alpha-r") {
            {
                controller = u -> u.team.isAI() ? new BuilderAI(true, 400f) : new CommandAI();
                isEnemy = false;

                targetBuildingsMobile = false;
                lowAltitude = true;
                flying = true;
                mineSpeed = 6.5f;
                mineTier = 1;
                buildSpeed = 0.5f;
                drag = 0.05f;
                speed = 3f;
                rotateSpeed = 15f;
                accel = 0.1f;
                fogRadius = 0f;
                itemCapacity = 30;
                health = 150f;
                engineOffset = 6f;
                hitSize = 8f;
                alwaysUnlocked = true;
                wreckSoundVolume = 0.8f;
                deathSoundVolume = 0.7f;

                weapons.add(new Weapon("small-basic-weapon") {
                        {
                            reload = 17f;
                            x = 2.75f;
                            y = 1f;
                            top = false;
                            shootSound = Sounds.shootAlpha;

                            bullet = new LaserBoltBulletType(2.5f, 11) {
                                {
                                    scaleKeepVelocity = true;
                                    width = 1.5f;
                                    height = 4.5f;
                                    hitEffect = despawnEffect = Fx.hitBulletColor;
                                    trailWidth = 1.2f;
                                    trailLength = 3;
                                    shootEffect = Fx.shootSmallColor;
                                    smokeEffect = Fx.hitLaserColor;
                                    backColor = trailColor = Pal.yellowBoltFront;
                                    hitColor = Pal.yellowBoltFront;
                                    frontColor = Color.white;
                                    lightColor = Pal.yellowBoltFront;

                                    lifetime = 60f;
                                    buildingDamageMultiplier = 0f;
                                    homingPower = 0.02f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  beta-r  (塞普罗 T2 核心无人机)
        // ============================================================
        betaR = new UnitType("beta-r") {
            {
                controller = u -> u.team.isAI() ? new BuilderAI(true, 400f) : new CommandAI();
                isEnemy = false;

                targetBuildingsMobile = false;
                flying = true;
                mineSpeed = 7f;
                mineTier = 1;
                buildSpeed = 0.75f;
                drag = 0.05f;
                speed = 3.3f;
                rotateSpeed = 17f;
                accel = 0.1f;
                fogRadius = 0f;
                itemCapacity = 50;
                health = 170f;
                engineOffset = 6f;
                hitSize = 9f;
                lowAltitude = true;

                weapons.add(new Weapon("small-mount-weapon") {
                        {
                            top = false;
                            reload = 20f;
                            x = 3f;
                            y = 1f;
                            recoil = 1f;
                            shootSound = Sounds.shootAlpha;

                            shoot.shots = 2;
                            shoot.shotDelay = 4f;

                            bullet = new LaserBoltBulletType(3f, 11) {
                                {
                                    scaleKeepVelocity = true;
                                    width = 1.5f;
                                    height = 4.5f;
                                    hitEffect = despawnEffect = Fx.hitBulletColor;
                                    trailWidth = 1.2f;
                                    trailLength = 3;
                                    shootEffect = Fx.shootSmallColor;
                                    smokeEffect = Fx.hitLaserColor;
                                    backColor = trailColor = Pal.yellowBoltFront;
                                    hitColor = Pal.yellowBoltFront;
                                    frontColor = Color.white;
                                    lightColor = Pal.yellowBoltFront;

                                    lifetime = 60f;
                                    buildingDamageMultiplier = 0f;
                                    homingPower = 0.03f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  gamma-r  (塞普罗 T3 核心无人机)
        // ============================================================
        gammaR = new UnitType("gamma-r") {
            {
                controller = u -> u.team.isAI() ? new BuilderAI(true, 400f) : new CommandAI();
                isEnemy = false;

                targetBuildingsMobile = false;
                lowAltitude = true;
                flying = true;
                mineSpeed = 8f;
                mineTier = 2;
                buildSpeed = 1f;
                drag = 0.05f;
                speed = 3.55f;
                rotateSpeed = 19f;
                accel = 0.11f;
                fogRadius = 0f;
                itemCapacity = 70;
                health = 220f;
                engineOffset = 6f;
                hitSize = 11f;

                weapons.add(new Weapon("small-mount-weapon") {
                        {
                            top = false;
                            reload = 15f;
                            x = 1f;
                            y = 2f;
                            inaccuracy = 3f;
                            shootSound = Sounds.shootAlpha;

                            shoot = new ShootSpread() {
                                {
                                    shots = 2;
                                    shotDelay = 3f;
                                    spread = 2f;
                                }
                            };

                            bullet = new LaserBoltBulletType(3.5f, 11) {
                                {
                                    scaleKeepVelocity = true;
                                    width = 1.5f;
                                    height = 5f;
                                    hitEffect = despawnEffect = Fx.hitBulletColor;
                                    trailWidth = 1.2f;
                                    trailLength = 4;
                                    shootEffect = Fx.shootSmallColor;
                                    smokeEffect = Fx.hitLaserColor;
                                    backColor = trailColor = Pal.yellowBoltFront;
                                    hitColor = Pal.yellowBoltFront;
                                    frontColor = Color.white;
                                    lightColor = Pal.yellowBoltFront;

                                    lifetime = 70f;
                                    buildingDamageMultiplier = 0f;
                                    homingPower = 0.04f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  evoke-r  (埃里克尔 T1 核心无人机)
        // ============================================================
        evokeR = new ErekirUnitType("evoke-r") {
            {
                constructor = PayloadUnit::create;
                coreUnitDock = true;
                controller = u -> new BuilderAI(true, 500f);
                isEnemy = false;
                envDisabled = 0;

                range = 60f;
                faceTarget = true;
                targetPriority = -2;
                lowAltitude = false;
                mineWalls = true;
                mineFloor = false;
                mineHardnessScaling = false;
                flying = true;
                mineSpeed = 6f;
                mineTier = 3;
                buildSpeed = 1.2f;
                drag = 0.08f;
                speed = 5.6f;
                rotateSpeed = 7f;
                accel = 0.09f;
                itemCapacity = 60;
                health = 300f;
                armor = 1f;
                hitSize = 9f;
                engineSize = 0f;
                payloadCapacity = 2f * 2f * tilesize * tilesize;
                pickupUnits = false;
                vulnerableWithPayloads = true;

                fogRadius = 0f;
                targetable = false;
                hittable = false;

                setEnginesMirror(
                    new UnitEngine(21f / 4f, 19f / 4f, 2.2f, 45f),
                    new UnitEngine(23f / 4f, -22f / 4f, 2.2f, 315f)
                );

                weapons.add(new RepairBeamWeapon() {
                        {
                            widthSinMag = 0.11f;
                            reload = 20f;
                            x = 0f;
                            y = 6.5f;
                            rotate = false;
                            shootY = 0f;
                            beamWidth = 0.7f;
                            repairSpeed = 3.1f;
                            fractionRepairSpeed = 0.06f;
                            aimDst = 0f;
                            shootCone = 15f;
                            mirror = false;

                            targetUnits = false;
                            targetBuildings = true;
                            autoTarget = false;
                            controllable = true;
                            laserColor = Pal.accent;
                            healColor = Pal.accent;

                            bullet = new BulletType() {
                                {
                                    maxRange = 60f;
                                }
                            };
                        }
                    });
            }
        };

        // ============================================================
        //  incite-r  (埃里克尔 T2 核心无人机)
        // ============================================================
        inciteR = new ErekirUnitType("incite-r") {
            {
                constructor = PayloadUnit::create;
                coreUnitDock = true;
                controller = u -> new BuilderAI(true, 500f);
                isEnemy = false;
                envDisabled = 0;

                range = 60f;
                targetPriority = -2;
                lowAltitude = false;
                faceTarget = true;
                mineWalls = true;
                mineFloor = false;
                mineHardnessScaling = false;
                flying = true;
                mineSpeed = 8f;
                mineTier = 3;
                buildSpeed = 1.4f;
                drag = 0.08f;
                speed = 7f;
                rotateSpeed = 8f;
                accel = 0.09f;
                itemCapacity = 90;
                health = 500f;
                armor = 2f;
                hitSize = 11f;
                payloadCapacity = 2f * 2f * tilesize * tilesize;
                pickupUnits = false;
                vulnerableWithPayloads = true;

                fogRadius = 0f;
                targetable = false;
                hittable = false;

                engineOffset = 7.2f;
                engineSize = 3.1f;

                setEnginesMirror(
                    new UnitEngine(25f / 4f, -1f / 4f, 2.4f, 300f)
                );

                weapons.add(new RepairBeamWeapon() {
                        {
                            widthSinMag = 0.11f;
                            reload = 20f;
                            x = 0f;
                            y = 7.5f;
                            rotate = false;
                            shootY = 0f;
                            beamWidth = 0.7f;
                            aimDst = 0f;
                            shootCone = 15f;
                            mirror = false;

                            repairSpeed = 3.3f;
                            fractionRepairSpeed = 0.06f;

                            targetUnits = false;
                            targetBuildings = true;
                            autoTarget = false;
                            controllable = true;
                            laserColor = Pal.accent;
                            healColor = Pal.accent;

                            bullet = new BulletType() {
                                {
                                    maxRange = 60f;
                                }
                            };
                        }
                    });

                drawBuildBeam = false;

                weapons.add(new BuildWeapon("build-weapon") {
                        {
                            rotate = true;
                            rotateSpeed = 7f;
                            x = 14f / 4f;
                            y = 15f / 4f;
                            layerOffset = -0.001f;
                            shootY = 3f;
                        }
                    });
            }
        };

        // ============================================================
        //  emanate-r  (埃里克尔 T3 核心无人机)
        // ============================================================
        emanateR = new ErekirUnitType("emanate-r") {
            {
                constructor = PayloadUnit::create;
                coreUnitDock = true;
                controller = u -> new BuilderAI(true, 500f);
                isEnemy = false;
                envDisabled = 0;

                range = 65f;
                faceTarget = true;
                targetPriority = -2;
                lowAltitude = false;
                mineWalls = true;
                mineFloor = false;
                mineHardnessScaling = false;
                flying = true;
                mineSpeed = 9f;
                mineTier = 3;
                buildSpeed = 1.5f;
                drag = 0.08f;
                speed = 7.5f;
                rotateSpeed = 8f;
                accel = 0.08f;
                itemCapacity = 110;
                health = 700f;
                armor = 3f;
                hitSize = 12f;
                buildBeamOffset = 8f;
                payloadCapacity = 2f * 2f * tilesize * tilesize;
                pickupUnits = false;
                vulnerableWithPayloads = true;

                fogRadius = 0f;
                targetable = false;
                hittable = false;

                engineOffset = 7.5f;
                engineSize = 3.4f;

                setEnginesMirror(
                    new UnitEngine(35f / 4f, -13f / 4f, 2.7f, 315f),
                    new UnitEngine(28f / 4f, -35f / 4f, 2.7f, 315f)
                );

                weapons.add(new RepairBeamWeapon() {
                        {
                            widthSinMag = 0.11f;
                            reload = 20f;
                            x = 19f / 4f;
                            y = 19f / 4f;
                            rotate = false;
                            shootY = 0f;
                            beamWidth = 0.7f;
                            aimDst = 0f;
                            shootCone = 40f;
                            mirror = true;

                            repairSpeed = 3.6f / 2f;
                            fractionRepairSpeed = 0.03f;

                            targetUnits = false;
                            targetBuildings = true;
                            autoTarget = false;
                            controllable = true;
                            laserColor = Pal.accent;
                            healColor = Pal.accent;

                            bullet = new BulletType() {
                                {
                                    maxRange = 65f;
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
        //  merui-serpulo  (塞普罗特供 T1 多足机甲)
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