package project.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.type.PayloadStack;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.world.Tile;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.Stat;

public class MultiAssembler extends GenericCrafter {

    public int maxCount = 8;
    public int areaRadius = 5;
    public int payloadCapacity = 30;
    public float powerUse = 0f;

    public Recipe[] recipe;
    public Seq<Recipe> recipes = new Seq<>();

    public ObjectSet<Item> inputItemSet = new ObjectSet<>();
    public ObjectSet<Liquid> inputLiquidSet = new ObjectSet<>();

    public MultiAssembler(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = true;
        saveConfig = false;
        hasItems = true;
        hasLiquids = true;
        hasPower = true;
        acceptsPayload = true;

        config(Integer.class, (Building b, Integer index) -> {
                if (!(b instanceof MultiAssemblerBuild)) return;
                MultiAssemblerBuild build = (MultiAssemblerBuild) b;
                if (index == null || index < 0 || index >= recipes.size) return;
                build.addJob(recipes.get(index));
            });
    }

    @Override
    public void init() {
        if (recipe != null) {
            for (Recipe r : recipe) {
                r.init();
                recipes.add(r);
            }
            recipe = null;
        }

        for (Recipe r : recipes) {
            for (ItemStack s : r.inputItems) inputItemSet.add(s.item);
            for (LiquidStack s : r.inputLiquids) inputLiquidSet.add(s.liquid);
        }

        if (powerUse > 0f) consumePower(powerUse);

        super.init();
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.output, table -> {
                table.clearChildren();
                table.left();
                for (Recipe r : recipes) {
                    table.table(t -> {
                            t.left();
                            t.add("[accent]" + r.unit.localizedName + "[]").padRight(8);
                            t.add("[lightgray]" + (r.craftTime / 60f) + "s[]").padRight(8);
                            t.row();
                            t.add("[lightgray]" + Core.bundle.get("ss-multi-assembler.requires", "Requires") + ": []");
                            for (ItemStack s : r.inputItems) t.image(s.item.uiIcon).size(24f).padRight(2);
                            for (LiquidStack s : r.inputLiquids) t.image(s.liquid.uiIcon).size(24f).padRight(2);
                            for (PayloadStack s : r.cachedInputPayloads) t.image(s.item.uiIcon).size(24f).padRight(2);
                        }).left().row();
                }
            });
    }

    public static class Recipe {
        public UnitType unit;
        public float craftTime = 60f;
        public ItemStack[] inputItems = {};
        public LiquidStack[] inputLiquids = {};
        public String[] inputPayloads = {};
        public transient PayloadStack[] cachedInputPayloads = {};

        public Recipe() {}

        public void init() {
            if (inputItems == null) inputItems = new ItemStack[0];
            if (inputLiquids == null) inputLiquids = new LiquidStack[0];
            if (inputPayloads == null) inputPayloads = new String[0];

            Seq<PayloadStack> list = new Seq<>();
            for (String s : inputPayloads) {
                if (s == null || s.isEmpty()) continue;
                String[] parts = s.split("/");
                if (parts.length != 2) continue;

                UnlockableContent content = Vars.content.getByName(ContentType.block, parts[0]);
                if (content == null) content = Vars.content.getByName(ContentType.unit, parts[0]);
                if (content == null) content = Vars.content.getByName(ContentType.item, parts[0]);

                if (content != null) {
                    try {
                        int amount = Integer.parseInt(parts[1]);
                        if (amount > 0) list.add(new PayloadStack(content, amount));
                    } catch (NumberFormatException ignored) {}
                }
            }
            cachedInputPayloads = list.toArray(PayloadStack.class);
        }
    }

    public static class UnitJob {
        public Recipe recipe;
        public float progress;
        public int offsetX, offsetY;

        public UnitJob(Recipe recipe, int offsetX, int offsetY) {
            this.recipe = recipe;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        public boolean hasSlot() {
            return offsetX != Integer.MIN_VALUE;
        }
    }

    public class MultiAssemblerBuild extends GenericCrafterBuild {

        public Seq<UnitJob> jobs = new Seq<>();
        public ObjectMap<UnlockableContent, Integer> payloadCounts = new ObjectMap<>();

        // ================================================================
        //  输入
        // ================================================================

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items.get(item) >= getMaximumAccepted(item)) return false;
            return inputItemSet.contains(item);
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            if (liquids.get(liquid) >= liquidCapacity) return false;
            return inputLiquidSet.contains(liquid);
        }

        @Override
        public boolean acceptPayload(Building source, Payload payload) {
            return payloadCounts.get(payload.content(), 0) < payloadCapacity;
        }

        @Override
        public void handlePayload(Building source, Payload payload) {
            int current = payloadCounts.get(payload.content(), 0);
            payloadCounts.put(payload.content(), current + 1);
            Fx.payloadReceive.at(payload.x(), payload.y(), rotation * 90f);
        }

        // ================================================================
        //  队列
        // ================================================================

        public void addJob(Recipe r) {
            if (jobs.size >= maxCount) {
                if (!Vars.headless) Vars.ui.showInfoToast(
                    Core.bundle.get("ss-multi-assembler.queue-full", "Queue full"), 1.5f);
                return;
            }

            UnitJob newJob = new UnitJob(r, Integer.MIN_VALUE, Integer.MIN_VALUE);
            jobs.add(newJob);
            reassignSlots();

            if (!newJob.hasSlot()) {
                // 没找到位置，撤回
                jobs.remove(newJob);
                reassignSlots();
                if (!Vars.headless) Vars.ui.showInfoToast(
                    Core.bundle.get("ss-multi-assembler.no-space", "No free space"), 1.5f);
            }
        }

        /**
        * 校验所有 job 的位置是否有效。无效则触发全体重排。
        * 每帧调用，开销可忽略（最多 8 个 job）。
        */
        private void validateJobs() {
            if (jobs.isEmpty()) return;

            boolean need = false;
            for (UnitJob j : jobs) {
                if (!j.hasSlot() || !canPlace(j.recipe.unit, j.offsetX, j.offsetY)) {
                    need = true;
                    break;
                }
            }
            if (need) reassignSlots();
        }

        /**
        * 重新为所有 job 分配位置：按队列顺序，先来先占。
        * 每个 job 的位置基于当前地形和队列结构重新计算。
        */
        private void reassignSlots() {
            for (UnitJob j : jobs) {
                j.offsetX = Integer.MIN_VALUE;
                j.offsetY = Integer.MIN_VALUE;
            }
            for (UnitJob j : jobs) {
                int[] slot = findFreeSlot(j.recipe.unit, j);
                if (slot != null) {
                    j.offsetX = slot[0];
                    j.offsetY = slot[1];
                }
            }
        }

        /**
        * 找空闲位置。跳过 exclude 以及 offset 未分配的其他 job。
        * 按到方块中心的距离升序。
        */
        public int[] findFreeSlot(UnitType unit, UnitJob exclude) {
            int tiles = (int) Math.ceil(unit.hitSize / Vars.tilesize);
            int maxR = Math.max(areaRadius, tiles + 2) + size;

            Seq<int[]> candidates = new Seq<>();
            for (int gy = -maxR; gy <= maxR; gy++) {
                for (int gx = -maxR; gx <= maxR; gx++) {
                    // 排除与方块自身重叠
                    if (gx + tiles > 0 && gx < size && gy + tiles > 0 && gy < size) continue;
                    candidates.add(new int[]{
                            gx, gy});
                }
            }

            final float halfSize = size / 2f;
            candidates.sort((a, b) -> {
                    float ax = a[0] + tiles / 2f - halfSize;
                    float ay = a[1] + tiles / 2f - halfSize;
                    float bx = b[0] + tiles / 2f - halfSize;
                    float by = b[1] + tiles / 2f - halfSize;
                    return Float.compare(ax * ax + ay * ay, bx * bx + by * by);
                });

            for (int[] c : candidates) {
                // 还要能真正放下（地面不能有建筑、不能是深水等）
                if (!canPlace(unit, c[0], c[1])) continue;

                boolean conflict = false;
                for (UnitJob j : jobs) {
                    if (j == exclude) continue;
                    if (!j.hasSlot()) continue;
                    // 还没分配的不算冲突
                    int jt = (int) Math.ceil(j.recipe.unit.hitSize / Vars.tilesize);
                    int need = Math.max(tiles, jt);
                    if (Math.abs(j.offsetX - c[0]) < need && Math.abs(j.offsetY - c[1]) < need) {
                        conflict = true;
                        break;
                    }
                }
                if (!conflict) return c;
            }
            return null;
        }

        // ================================================================
        //  UI
        // ================================================================

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);

            Table content = new Table();
            content.left().top();
            table.add(content).growX().left().padTop(6f);

            final int[] lastSizes = {
                -1, -1};
            content.update(() -> {
                    if (lastSizes[0] != jobs.size || lastSizes[1] != payloadCounts.size) {
                        lastSizes[0] = jobs.size;
                        lastSizes[1] = payloadCounts.size;
                        rebuildContent(content);
                    }
                });

            rebuildContent(content);
            lastSizes[0] = jobs.size;
            lastSizes[1] = payloadCounts.size;
        }

        private void rebuildContent(Table content) {
            content.clearChildren();
            content.left().top();

            content.label(() -> Core.bundle.get("ss-multi-assembler.select", "Select unit:"))
            .left().padTop(4f).row();

            for (int i = 0; i < recipes.size; i++) {
                final int idx = i;
                Recipe r = recipes.get(i);
                content.table(Styles.grayPanel, t -> {
                        t.left();
                        t.button(r.unit.localizedName, () -> configure(idx))
                        .size(160f, 42f).pad(4f).left();
                    }).growX().pad(2f).left().row();
            }

            content.label(() -> Core.bundle.get("ss-multi-assembler.queue", "Queue")
                + " (" + jobs.size + "/" + maxCount + "):").left().padTop(6f).row();

            if (jobs.isEmpty()) {
                content.table(Styles.grayPanelDark, t -> {
                        t.left();
                        t.label(() -> "[gray]"
                            + Core.bundle.get("ss-multi-assembler.empty", "(empty)"))
                        .left().pad(4f);
                    }).growX().pad(2f).left().row();
            } else {
                for (int i = 0; i < jobs.size; i++) {
                    final int idx = i;
                    UnitJob j = jobs.get(i);
                    content.table(Styles.grayPanelDark, t -> {
                            t.left();
                            t.label(() -> j.recipe.unit.localizedName + "  "
                                + (int) (j.progress * 100) + "%")
                            .left().width(180f).padLeft(6f);
                            t.button(Icon.cancel, () -> {
                                    jobs.remove(idx);
                                    reassignSlots();
                                }).size(30f).right();
                        }).growX().pad(2f).left().row();
                }
            }

            if (!payloadCounts.isEmpty()) {
                content.label(() -> Core.bundle.get("ss-multi-assembler.payloads", "Payloads:"))
                .left().padTop(6f).row();

                content.table(Styles.grayPanelDark, t -> {
                        t.left();
                        int col = 0;
                        for (ObjectMap.Entry<UnlockableContent, Integer> e : payloadCounts) {
                            t.image(e.key.uiIcon).size(28f).padRight(4f);
                            t.label(() -> "x" + e.value).left().padRight(10f);
                            if (++col % 3 == 0) t.row();
                        }
                    }).growX().pad(2f).left().row();
            }
        }

        // ================================================================
        //  位置判断
        // ================================================================

        public boolean canPlace(UnitType unit, int gx, int gy) {
            int tiles = (int) Math.ceil(unit.hitSize / Vars.tilesize);
            for (int dy = 0; dy < tiles; dy++) {
                for (int dx = 0; dx < tiles; dx++) {
                    Tile t = Vars.world.tile(tile.x + gx + dx, tile.y + gy + dy);
                    if (t == null) return false;
                    if (t.solid()) return false;
                    if (!unit.flying && t.floor().isDeep()) return false;
                }
            }
            return true;
        }

        // ================================================================
        //  每帧更新
        // ================================================================

        @Override
        public void updateTile() {
            // 先校验所有 job 位置
            validateJobs();

            if (jobs.isEmpty()) return;

            for (int i = jobs.size - 1; i >= 0; i--) {
                UnitJob job = jobs.get(i);
                Recipe r = job.recipe;

                if (!job.hasSlot()) continue;
                if (!canPlace(r.unit, job.offsetX, job.offsetY)) continue;
                if (!hasMaterials(r)) continue;

                job.progress += delta() * efficiency / r.craftTime;
                if (job.progress >= 1f) {
                    consumeMaterials(r);

                    int tiles = (int) Math.ceil(r.unit.hitSize / Vars.tilesize);
                    float cx = tile.worldx() + (job.offsetX + (tiles - 1) / 2f) * Vars.tilesize;
                    float cy = tile.worldy() + (job.offsetY + (tiles - 1) / 2f) * Vars.tilesize;

                    Unit u = r.unit.create(team);
                    u.set(cx, cy);
                    u.rotation = rotation * 90f;
                    u.add();

                    jobs.remove(i);
                    reassignSlots();
                }
            }
        }

        // ================================================================
        //  材料
        // ================================================================

        public boolean hasMaterials(Recipe r) {
            for (ItemStack s : r.inputItems) if (items.get(s.item) < s.amount) return false;
            for (LiquidStack s : r.inputLiquids) if (liquids.get(s.liquid) < s.amount) return false;
            for (PayloadStack s : r.cachedInputPayloads) {
                if (payloadCounts.get(s.item, 0) < s.amount) return false;
            }
            return true;
        }

        public void consumeMaterials(Recipe r) {
            for (ItemStack s : r.inputItems) items.remove(s.item, s.amount);
            for (LiquidStack s : r.inputLiquids) liquids.remove(s.liquid, s.amount);
            for (PayloadStack s : r.cachedInputPayloads) {
                int have = payloadCounts.get(s.item, 0);
                if (have <= s.amount) payloadCounts.remove(s.item);
                else payloadCounts.put(s.item, have - s.amount);
            }
        }

        // ================================================================
        //  绘制
        // ================================================================

        @Override
        public void drawSelect() {
            super.drawSelect();
            for (UnitJob job : jobs) {
                if (!job.hasSlot()) continue;
                int tiles = (int) Math.ceil(job.recipe.unit.hitSize / Vars.tilesize);
                boolean ok = canPlace(job.recipe.unit, job.offsetX, job.offsetY);
                Color c = ok ? Color.green : Color.red;

                float w = tiles * Vars.tilesize;
                float cx = tile.worldx() + (job.offsetX + (tiles - 1) / 2f) * Vars.tilesize;
                float cy = tile.worldy() + (job.offsetY + (tiles - 1) / 2f) * Vars.tilesize;

                Draw.color(c, 0.3f);
                Fill.rect(cx, cy, w, w);

                Draw.color(c);
                Lines.stroke(1.2f);
                Lines.rect(cx - w / 2f, cy - w / 2f, w, w);
            }
            Draw.reset();
        }

        // ================================================================
        //  存档
        // ================================================================

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(jobs.size);
            for (UnitJob j : jobs) {
                write.i(recipes.indexOf(j.recipe, true));
                write.f(j.progress);
                // 不再存 offset，由 reassignSlots 每次重算
            }
            write.i(payloadCounts.size);
            for (ObjectMap.Entry<UnlockableContent, Integer> e : payloadCounts) {
                write.b(e.key.getContentType().ordinal());
                write.s(e.key.id);
                write.i(e.value);
            }
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int n = read.i();
            jobs.clear();
            for (int i = 0; i < n; i++) {
                int idx = read.i();
                float prog = read.f();
                if (idx >= 0 && idx < recipes.size) {
                    UnitJob j = new UnitJob(recipes.get(idx), Integer.MIN_VALUE, Integer.MIN_VALUE);
                    j.progress = prog;
                    jobs.add(j);
                }
            }
            int pn = read.i();
            payloadCounts.clear();
            for (int i = 0; i < pn; i++) {
                byte ct = read.b();
                int id = read.s();
                int count = read.i();
                ContentType type = ContentType.all[ct];
                UnlockableContent c = Vars.content.getByID(type, id);
                if (c != null) payloadCounts.put(c, count);
            }

            // 读档后立刻重排位置
            reassignSlots();
        }
    }
}