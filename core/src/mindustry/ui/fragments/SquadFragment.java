package mindustry.ui.fragments;

import arc.*;
import arc.graphics.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.util.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.Tooltip.*;
import arc.scene.ui.layout.*;
import mindustry.ai.*;
import mindustry.core.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

/**
 * 编队命令页内容构建器（改端）。
 *
 * 不自行挂载到 HUD；由 PlacementFragment 在按住编队模式键（K）时，
 * 像原版指挥模式（Shift）替换建造分类页那样，把本内容填进 squadTable 显示。
 * 这样交互与视觉都与原版指挥模式一致，不再有悬浮面板贴脸。
 */
public class SquadFragment{
    /** 预设保存模式：开启后点数字键即把当前编队存进该预设（移动端没有右键可用）。 */
    private boolean saveMode = false;

    /** 成员按钮缓存的编队 id 序列：只在序列变化时重建按钮，避免拖动过程中被打断。 */
    private int[] cachedMemberIds = new int[0];

    /**
     * 移动端独立面板：手机没有建造分类栏可替换，仍用一个底部条显示编队控制。
     * 桌面端由 PlacementFragment 的 squadTable 替换建造页，这里不显示。
     */
    public void build(Group parent){
        parent.fill(cont -> {
            cont.visible(() -> mobile && (SquadManager.squad.size > 0 || control.input.squadMode)
                && state.isGame() && !Core.settings.getBool("showperformance"));
            cont.touchable = Touchable.childrenOnly;
            cont.bottom().center();
            cont.table(Styles.black6, this::build).padBottom(4f);
        });
    }

    /** 把编队控制内容填进指定 table（由 PlacementFragment 的 squadTable 调用）。 */
    public void build(Table bar){
        bar.defaults().pad(2f);
        bar.left();

        //标题
        bar.add("@squad.panel").style(Styles.outlineLabel).color(Pal.accent).padLeft(6f);
        bar.image().color(Pal.accent).growX().height(2f).padLeft(6f).padRight(6f).row();

        //---- 成员图标横排：左键移除，右键归队，拖动改变顺序 ----
        bar.table(members -> {
            members.left();
            members.update(() -> {
                //收集当前编队 id 序列
                int size = SquadManager.squad.size;
                int[] ids = new int[size];
                for(int i = 0; i < size; i++) ids[i] = SquadManager.squad.get(i);

                boolean changed = ids.length != cachedMemberIds.length;
                if(!changed){
                    for(int i = 0; i < ids.length; i++){
                        if(ids[i] != cachedMemberIds[i]){ changed = true; break; }
                    }
                }
                if(!changed) return; //序列没变，保留按钮（否则会打断正在进行的拖动）
                cachedMemberIds = ids;

                members.clearChildren();
                if(size == 0){
                    members.label(() -> "@squad.panel.empty").style(Styles.outlineLabel)
                        .color(Color.lightGray).padLeft(4f).padRight(6f);
                    return;
                }
                for(int i = 0; i < size; i++){
                    int slot = i;
                    Unit u = Groups.unit.getByID(ids[i]);
                    if(u == null || u.dead()) continue;

                    members.button(Icon.units, Styles.clearNonei, () -> SquadManager.removeSquad(u))
                        .size(30f).padRight(2f).with(b -> {
                            b.addListener(new HandCursorListener());
                            b.clicked(KeyCode.mouseRight, () -> control.input.returnSquadUnit(slot));
                            //拖动排序：拖到哪个槽位就与那个槽位交换
                            DragListener drag = new DragListener(){
                                @Override
                                public void dragStop(InputEvent event, float x, float y, int pointer){
                                    //用舞台 x 换算成 members 内的局部 x，除以单格宽度得到目标槽位
                                    Vec2 local = members.stageToLocalCoordinates(Tmp.v1.set(event.stageX, event.stageY));
                                    int target = Mathf.clamp((int)(local.x / 32f), 0, SquadManager.squad.size - 1);
                                    if(target != slot) control.input.moveSlotTo(slot, target);
                                }
                            };
                            drag.setTapSquareSize(8f);
                            b.addListener(drag);
                            b.addListener(Tooltips.getInstance().create(
                                (slot + 1) + ". " + u.type.localizedName + "\n" + Core.bundle.get("squad.panel.removehint"), false));
                        });
                }
            });
        }).padLeft(6f).row();

        bar.image().color(Pal.gray).growX().height(2f).pad(4f).row();

        //---- 命令图标按钮区 ----
        bar.table(buttons -> {
            buttons.left();
            buttons.defaults().size(38f).pad(2f);

            buttons.button(Icon.grid, Styles.clearNoneTogglei, () -> control.input.cycleShape())
                .tooltip("@squad.panel.shape.tip");

            buttons.button(Icon.commandAttack, Styles.clearNoneTogglei, () -> control.input.toggleAutoAttack())
                .checked(b -> SquadSettings.autoAttack())
                .tooltip("@squad.panel.autoattack.tip");

            buttons.button(Icon.units, Styles.clearNoneTogglei, () -> control.input.toggleFollow())
                .checked(b -> SquadManager.follow)
                .tooltip("@squad.panel.follow.tip");

            buttons.button(Icon.move, Styles.clearNonei, () -> {
                for(int i = 0; i < SquadManager.squad.size; i++) control.input.returnSquadUnit(i);
            }).disabled(b -> SquadManager.follow).tooltip("@squad.panel.return");

            buttons.button(Icon.download, Styles.clearNoneTogglei, () -> {
                control.input.placingDropPoint = !control.input.placingDropPoint;
            }).checked(b -> control.input.placingDropPoint).tooltip("@squad.panel.droppoint.tip");

            buttons.button(Icon.cancel, Styles.clearNonei, () -> control.input.clearDropPoint())
                .disabled(b -> SquadManager.dropPoint == null).tooltip("@squad.panel.droppoint.clear");

            buttons.button("-", () -> SquadSettings.setRadius(Math.max(SquadSettings.RADIUS_MIN, (int)SquadSettings.radius() - 5)))
                .size(28f).tooltip("@squad.panel.radius");
            buttons.button("+", () -> SquadSettings.setRadius(Math.min(SquadSettings.RADIUS_MAX, (int)SquadSettings.radius() + 5)))
                .size(28f).tooltip("@squad.panel.radius");
        }).padLeft(6f).row();

        //---- 第二行：预设 1~5 + 保存模式 + 解散 ----
        bar.table(buttons -> {
            buttons.left();
            buttons.defaults().pad(2f);
            buttons.add("@squad.panel.preset").style(Styles.outlineLabel).left().padLeft(6f).padRight(4f);

            for(int i = 0; i < SquadSettings.presetCount; i++){
                int index = i;
                buttons.button(b -> {
                    b.clicked(KeyCode.mouseLeft, () -> {
                        if(saveMode){ control.input.savePreset(index); saveMode = false; }
                        else control.input.applyPreset(index);
                    });
                    b.clicked(KeyCode.mouseRight, () -> { control.input.savePreset(index); saveMode = false; });
                    b.label(() -> (index + 1) + "").style(Styles.outlineLabel);
                }, () -> {}).size(28f).tooltip("@squad.preset.hint");
            }

            buttons.button("@squad.panel.save", Styles.clearTogglet, () -> saveMode = !saveMode)
                .width(46f).checked(b -> saveMode).tooltip("@squad.preset.savemode");

            buttons.button(Icon.trash, Styles.clearNonei, () -> Call.squadClear(player)).size(34f).tooltip("@squad.disband");
        }).row();
    }
}
