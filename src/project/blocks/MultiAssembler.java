package project.blocks;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.type.UnitType;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.world.meta.BlockStatus;

/** 每个单位的完整材料需求（物品 + 液体 + 载荷） */
class UnitRecipe {
    UnitType unit;
    float time;
    ItemStack[] items = {};
    LiquidStack[] liquids = {};
    /** 载荷需求：把载荷写进 items 数组即可，格式为 "单位名/数量" 或 "方块名/数量" */
    ItemStack[] payloads = {};

    UnitRecipe(UnitType unit, float time, ItemStack[] items, LiquidStack[] liquids, ItemStack[] payloads) {
        this.unit = unit;
        this.time = time;
        this.items = items;
        this.liquids = liquids;
        this.payloads = payloads;
    }
}

public class AdvancedUnitFactory extends UnitFactory {

    /** 允许选择的最大数量 */
    public int maxCount = 8;
    /** 每个单位的配方表 */
    public Seq<UnitRecipe> recipes = new Seq<>();

    public AdvancedUnitFactory(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = true;
        saveConfig = true;
        hasItems = true;
        hasLiquids = true;
        hasPower = true;
    }

    /** 便捷方法：往 recipes 里添加一个配方 */
    public void addRecipe(UnitType unit, float time, ItemStack[] items, LiquidStack[] liquids, ItemStack[] payloads) {
        recipes.add(new UnitRecipe(unit, time, items, liquids, payloads));
    }

    public class AdvancedUnitFactoryBuild extends UnitFactoryBuild {
        /** 已选的生产队列：单位 -> 数量 */
        public arc.struct.OrderedMap<UnitType, Integer> queue = new arc.struct.OrderedMap<>();
        /** 当前正在生产的单位 */
        public UnitType currentUnit;
        /** 当前生产进度 */
        public float progress;
        /** 当前配方的总时间 */
        public float craftTime;

        @Override
        public void created() {
            super.created();
        }

        /** 找到某个单位的配方 */
        public UnitRecipe recipeFor(UnitType unit) {
            for (UnitRecipe r : recipes) {
                if (r.unit == unit) return r;
            }
            return null;
        }

        /** 累计整个队列的材料需求 */
        public void computeTotalCost(ItemStack[] outItems, LiquidStack[] outLiquids) {
            // 具体累加逻辑看 UI 需要，这里只留接口
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);

            table.row();
            table.label(() -> "生产队列:").left().padTop(6f).row();

            // 每个单位一行：名称 + 加减按钮 + 显示已选数量
            for (UnitRecipe r : recipes) {
                UnitType u = r.unit;
                int count = queue.get(u, 0);

                table.table(row -> {
                    row.label(() -> u.localizedName).left().width(120f);
                    row.button("-", () -> {
                        int c = queue.get(u, 0);
                        if (c > 1) queue.put(u, c - 1);
                        else queue.remove(u);
                    }).size(40f, 40f);
                    row.label(() -> "" + queue.get(u, 0)).width(40f);
                    row.button("+", () -> {
                        int c = queue.get(u, 0);
                        if (c < maxCount) queue.put(u, c + 1);
                    }).size(40f, 40f);
                }).row();
            }

            // 显示总材料需求
            table.row();
            table.label(() -> "总材料需求：").left().padTop(6f).row();
            table.table(t -> {
                for (UnitRecipe r : recipes) {
                    int count = queue.get(r.unit, 0);
                    if (count <= 0) continue;

                    for (ItemStack s : r.items) {
                        int total = s.amount * count;
                        t.image(s.item.uiIcon).size(24f).padRight(4f);
                        t.label(() -> "" + total).left().padRight(12f);
                    }
                    for (LiquidStack s : r.liquids) {
                        float total = s.amount * count;
                        t.image(s.liquid.uiIcon).size(24f).padRight(4f);
                        t.label(() -> String.format("%.1f", total)).left().padRight(12f);
                    }
                    for (ItemStack s : r.payloads) {
                        int total = s.amount * count;
                        t.image(s.item.uiIcon).size(24f).padRight(4f);
                        t.label(() -> "" + total).left().padRight(12f);
                    }
                }
            }).row();

            // 清空队列按钮
            table.row();
            table.button("清空队列", () -> queue.clear()).size(120f, 40f);
        }

        @Override
        public void updateTile() {
            if (queue.isEmpty()) {
                currentUnit = null;
                return;
            }

            // 取队首单位作为当前生产目标
            currentUnit = queue.orderedKeys().first();
            UnitRecipe recipe = recipeFor(currentUnit);
            if (recipe == null) {
                queue.remove(currentUnit);
                return;
            }
            craftTime = recipe.time;

            // 检查材料
            boolean hasMaterials = true;
            for (ItemStack s : recipe.items) {
                if (items == null || items.get(s.item) < s.amount) { hasMaterials = false; break; }
            }
            if (hasMaterials) {
                for (LiquidStack s : recipe.liquids) {
                    if (liquids == null || liquids.get(s.liquid) < s.amount) { hasMaterials = false; break; }
                }
            }

            if (hasMaterials && efficiency > 0f) {
                progress += delta();
                if (progress >= craftTime) {
                    progress = 0f;

                    // 消耗材料
                    for (ItemStack s : recipe.items) items.remove(s.item, s.amount);
                    for (LiquidStack s : recipe.liquids) liquids.remove(s.liquid, s.amount);

                    // 生产单位
                    Unit u = currentUnit.create(team);
                    u.set(x + Mathf.range(8f), y + Mathf.range(8f));
                    u.rotation = rotation * 90f;
                    u.add();

                    // 数量减一
                    int c = queue.get(currentUnit);
                    if (c <= 1) queue.remove(currentUnit);
                    else queue.put(currentUnit, c - 1);
                }
            } else {
                progress = 0f;
            }
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            // 预览正在生产的单位
            if (currentUnit != null) {
                Draw.color(Color.green);
                Lines.stroke(1.5f);
                Lines.circle(x, y, currentUnit.hitSize);
            }
            Draw.reset();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(queue.size);
            for (var entry : queue) {
                write.s(entry.key.id);
                write.i(entry.value);
            }
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int n = read.i();
            queue.clear();
            for (int i = 0; i < n; i++) {
                short uid = read.s();
                int count = read.i();
                UnitType u = Vars.content.unit(uid);
                if (u != null) queue.put(u, count);
            }
        }
    }
}