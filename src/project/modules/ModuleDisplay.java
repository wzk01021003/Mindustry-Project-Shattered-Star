package project.modules;

import arc.graphics.Color;
import arc.scene.ui.layout.Table;
import mindustry.gen.Unit;
import mindustry.ui.Bar;

/**
 * 模块系统的两处 UI：悬停信息面板、核心数据库的单位页。
 *
 * <p>分开是因为两者能拿到的信息不一样：数据库页只有 {@link ModularUnitType}
 * （类型级，所有同型单位共用），能说「这台单位有哪几个模块、各自多少血」；
 * 悬停面板手上有 {@link Unit}，能读到这一台<b>当下</b>的模块血量。
 * 前者回答「它由什么组成」，后者回答「它被打成什么样了」。
 *
 * <p>颜色一律走 {@link ModuleKind#color}，两处用同一套色，玩家在数据库里认清
 * 颜色之后，悬停面板不用再认第二遍。
 */
public final class ModuleDisplay{

    /** 报废模块的条色：暗红，和「还活着但很低」区分开 */
    private static final Color BROKEN = Color.valueOf("5c2a2a");
    /** 报废模块的名字色 */
    private static final Color BROKEN_TEXT = Color.valueOf("8a5a5a");
    /** 说明文字色 */
    private static final Color MUTED = Color.valueOf("9aa0a6");

    private ModuleDisplay(){}

    // ============================================================
    //  悬停面板
    // ============================================================

    /**
     * 在单位悬停面板里加一块模块状态。
     *
     * <p>没有模块的单位什么都不加——原版面板长什么样还是什么样，
     * 不会多出一行空标题。
     */
    public static void hover(Unit unit, Table table, ModularUnitType type){
        if(!type.modular()) return;

        ModuleState st = ModuleState.get(unit);
        if(st == null) return;

        int broken = 0;
        for(int i = 0; i < st.hp.length; i++){
            if(st.broken(i)) broken++;
        }

        table.row();
        Table box = new Table();
        box.left();

        int brokenCount = broken;
        box.table(head -> {
            head.left();
            head.add("[lightgray]模块").left().growX();
            // 全好时不写「0 处报废」，那是噪音；坏了才提示，玩家一眼能看到
            if(brokenCount > 0){
                head.add("[scarlet]" + brokenCount + " 处报废").right();
            }
        }).growX().padBottom(3f).row();

        for(int i = 0; i < st.hp.length; i++){
            box.add(bar(st, i)).growX().padBottom(2f).row();
        }

        table.add(box).growX().left().padTop(6f);
    }

    /** 一行：模块名 + 血条（条上带当前值） */
    private static Table bar(ModuleState st, int index){
        UnitModule def = st.defs[index];
        boolean broken = st.broken(index);

        Table row = new Table();
        row.left();

        row.add(def.name).left().width(44f)
           .color(broken ? BROKEN_TEXT : Color.white);

        // 条上的字和条色都从 st 里现读，不是建表时拍下来的快照——
        // 面板不保证因为血量变化而重建，写死的话会停在打开那一刻
        row.add(new Bar(
            () -> (int)st.hp[index] + "/" + (int)def.maxHp,
            () -> st.broken(index) ? BROKEN : def.kind.color,
            () -> st.ratio(index)
        )).height(16f).growX().padLeft(6f);

        return row;
    }

    // ============================================================
    //  数据库页
    // ============================================================

    /**
     * 在核心数据库的单位页里加一张模块表。
     *
     * <p>这里只有类型，没有实例，所以列的是「上限 / 易损 / 曲线」这三样静态值。
     * 曲线写成一句话而不是画图：数据库页宽度有限，四条折线并排会挤成一团，
     * 一句话反而读得完。
     */
    public static void database(Table table, ModularUnitType type){
        if(!type.modular()) return;

        table.row();
        table.table(box -> {
            box.left();
            box.add("[lightgray]模块受损").left().padBottom(5f).row();

            for(UnitModule def : type.modules){
                box.table(row -> {
                    row.left();
                    row.add(def.name).left().width(46f).color(def.kind.color);
                    row.add((int)def.maxHp + " 血").left().width(52f).color(MUTED);
                    row.add("易损 " + def.vulnerability).left().width(80f).color(MUTED);
                    row.add(def.effectiveCurve().label).left().color(MUTED);
                }).growX().padBottom(3f).row();
            }

            box.add("[darkgray]默认每发命中把子弹伤害的 "
                + Math.round(ModuleDamage.DEFAULT.amount * 100f)
                + "% 同时结算到每个模块").left().padTop(5f);
        }).growX().left().padTop(12f);
    }
}
