package project.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.content.Fx;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.io.TypeIO;
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

    /** 是否根据配方自动调整容量（物品/液体/载荷），默认开启 */
    public boolean autoCapacity = true;

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
        commandable = true;

        config(Integer.class, (Building b, Integer index) -> {
                if (!(b instanceof MultiAssemblerBuild)) return;
                MultiAssemblerBuild build = (MultiAssemblerBuild) b;
                if (index == null) return;
                if (index == -1) {
                    build.loopMode = !build.loopMode;
                    return;
                }
                if (index < 0 || index >= recipes.size) return;
                build.addJob(recipes.get(index));
            });

        config(UnitCommand.class, (Building b, UnitCommand cmd) -> {
                if (b instanceof MultiAssemblerBuild) {
                    ((MultiAssemblerBuild) b).command = cmd;
                }
            });

        config(Point2.class, (Building b, Point2 p) -> {
                if (!(b instanceof MultiAssemblerBuild)) return;
                MultiAssemblerBuild build = (MultiAssemblerBuild) b;
                if (p.x == -1 && p.y == -1) {
                    build.commandPos = null;
                } else {
                    build.commandPos = new Vec2(p.x, p.y);
                }
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

        // ============================================================
        //  自动容量：取所有配方中最大的单个材料需求，乘以 2
        // ============================================================
        if (autoCapacity) {
            int maxItem = 0;
            float maxLiquid = 0f;
            int maxPayload = 0;

            for (Recipe r : recipes) {
                for (ItemStack s : r.inputItems) {
                    maxItem = Math.max(maxItem, s.amount);
                }
                for (LiquidStack s : r.inputLiquids) {
                    maxLiquid = Math.max(maxLiquid, s.amount);
                }
                for (PayloadStack s : r.cachedInputPayloads) {
                    maxPayload = Math.max(maxPayload, s.amount);
                }
            }

            // 用 Math.max 保留 JSON 里显式设置的更大值
            if (maxItem > 0) {
                itemCapacity = Math.max(itemCapacity, maxItem * 2);
            }
            if (maxLiquid > 0f) {
                liquidCapacity = Math.max(liquidCapacity, maxLiquid * 2f);
            }
            if (maxPayload > 0) {
                payloadCapacity = Math.max(payloadCapacity, maxPayload * 2);
            }
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

        public boolean loopMode = false;
        public Vec2 commandPos;
        public UnitCommand command;

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

        @Override
        public Vec2 getCommandPosition() {
            return commandPos;
        }

        @Override
        public void onCommand(Vec2 target) {
            if (target == null) {
                configure(new Point2(-1, -1));
            } else {
                configure(new Point2((int) target.x, (int) target.y));
            }
        }

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
                jobs.remove(newJob);
                reassignSlots();
                if (!Vars.headless) Vars.ui.showInfoToast(
                    Core.bundle.get("ss-multi-assembler.no-space", "No free space"), 1.5f);
            }
        }

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

        public int[] findFreeSlot(UnitType unit, UnitJob exclude) {
            int tiles = (int) Math.ceil(unit.hitSize / Vars.tilesize);
            int maxR = Math.max(areaRadius, tiles + 2) + size;

            Seq<int[]> candidates = new Seq<>();
            for (int gy = -maxR; gy <= maxR; gy++) {
                for (int gx = -maxR; gx <= maxR; gx++) {
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
                if (!canPlace(unit, c[0], c[1])) continue;
                boolean conflict = false;
                for (UnitJob j : jobs) {
                    if (j == exclude) continue;
                    if (!j.hasSlot()) continue;
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

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);

            Table content = new Table();
            content.left().top();
            table.add(content).growX().left().padTop(6f);

            final int[] last = {
                -1, -1, -1};
            content.update(() -> {
                    if (last[0] != jobs.size || last[1] != payloadCounts.size
                        || last[2] != (loopMode ? 1 : 0)) {
                        last[0] = jobs.size;
                        last[1] = payloadCounts.size;
                        last[2] = loopMode ? 1 : 0;
                        rebuildContent(content);
                    }
                });

            rebuildContent(content);
            last[0] = jobs.size;
            last[1] = payloadCounts.size;
            last[2] = loopMode ? 1 : 0;
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

            content.table(Styles.grayPanel, t -> {
                    t.left();
                    t.button(Icon.refresh, Styles.clearTogglei, 36f, () -> configure(-1))
                    .update(b -> b.setChecked(loopMode))
                    .tooltip(Core.bundle.get("ss-multi-assembler.loop.tip",
                            "Loop mode: finished jobs re-enter the queue"))
                    .left().pad(4f);
                    t.label(() -> Core.bundle.get("ss-multi-assembler.loop", "Loop"))
                    .left().padLeft(6f);
                }).growX().pad(2f).left().row();

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

            buildCommandUI(content);
        }

        private void buildCommandUI(Table parent) {
            UnitType unitType = getCommandableUnitType();
            if (unitType == null || unitType.commands.size == 0) return;

            parent.row();
            Table commands = new Table();
            commands.top().left();
            commands.background(Styles.black6);

            ButtonGroup<ImageButton> group = new ButtonGroup<>();
            group.setMinCheckCount(0);

            int cols = 3;
            for (int i = 0; i < unitType.commands.size; i++) {
                UnitCommand cmd = unitType.commands.get(i);
                ImageButton button = commands.button(cmd.getIcon(), Styles.clearNoneTogglei, 40f,
                    () -> configure(cmd))
                .tooltip(cmd.localized()).group(group).get();
                button.update(() -> button.setChecked(
                        command == cmd || (command == null && unitType.defaultCommand == cmd)));
                if (i % cols == cols - 1) commands.row();
            }

            parent.add(commands).fillX().left().padTop(4f);
        }

        private UnitType getCommandableUnitType() {
            for (Recipe r : recipes) {
                if (r.unit != null && r.unit.commands.size > 0) return r.unit;
            }
            return null;
        }

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

        @Override
        public void updateTile() {
            validateJobs();
            if (jobs.isEmpty()) return;

            boolean slotFreed = false;

            for (int i = jobs.size - 1; i >= 0; i--) {
                UnitJob job = jobs.get(i);
                Recipe r = job.recipe;

                if (!job.hasSlot()) continue;
                if (!canPlace(r.unit, job.offsetX, job.offsetY)) continue;
                if (!hasMaterials(r)) continue;

                job.progress += delta() * efficiency / r.craftTime;

                if (job.progress >= 1f) {
                    if (!hasMaterials(r)) {
                        job.progress = 0.999f;
                        continue;
                    }
                    consumeMaterials(r);
                    spawnUnit(job);
                    jobs.remove(i);

                    if (loopMode) {
                        jobs.add(new UnitJob(r, Integer.MIN_VALUE, Integer.MIN_VALUE));
                    }
                    slotFreed = true;
                }
            }

            if (slotFreed) reassignSlots();
        }

        private void spawnUnit(UnitJob job) {
            Recipe r = job.recipe;
            int tiles = (int) Math.ceil(r.unit.hitSize / Vars.tilesize);
            float cx = tile.worldx() + (job.offsetX + (tiles - 1) / 2f) * Vars.tilesize;
            float cy = tile.worldy() + (job.offsetY + (tiles - 1) / 2f) * Vars.tilesize;

            Unit u = r.unit.create(team);
            u.set(cx, cy);
            u.rotation = rotation * 90f;
            u.add();

            if (u.isCommandable()) {
                if (commandPos != null) u.command().commandPosition(commandPos);
                UnitCommand effective = command != null ? command : u.type.defaultCommand;
                if (effective != null) u.command().command(effective);
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
        public void draw() {
            super.draw();
            for (UnitJob job : jobs) {
                if (job.hasSlot()) drawCraftingVisual(job);
            }
        }

        private void drawCraftingVisual(UnitJob job) {
            UnitType unit = job.recipe.unit;
            int tiles = (int) Math.ceil(unit.hitSize / Vars.tilesize);
            float cx = tile.worldx() + (job.offsetX + (tiles - 1) / 2f) * Vars.tilesize;
            float cy = tile.worldy() + (job.offsetY + (tiles - 1) / 2f) * Vars.tilesize;
            float progress = Mathf.clamp(job.progress);
            boolean hasMats = hasMaterials(job.recipe);

            float ghostScale = Math.max(0f, 1f - progress);
            float ghostAlpha = Math.max(0f, 0.75f * (1f - progress));
            if (ghostScale > 0.01f) {
                float oldScl = Draw.scl;
                Draw.scl *= ghostScale;
                Draw.z(Layer.blockOver + 1f);
                Draw.color(Pal.accent, ghostAlpha);
                Draw.rect(unit.fullIcon, cx, cy, Time.time * 2f);
                Draw.scl = oldScl;
                Draw.color();
            }

            if (progress > 0.01f) {
                Draw.z(Layer.blockOver + 2f);
                Draw.color(Color.white, progress);
                Draw.rect(unit.fullIcon, cx, cy, rotation * 90f - 90f);
                Draw.color();
            }

            float topY = cy + tiles * Vars.tilesize / 2f + 10f;

            Seq<Object> mats = new Seq<>();
            for (ItemStack s : job.recipe.inputItems) mats.add(s.item);
            for (LiquidStack s : job.recipe.inputLiquids) mats.add(s.liquid);
            for (PayloadStack s : job.recipe.cachedInputPayloads) mats.add(s.item);

            if (mats.size > 0) {
                float iconSize = 5f;
                float gap = 2f;
                float totalW = mats.size * iconSize + (mats.size - 1) * gap;
                float startX = cx - totalW / 2f + iconSize / 2f;
                Draw.z(Layer.overlayUI);
                for (int i = 0; i < mats.size; i++) {
                    Object o = mats.get(i);
                    Color c = Color.white;
                    if (o instanceof Item) c = ((Item) o).color;
                    else if (o instanceof Liquid) c = ((Liquid) o).color;
                    Draw.color(c);
                    Fill.square(startX + i * (iconSize + gap), topY, iconSize / 2f, 45f);
                }
                Draw.color();
            }

            float barW = Math.max(tiles * Vars.tilesize, 12f);
            float barH = 3f;
            float barY = topY + 6f;

            Draw.z(Layer.overlayUI);
            Draw.color(Color.darkGray);
            Fill.rect(cx, barY, barW, barH);
            Draw.color(hasMats ? Pal.accent : Pal.remove);
            Fill.rect(cx - barW / 2f + barW * progress / 2f, barY, barW * progress, barH);
            Draw.color();

            Draw.z(Layer.block);
        }

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

        @Override
        public byte version() {
            return 1;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.bool(loopMode);

            write.i(jobs.size);
            for (UnitJob j : jobs) {
                write.i(recipes.indexOf(j.recipe, true));
                write.f(j.progress);
            }

            write.i(payloadCounts.size);
            for (ObjectMap.Entry<UnlockableContent, Integer> e : payloadCounts) {
                write.b(e.key.getContentType().ordinal());
                write.s(e.key.id);
                write.i(e.value);
            }

            TypeIO.writeVecNullable(write, commandPos);
            TypeIO.writeCommand(write, command);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);

            loopMode = revision >= 1 && read.bool();

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

            if (revision >= 1) {
                commandPos = TypeIO.readVecNullable(read);
                command = TypeIO.readCommand(read);
            } else {
                commandPos = null;
                command = null;
            }

            reassignSlots();
        }
    }
}