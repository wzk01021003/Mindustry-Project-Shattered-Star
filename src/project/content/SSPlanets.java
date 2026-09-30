package project.content;

import arc.graphics.Color;
import arc.struct.ObjectMap;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.graphics.Pal;
import mindustry.graphics.g3d.HexMesh;
import mindustry.graphics.g3d.HexSkyMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.SunMesh;
import mindustry.maps.planet.SerpuloPlanetGenerator;
import mindustry.type.Planet;

public class SSPlanets {

    // ============================================================
    //  声明区
    // ============================================================

    public static Planet ssSun;
    public static Planet serpuloR;

    // ============================================================
    //  load()
    //  顺序很重要：太阳必须最先 new，其他星球才能拿它当父级
    // ============================================================
    public static void load() {
        loadSun();
        loadSerpuloR();
    }

    // ============================================================
    //  太阳
    // ============================================================
    private static void loadSun() {
        ssSun = new Planet("ss-sun", null, 4f) {
            {
                bloom = true;
                accessible = false;
                visible = false;

                hasAtmosphere = false;
                updateLighting = false;

                iconColor = Color.valueOf("ffc64c");

                meshLoader = () -> new SunMesh(
                    this, 4,
                    5, 0.3f, 1.7f, 1.2f, 1f,
                    1.1f,
                    Color.valueOf("ff7a38"),
                    Color.valueOf("ff9638"),
                    Color.valueOf("ffc64c"),
                    Color.valueOf("ffc64c"),
                    Color.valueOf("ffe371"),
                    Color.valueOf("f4ee8e")
                );
            }
        };
    }

    // ============================================================
    //  serpulo-r
    // ============================================================
    private static void loadSerpuloR() {
        serpuloR = new Planet("serpulo-r", ssSun, 1f, 3) {
            {

                // ============ 生成器（借用原版塞普罗生成器） ============
                generator = new SerpuloPlanetGenerator();
                meshLoader = () -> new HexMesh(this, 6);
                cloudMeshLoader = () -> new MultiMesh(
                    new HexSkyMesh(this, 11, 0.15f, 0.13f, 5,
                        new Color().set(Pal.spore).mul(0.9f).a(0.75f),
                        2, 0.45f, 0.9f, 0.38f),
                    new HexSkyMesh(this, 1, 0.6f, 0.16f, 5,
                        Color.white.cpy().lerp(Pal.spore, 0.55f).a(0.75f),
                        2, 0.45f, 1f, 0.41f)
                );

                // ============ 基础属性 ============
                sectorSeed = 2;
                launchCapacityMultiplier = 0.5f;
                enemyFactoryActivationDelay = 60f * 60f * 2f;
                allowWaves = true;
                allowLegacyLaunchPads = true;
                allowSectorInvasion = true;
                allowLaunchSchematics = true;
                allowLaunchLoadout = true;
                allowSelfSectorLaunch = true;
                enemyCoreSpawnReplace = true;
                showRtsAIRule = true;

                // ============ 可见性 / 可访问性 ============
                alwaysUnlocked = true;
                accessible = true;
                visible = true;

                // ============ 起始区块 ============
                startSector = 170;

                // ============ 视觉 ============
                iconColor = Color.valueOf("7d4dff");
                atmosphereColor = Color.valueOf("3c1b8f");
                atmosphereRadIn = 0.02f;
                atmosphereRadOut = 0.3f;
                landCloudColor = Pal.spore.cpy().a(0.5f);

                // ============ 规则 ============
                ruleSetter = r -> {
                    r.waveTeam = Team.crux;
                    r.placeRangeCheck = false;
                    r.hideSpawns = true;
                    r.derelictRepair = true;
                    r.coreDestroyClear = true;
                };

                // ============ 区块捕获替换 ============
                sectorCaptureReplacements = ObjectMap.of(
                    Blocks.metalTiles12, Blocks.metalTiles11,
                    Blocks.metalTiles6,  Blocks.metalTiles10
                );
            }
        };
    }
}