package project.blocks;

import arc.util.Log;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.type.PayloadStack;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import mindustry.world.blocks.units.UnitAssembler;
import mindustry.world.blocks.units.UnitAssembler.AssemblerUnitPlan;

public class MultiAssembler extends UnitAssembler {

    /** 最多同时排队的单位数量 */
    public int maxCount = 8;
    /** 区域计算时额外留白（格） */
    public float padding = 3f;
    /** 区域边长上限 */
    public int maxAreaSize = 24;

    public MultiAssembler(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = true;
        saveConfig = true;
        hasItems = true;
        hasLiquids = true;
        hasPower = true;

        // 点击 UI 添加单位
        config(UnitType.class, (MultiAssemblerBuild build, UnitType type) -> {
                build.addJob(type);
            });

        // 点击取消某个任务
        config(Integer.class, (MultiAssemblerBuild build, Integer index) -> {
                if (index >= 0 && index < build.jobs.size) {
                    build.jobs.remove(index);
                    build.recalculateArea();
                }
            });
    }

    /** 根据单位大小和数量计算区域边长 */
    public int computeAreaSize(UnitType unit, int count) {
        if (unit == null) return 3;
        int unitTiles = Mathf.ceil(unit.hitSize / Vars.tilesize);
        int side = Mathf.ceil(Mathf.sqrt(Math.max(1, count)));
        return Mathf.clamp(side * unitTiles + (int) padding, 3, maxAreaSize);
    }

    /** 单个生产任务：记录单位、配方、进度、落点 */
    public static class UnitJob {
        public UnitType unit;
        public AssemblerUnitPlan plan;
        public float progress;
        public float craftTime;
        public int offsetX, offsetY;

