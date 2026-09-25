package project.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.type.PayloadStack;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.Stat;

public class MultiAssembler extends GenericCrafter {

    public int maxCount = 8;
    public int areaRadius = 5;
    public int payloadCapacity = 30;
    /** 从 JSON 读取的电力消耗（自定义字段，避开 GenericCrafter 的 consumePower 保留字段） */
    public float powerUse = 0f;

    public Recipe[] recipe;
    public Seq<Recipe> recipes = new Seq<>();

    public MultiAssembler(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = true;
        // 队列状态走 write/read，不走 config 存档，避免读档重放导致重复添加
        saveConfig = false;
        hasItems = true;
        hasLiquids = true;
        hasPower = true;
        acceptsPayload = true;

        // lambda 第一个参数必须是 Building，不能用 MultiAssemblerBuild
        config(Integer.class, (Building b, Integer index) -> {
                if (b instanceof MultiAssemblerBuild build && index != null
                    && index >= 0 && index < recipes.size) {
                    build.addJob(recipes.get(index));
                }
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
        // 电力消耗
        if (powerUse > 0f) {
            consumePower(powerUse);
        }
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

                // 依次查 block / unit / item，避免跨类型冲突
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
    }

    public class MultiAssemblerBuild extends GenericCrafterBuild {
        public Seq<UnitJob> jobs = new Seq<>();
        public ObjectMap<UnlockableContent, Integer> payloadCounts = new ObjectMap<>();

        @Override
        public boolean acceptPayload(Building source, Payload payload) {
            int current = payloadCounts.get(payload.content(), 0);
            return current < payloadCapacity;
        }

        @Override
        public void handlePayload(Building source, Payload payload) {
            int current = payloadCounts.get(payload.content(), 0);
            payloadCounts.put(payload.content(), current + 1);
        }

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
        * 螺旋向外搜索，避开：
        *  1. 方块自身占据的格子
        *  2. 与已有 job 的落点重叠
        */
        public int[] findFreeSlot(UnitType unit) {
            int tiles = (int)Math.ceil(unit.hitSize / Vars.tilesize);
            int maxR = Math.max(areaRadius, tiles + 2);

            for (int r = 0; r <= maxR; r++) {
                for (int gy = -r; gy <= r; gy++) {
                    for (int gx = -r; gx <= r; gx++) {
                        // 只搜当前环
                        if (Math.max(Math.abs(gx), Math.abs(gy)) != r) continue;

                        // 单位占据 [gx, gx+tiles) × [gy, gy+tiles)
                        // 方块占据 [0, size) × [0, size)
                        // 与方块自身重叠 → 跳过
                        if (gx + tiles > 0 && gx < size && gy + tiles > 0 && gy < size) continue;

                        // 与已有 job 重叠 → 跳过
                        boolean conflict = false;
                        for (UnitJob j : jobs) {
                            int jt = (int)Math.ceil(j.recipe.unit.hitSize / Vars.tilesize);
                            int need = Math.max(tiles, jt);
                            if (Math.abs(j.offsetX - gx) < need && Math.abs(j.offsetY - gy) < need) {
                                conflict = true;
                                break;
                            }
                        }
                        if (!conflict) return new int[]{
                            gx, gy};
                    }
                }
            }
            return null;
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);

            table.row();
            table.label(() -> Core.bundle.get("ss-multi-assembler.select", "Select unit:")).left().padTop(6f).row();

            for (int i = 0; i < recipes.size; i++) {
                final int idx = i;
                Recipe r = recipes.get(i);
                table.button(r.unit.localizedName, () -> configure(idx))
                .size(140f, 45f).pad(3f).row();
            }

            table.row();
            table.label(() -> Core.bundle.get("ss-multi-assembler.queue", "Queue")
                + " (" + jobs.size + "/" + maxCount + "):").left().padTop(6f).row();

            for (int i = 0; i < jobs.size; i++) {
                final int idx = i;
                UnitJob j = jobs.get(i);
                table.table(t -> {
                        t.label(() -> j.recipe.unit.localizedName + "  "
                            + (int)(j.progress / j.recipe.craftTime * 100) + "%")
                        .left().width(160f);
                        t.button(Icon.cancel, () -> jobs.remove(idx)).size(32f).left();
                    }).left().row();
            }

            if (!payloadCounts.isEmpty()) {
                table.row();
                table.label(() -> Core.bundle.get("ss-multi-assembler.payloads", "Payloads:")).left().padTop(6f).row();
                for (ObjectMap.Entry<UnlockableContent, Integer> e : payloadCounts) {
                    table.table(t -> {
                            t.image(e.key.uiIcon).size(24f).padRight(4f);
                            t.label(() -> "x" + e.value).left();
                        }).left().row();
                }
            }
        }

        public boolean canPlace(UnitType unit, int gx, int gy) {
            int tiles = (int)Math.ceil(unit.hitSize / Vars.tilesize);
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

        @Override
        public void updateTile() {
            if (jobs.isEmpty()) return;

            float eff = efficiency * delta();

            for (int i = jobs.size - 1; i >= 0; i--) {
                UnitJob job = jobs.get(i);
                Recipe r = job.recipe;

                if (!canPlace(r.unit, job.offsetX, job.offsetY)) {
                    job.progress = 0f;
                    continue;
                }
                if (!hasMaterials(r)) continue;

                job.progress += eff;
                if (job.progress >= r.craftTime) {
                    consumeMaterials(r);

                    Unit u = r.unit.create(team);
                    u.set(
                        tile.worldx() + (job.offsetX + 0.5f) * Vars.tilesize,
                        tile.worldy() + (job.offsetY + 0.5f) * Vars.tilesize
                    );
                    u.rotation = rotation * 90f;
                    u.add();

                    jobs.remove(i);
                }
            }
        }

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

        @Override
        public void drawSelect() {
            super.drawSelect();
            for (UnitJob job : jobs) {
                int tiles = (int)Math.ceil(job.recipe.unit.hitSize / Vars.tilesize);
                boolean ok = canPlace(job.recipe.unit, job.offsetX, job.offsetY);
                Color c = ok ? Color.green : Color.red;

                Draw.color(c, 0.3f);
                Fill.rect(
                    tile.worldx() + (job.offsetX + tiles / 2f) * Vars.tilesize,
                    tile.worldy() + (job.offsetY + tiles / 2f) * Vars.tilesize,
                    tiles * Vars.tilesize, tiles * Vars.tilesize
                );
                Draw.color(c);
                Lines.stroke(1.2f);
                Lines.rect(
                    tile.worldx() + job.offsetX * Vars.tilesize,
                    tile.worldy() + job.offsetY * Vars.tilesize,
                    tiles * Vars.tilesize, tiles * Vars.tilesize
                );
            }
            Draw.reset();
        }

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
                boolean isUnit = e.key instanceof UnitType;
                write.bool(isUnit);
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
                boolean isUnit = read.bool();
                int id = read.s();
                int count = read.i();
                UnlockableContent c = Vars.content.getByID(
                    isUnit ? ContentType.unit : ContentType.block, id);
                if (c != null) payloadCounts.put(c, count);
            }
        }
    }
}