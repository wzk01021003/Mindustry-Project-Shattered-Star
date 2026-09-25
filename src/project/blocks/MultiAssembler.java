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

    /** 最多排队数量 */
    public int maxCount = 8;
    /** 区域半径（格） */
    public int areaRadius = 5;
    /** 最大载荷容量 */
    public int payloadCapacity = 30;
    /** 电力消耗（自定义字段，避开 GenericCrafter 保留的 consumePower） */
    public float powerUse = 0f;

    /** 供 JSON 填充的配方数组 */
    public Recipe[] recipe;
    public Seq<Recipe> recipes = new Seq<>();

    /** 所有配方用到的输入物品 / 液体（用于 acceptItem / acceptLiquid） */
    public ObjectSet<Item> inputItemSet = new ObjectSet<>();
    public ObjectSet<Liquid> inputLiquidSet = new ObjectSet<>();

    public MultiAssembler(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = true;
        // 队列状态走 write/read，不走 config 存档
        saveConfig = false;
        hasItems = true;
        hasLiquids = true;
        hasPower = true;
        acceptsPayload = true;

        // 点击 UI 按钮时把对应配方加入队列
        config(Integer.class, (Building b, Integer index) -> {
            if (!(b instanceof MultiAssemblerBuild)) return;
            MultiAssemblerBuild build = (MultiAssemblerBuild) b;
            if (index == null || index < 0 || index >= recipes.size) return;
            build.addJob(recipes.get(index));
        });
    }

    @Override
    public void init() {
        // 解析 JSON 里的 recipe 数组
        if (recipe != null) {
            for (Recipe r : recipe) {
                r.init();
                recipes.add(r);
            }
            recipe = null;
        }

        // 收集所有输入物品 / 液体
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

    /** 单个单位的配方 */
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

    /** 单个生产任务 */
    public static class UnitJob {
        public Recipe recipe;
        public float progress;
        public int offsetX, offsetY;

        public UnitJob(Recipe recipe, int offsetX, int offsetY) {
            this.recipe = recipe;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    public class MultiAssemblerBuild extends GenericCrafterBuild {

        public Seq<UnitJob> jobs = new Seq<>();
        /** 已接收的载荷计数：内容 -> 数量 */
        public ObjectMap<UnlockableContent, Integer> payloadCounts = new ObjectMap<>();

        // ================================================================
        //  输入接受（物品 / 液体）
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

        // ================================================================
        //  载荷接收（带下落特效）
        // ================================================================

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
        //  队列管理
        // ================================================================

        public void addJob(Recipe r) {
            if (jobs.size >= maxCount) {
                if (!Vars.headless) {
                    Vars.ui.showInfoToast(
                        Core.bundle.get("ss-multi-assembler.queue-full", "Queue full"), 1.5f);
                }
                return;
            }
            int[] slot = findFreeSlot(r.unit);
            if (slot == null) {
                if (!Vars.headless) {
                    Vars.ui.showInfoToast(
                        Core.bundle.get("ss-multi-assembler.no-space", "No free space"), 1.5f);
                }
                return;
            }
            jobs.add(new UnitJob(r, slot[0], slot[1]));
        }

        /**
         * 按到方块中心的距离升序搜索空闲位置。
         * 排除：与方块自身重叠、与已有 job 冲突。
         */
        public int[] findFreeSlot(UnitType unit) {
            int tiles = (int) Math.ceil(unit.hitSize / Vars.tilesize);
            int maxR = Math.max(areaRadius, tiles + 2) + size;

            Seq<int[]> candidates = new Seq<>();
            for (int gy = -maxR; gy <= maxR; gy++) {
                for (int gx = -maxR; gx <= maxR; gx++) {
                    // 排除与方块自身重叠
                    if (gx + tiles > 0 && gx < size && gy + tiles > 0 && gy < size) continue;
                    candidates.add(new int[]{gx, gy});
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
                boolean conflict = false;
                for (UnitJob j : jobs) {
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

            // 用 update 检测 jobs / payloadCounts 变化，变化时重建
            final int[] lastSizes = {-1, -1};
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

            // ===== 配方选择 =====
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

            // ===== 队列 =====
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
                        t.button(Icon.cancel, () -> jobs.remove(idx)).size(30f).right();
                    }).growX().pad(2f).left().row();
                }
            }

            // ===== 载荷 =====
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
            if (jobs.isEmpty()) return;

            for (int i = jobs.size - 1; i >= 0; i--) {
                UnitJob job = jobs.get(i);
                Recipe r = job.recipe;

                if (!canPlace(r.unit, job.offsetX, job.offsetY)) {
                    job.progress = 0f;
                    continue;
                }
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
                }
            }
        }

        // ================================================================
        //  材料检查与消耗
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
        //  选中时绘制落点框
        // ================================================================

        @Override
        public void drawSelect() {
            super.drawSelect();
            for (UnitJob job : jobs) {
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
                write.i(j.offsetX);
                write.i(j.offsetY);
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
                int ox = read.i();
                int oy = read.i();
                if (idx >= 0 && idx < recipes.size) {
                    UnitJob j = new UnitJob(recipes.get(idx), ox, oy);
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
        }
    }
}