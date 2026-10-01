package project.content.units;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Leg;
import mindustry.gen.Legsc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

public class GlowLegsUnitType extends UnitType {

    public GlowData glow = new GlowData();

    public GlowLegsUnitType(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        glow.load(name);
    }

    @Override
    public <T extends Unit & Legsc> void drawLegs(T unit) {
        super.drawLegs(unit);
        glow.draw(this, unit);
    }

    public static class GlowData {
        public TextureRegion leg, legBase, foot, joint, baseJoint, legCell, footCell;

        public Color color = Color.white.cpy();
        public Blending blending = Blending.additive;
        public boolean teamColor = false;
        public boolean pulse = true;
        public float pulseScl = 3f, pulseMag = 0.35f;
        public float alpha = 1f;
        public boolean cellEnabled = false;

        public void load(String name) {
            leg       = Core.atlas.find(name + "-leg-glow");
            legBase   = Core.atlas.find(name + "-leg-base-glow");
            foot      = Core.atlas.find(name + "-foot-glow");
            joint     = Core.atlas.find(name + "-joint-glow");
            baseJoint = Core.atlas.find(name + "-joint-base-glow");
            legCell   = Core.atlas.find(name + "-leg-cell");
            footCell  = Core.atlas.find(name + "-foot-cell");
        }

        public <T extends Unit & Legsc> void draw(UnitType type, T unit) {
            Leg[] legs = unit.legs();
            if (legs == null || legs.length == 0) return;
            if (!leg.found() && !legBase.found() && !foot.found()
                && !joint.found() && !baseJoint.found()) return;

            float rotation = unit.baseRotation();
            float pulseVal = pulse ? Mathf.absin(Time.time, pulseScl, pulseMag) : 0f;
            Color c = teamColor ? unit.team.color : color;
            float a = Mathf.clamp(alpha + pulseVal);

            Draw.blend(blending);
            Draw.color(c, a);
            Draw.mixcol(c, a);

            for (int j = legs.length - 1; j >= 0; j--) {
                int i = (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2);
                Leg legObj = legs[i];
                boolean flip = i >= legs.length / 2f;
                int flips = Mathf.sign(flip);

                Vec2 position = unit.legOffset(Tmp.v4, i);
                position.add(unit);

                Tmp.v1.set(legObj.base).sub(legObj.joint).inv().setLength(type.legExtension);

                if (foot.found()) {
                    Draw.rect(foot, legObj.base.x, legObj.base.y,
                        position.angleTo(legObj.base));
                }

                if (type.legBaseUnder) {
                    if (legBase.found()) {
                        Lines.stroke(type.legBaseRegion.height * type.legRegion.scl() * flips);
                        Lines.line(legBase,
                            legObj.joint.x + Tmp.v1.x, legObj.joint.y + Tmp.v1.y,
                            legObj.base.x, legObj.base.y, false);
                    }
                    if (leg.found()) {
                        Lines.stroke(type.legRegion.height * type.legRegion.scl() * flips);
                        Lines.line(leg,
                            position.x, position.y,
                            legObj.joint.x, legObj.joint.y, false);
                    }
                } else {
                    if (leg.found()) {
                        Lines.stroke(type.legRegion.height * type.legRegion.scl() * flips);
                        Lines.line(leg,
                            position.x, position.y,
                            legObj.joint.x, legObj.joint.y, false);
                    }
                    if (legBase.found()) {
                        Lines.stroke(type.legBaseRegion.height * type.legRegion.scl() * flips);
                        Lines.line(legBase,
                            legObj.joint.x + Tmp.v1.x, legObj.joint.y + Tmp.v1.y,
                            legObj.base.x, legObj.base.y, false);
                    }
                }

                if (joint.found()) {
                    Draw.rect(joint, legObj.joint.x, legObj.joint.y);
                }
            }

            if (baseJoint.found()) {
                for (int j = legs.length - 1; j >= 0; j--) {
                    Vec2 position = unit.legOffset(Tmp.v4,
                        (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2));
                    position.add(unit);
                    Draw.rect(baseJoint, position.x, position.y, rotation);
                }
            }

            if (cellEnabled && legCell.found()) {
                Draw.blend(Blending.normal);
                Draw.mixcol();
                Draw.color(type.cellColor(unit));

                for (int j = legs.length - 1; j >= 0; j--) {
                    int i = (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2);
                    Leg legObj = legs[i];
                    boolean flip = i >= legs.length / 2f;
                    int flips = Mathf.sign(flip);

                    Vec2 position = unit.legOffset(Tmp.v4, i);
                    position.add(unit);

                    Lines.stroke(type.legRegion.height * type.legRegion.scl() * flips);
                    Lines.line(legCell,
                        position.x, position.y,
                        legObj.joint.x, legObj.joint.y, false);

                    if (footCell.found()) {
                        Draw.rect(footCell, legObj.base.x, legObj.base.y,
                            position.angleTo(legObj.base));
                    }
                }
            }

            Draw.blend();
            Draw.mixcol();
            Draw.color();
            Draw.reset();
        }
    }
}