        public UnitJob(UnitType unit, AssemblerUnitPlan plan, int offsetX, int offsetY) {
            this.unit = unit;
            this.plan = plan;
            this.craftTime = plan.time;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    public class MultiAssemblerBuild extends UnitAssemblerBuild {
        public Seq<UnitJob> jobs = new Seq<>();

        @Override
        public void created() {
            super.created();
            recalculateArea();
        }

        /** 往队列里加一个单位，会自动分配落点 */
        public void addJob(UnitType type) {
            if (jobs.size >= maxCount) return;
            AssemblerUnitPlan plan = null;
            for (AssemblerUnitPlan p : plans) {
                if (p.unit == type) {
                    plan = p;
                    break;
                }
            }
            if (plan == null) return;

            // 计算一个空闲的落点
            int unitTiles = Mathf.ceil(type.hitSize / Vars.tilesize);
            int[] slot = findFreeSlot(unitTiles);
            if (slot == null) return;

            jobs.add(new UnitJob(type, plan, slot[0], slot[1]));
            recalculateArea();
        }

        /** 找一个不跟其他 job 冲突的落点 */
        public int[] findFreeSlot(int unitTiles) {
            int half = areaSize / 2;
            for (int gy = -half; gy < areaSize - half; gy++) {
                for (int gx = -half; gx < areaSize - half; gx++) {
                    boolean conflict = false;
                    for (UnitJob j : jobs) {
                        int otherTiles = Mathf.ceil(j.unit.hitSize / Vars.tilesize);
                        if (Math.abs(j.offsetX - gx) < Math.max(unitTiles, otherTiles)
                            && Math.abs(j.offsetY - gy) < Math.max(unitTiles, otherTiles)) {
                            conflict = true;
                            break;
                        }
                    }
                    if (!conflict) return new int[]{
                        gx, gy};
                }
            }
            return null;
        }

        public void recalculateArea() {
            int maxUnits = Math.max(1, jobs.size);
            int maxSide = 3;
            for (UnitJob j : jobs) {
                int s = computeAreaSize(j.unit, 1);
                maxSide = Math.max(maxSide, s);
            }
            // 同时考虑队列总规模
            int totalSide = computeAreaSize(
                jobs.size > 0 ? jobs.first().unit : null, maxUnits);
            areaSize = Math.max(maxSide, totalSide);
        }

        @Override
        @Override
        public void buildConfiguration(Table table) {
            table.button("HELLO", () -> {
                    Log.info("Button clicked!");
                }).size(200f, 50f);
        }

        /** 判断某个落点能不能放下单位 */
        public boolean canPlaceAt(UnitType type, int gx, int gy) {
            int unitTiles = Mathf.ceil(type.hitSize / Vars.tilesize);
            for (int dy = 0; dy < unitTiles; dy++) {
                for (int dx = 0; dx < unitTiles; dx++) {
                    Tile t = Vars.world.tile(tile.x + gx + dx, tile.y + gy + dy);
                    if (t == null) return false;
                    if (!type.flying && (t.solid() || t.floor().isDeep())) return false;
                    if (type.flying && t.solid()) return false;
                }
            }
            return true;
        }

        @Override
        public void updateTile() {
            // 不调用 super.updateTile()，因为我们重写了整个生产逻辑
            if (jobs.isEmpty()) return;

            float eff = efficiency * delta();
            int half = areaSize / 2;

            for (int i = jobs.size - 1; i >= 0; i--) {
                UnitJob job = jobs.get(i);

                // 检查落点是否合法
                if (!canPlaceAt(job.unit, job.offsetX, job.offsetY)) {
                    job.progress = 0f;
                    // 不能放就暂停
                    continue;
                }

                // 检查材料是否齐全（物品/液体/载荷）
                if (!hasMaterials(job.plan)) {
                    continue;
                    // 材料不足就暂停
                }

                // 推进进度
                job.progress += eff;
                if (job.progress >= job.craftTime) {
                    // 完成时消耗材料
                    consumeMaterials(job.plan);

                    // 生成单位
                    Unit u = job.unit.create(team);
                    u.set(
                        tile.x * Vars.tilesize + (job.offsetX + 0.5f) * Vars.tilesize,
                        tile.y * Vars.tilesize + (job.offsetY + 0.5f) * Vars.tilesize
                    );
                    u.rotation = rotation * 90f;
                    u.add();

                    jobs.remove(i);
                }
            }

            // 队列变化后重新计算区域
            recalculateArea();
        }

        /** 检查材料是否足够（支持物品、液体、载荷） */
        public boolean hasMaterials(AssemblerUnitPlan plan) {
            if (plan.requirements.size == 0) return true;
            for (PayloadStack stack : plan.requirements) {
                UnlockableContent content = stack.item;
                int amount = stack.amount;
                if (content instanceof Item item) {
                    if (items == null || items.get(item) < amount) return false;
                } else if (content instanceof Liquid liquid) {
                    if (liquids == null || liquids.get(liquid) < amount) return false;
                } else {
                    if (getPayloads() == null || getPayloads().get(content) < amount) return false;
                }
            }
            return true;
        }

        /** 消耗材料（支持物品、液体、载荷） */
        public void consumeMaterials(AssemblerUnitPlan plan) {
            if (plan.requirements.size == 0) return;
            for (PayloadStack stack : plan.requirements) {
                UnlockableContent content = stack.item;
                int amount = stack.amount;
                if (content instanceof Item item) {
                    if (items != null) items.remove(item, amount);
                } else if (content instanceof Liquid liquid) {
                    if (liquids != null) liquids.remove(liquid, amount);
                } else {
                    if (getPayloads() != null) getPayloads().remove(content, amount);
                }
            }
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            if (jobs.isEmpty()) return;

            int half = areaSize / 2;
            for (UnitJob job : jobs) {
                int unitTiles = Mathf.ceil(job.unit.hitSize / Vars.tilesize);
                boolean ok = canPlaceAt(job.unit, job.offsetX, job.offsetY);
                Color c = ok ? Color.green : Color.red;

                Draw.color(c, 0.3f);
                Fill.rect(
                    tile.x * Vars.tilesize + (job.offsetX + unitTiles / 2f) * Vars.tilesize,
                    tile.y * Vars.tilesize + (job.offsetY + unitTiles / 2f) * Vars.tilesize,
                    unitTiles * Vars.tilesize, unitTiles * Vars.tilesize
                );

                Draw.color(c);
                Lines.stroke(1.2f);
                Lines.rect(
                    tile.x * Vars.tilesize + job.offsetX * Vars.tilesize,
                    tile.y * Vars.tilesize + job.offsetY * Vars.tilesize,
                    unitTiles * Vars.tilesize, unitTiles * Vars.tilesize
                );
            }

            // 未使用的格子用淡蓝框
            Draw.color(Color.sky, 0.15f);
            for (int gx = -half; gx < areaSize - half; gx++) {
                for (int gy = -half; gy < areaSize - half; gy++) {
                    Lines.stroke(0.8f);
                    Lines.rect(
                        (tile.x + gx - 0.5f) * Vars.tilesize,
                        (tile.y + gy - 0.5f) * Vars.tilesize,
                        Vars.tilesize, Vars.tilesize
                    );
                }
            }
            Draw.reset();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(jobs.size);
            for (UnitJob j : jobs) {
                write.s(j.unit.id);
                write.f(j.progress);
                write.i(j.offsetX);
                write.i(j.offsetY);
            }
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int n = read.i();
            jobs.clear();
            for (int i = 0; i < n; i++) {
                short uid = read.s();
                float prog = read.f();
                int ox = read.i();
                int oy = read.i();
                UnitType u = Vars.content.unit(uid);
                if (u != null) {
                    AssemblerUnitPlan plan = null;
                    for (AssemblerUnitPlan p : plans) {
                        if (p.unit == u) {
                            plan = p;
                            break;
                        }
                    }
                    if (plan != null) {
                        UnitJob job = new UnitJob(u, plan, ox, oy);
                        job.progress = prog;
                        jobs.add(job);
                    }
                }
            }
            recalculateArea();
        }
    }
}