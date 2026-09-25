package project.blocks;

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
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.type.PayloadStack;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

public class MultiAssembler extends GenericCrafter {

    /** 最多同时排队的单位数量 */
    public int maxCount = 8;
    /** 区域计算时额外留白（格） */
    public float padding = 3f;
    /** 区域边长上限 */
    public int maxAreaSize = 24;

    /** 配方列表，由 JSON 填充 */
    public UnitRecipe[] recipe;
    public Seq<UnitRecipe> recipes = new Seq<>();

    public MultiAssembler(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = true;
        saveConfig = true;
        hasItems = true;
        hasLiquids = true;
        hasPower = true;

        // 点击 UI 添加单位到队列
        config(Integer.class, (MultiAssemblerBuild build, Integer index) -> {
                if (index >= 0 && index < recipes.size) {
                    build.addJob(recipes.get(index));
                }
            });
    }

    @Override
    public void init() {
        super.init();
        // 解析 JSON 里的配方
        if (recipe != null) {
            for (UnitRecipe r : recipe) {
                r.parsePayloads();
                recipes.add(r);
            }
            recipe = null;
        }
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.output, table -> {
                table.clearChildren();
                table.left();
                for (UnitRecipe r : recipes) {
                    table.table(t -> {
                            t.left();
                            t.add("[accent]" + r.unit.localizedName + "[]").padRight(8);
                            t.add("[lightgray]时间: " + (r.craftTime / 60f) + "s[]").padRight(8);
                            t.row();
                            t.add("[lightgray]需求: []");
                            for (ItemStack s : r.inputItems) t.add(s.item.uiIcon).size(24f).padRight(2);
                            for (LiquidStack s : r.inputLiquids) t.add(s.liquid.uiIcon).size(24f).padRight(2);
                            for (PayloadStack s : r.cachedInputPayloads) t.add(s.item.uiIcon).size(24f).padRight(2);
                        }).left().row();
                }
            });
    }

    /** 单个单位的配方 */
    public static class UnitRecipe {
        public UnitType unit;
        public float craftTime = 60f;
        public ItemStack[] inputItems = {};
        public LiquidStack[] inputLiquids = {};
        public String[] inputPayloads = {};
        public transient PayloadStack[] cachedInputPayloads = {};

        public UnitRecipe() {}

        public void parsePayloads() {
            Seq<PayloadStack> list = new Seq<>();
            for (String s : inputPayloads) {
                if (s == null || s.isEmpty()) continue;
                String[] parts = s.split("/");
                if (parts.length != 2) continue;
                UnlockableContent content = Vars.content.getByName(mindustry.ctype.ContentType.block, parts[0]);
                if (content == null) content = Vars.content.getByName(mindustry.ctype.ContentType.unit, parts[0]);
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
        public UnitRecipe recipe;
        public float progress;
        public int offsetX, offsetY;

        public UnitJob(UnitRecipe recipe, int offsetX, int offsetY) {
            this.recipe = recipe;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    public class MultiAssemblerBuild extends GenericCrafterBuild {
        public Seq<UnitJob> jobs = new Seq<>();

        /** 往队列里加一个单位，会自动分配落点 */
        public void addJob(UnitRecipe recipe) {
            if (jobs.size >= maxCount) return;
            int unitTiles = Mathf.ceil(recipe.unit.hitSize / Vars.tilesize);
            int[] slot = findFreeSlot(unitTiles);
            if (slot == null) return;
            jobs.add(new UnitJob(recipe, slot[0], slot[1]));
        }

        /** 找一个不跟其他 job 冲突的落点 */
        public int[] findFreeSlot(int unitTiles) {
            int areaSize = Math.max(5, maxAreaSize / 2);
            int half = areaSize / 2;
            for (int gy = -half; gy < areaSize - half; gy++) {
                for (int gx = -half; gx < areaSize - half; gx++) {
                    boolean conflict = false;
                    for (UnitJob j : jobs) {
                        int otherTiles = Mathf.ceil(j.recipe.unit.hitSize / Vars.tilesize);
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

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);

            table.row();
            table.label(() -> "选择生产单位:").left().padTop(6f).row();

            for (int i = 0; i < recipes.size; i++) {
                final int index = i;
                UnitRecipe r = recipes.get(i);
                table.button(r.unit.localizedName, () -> configure(index))
                .size(120f, 45f).pad(3f).row();
            }

            table.row();
            table.label(() -> "生产队列 (" + jobs.size + "/" + maxCount + "):").left().padTop(6f).row();

            for (int i = 0; i < jobs.size; i++) {
                UnitJob job = jobs.get(i);
                table.label(() -> job.recipe.unit.localizedName + "  "
                    + (int)(job.progress / job.recipe.craftTime * 100) + "%").left().row();
            }
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
            // 注意：这里没有 super.updateTile()，完全重写生产逻辑
            if (jobs.isEmpty()) return;

            float eff = efficiency * delta();

            for (int i = jobs.size - 1; i >= 0; i--) {
                UnitJob job = jobs.get(i);

                if (!canPlaceAt(job.recipe.unit, job.offsetX, job.offsetY)) {
                    job.progress = 0f;
                    continue;
                }

                if (!hasMaterials(job.recipe)) {
                    continue;
                }

                job.progress += eff;
                if (job.progress >= job.recipe.craftTime) {
                    consumeMaterials(job.recipe);

                    Unit u = job.recipe.unit.create(team);
                    u.set(
                        tile.x * Vars.tilesize + (job.offsetX + 0.5f) * Vars.tilesize,
                        tile.y * Vars.tilesize + (job.offsetY + 0.5f) * Vars.tilesize
                    );
                    u.rotation = rotation * 90f;
                    u.add();

                    jobs.remove(i);
                }
            }
        }

        /** 检查材料是否足够 */
        public boolean hasMaterials(UnitRecipe recipe) {
            for (ItemStack stack : recipe.inputItems) {
                if (items.get(stack.item) < stack.amount) return false;
            }
            for (LiquidStack stack : recipe.inputLiquids) {
                if (liquids.get(stack.liquid) < stack.amount) return false;
            }
            for (PayloadStack stack : recipe.cachedInputPayloads) {
                if (getPayloads() == null || getPayloads().get(stack.item) < stack.amount) return false;
            }
            return true;
        }

        /** 消耗材料 */
        public void consumeMaterials(UnitRecipe recipe) {
            for (ItemStack stack : recipe.inputItems) items.remove(stack.item, stack.amount);
            for (LiquidStack stack : recipe.inputLiquids) liquids.remove(stack.liquid, stack.amount);
            if (getPayloads() != null) {
                for (PayloadStack stack : recipe.cachedInputPayloads) {
                    getPayloads().remove(stack.item, stack.amount);
                }
            }
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            if (jobs.isEmpty()) return;

            for (UnitJob job : jobs) {
                int unitTiles = Mathf.ceil(job.recipe.unit.hitSize / Vars.tilesize);
                boolean ok = canPlaceAt(job.recipe.unit, job.offsetX, job.offsetY);
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
                    UnitJob job = new UnitJob(recipes.get(idx), ox, oy);
                    job.progress = prog;
                    jobs.add(job);
                }
            }
        }
    }
}