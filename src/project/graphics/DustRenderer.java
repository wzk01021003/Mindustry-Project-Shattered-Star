package project.graphics;

import arc.Events;
import arc.util.Time;
import arc.graphics.g2d.Draw;
import arc.struct.IntMap;
import arc.struct.Seq;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Layer;

/**
 * 世界空间扬尘渲染器。
 *
 * <p>作用上对标 {@link DistortionRenderer}：自己持有一组世界坐标的尘团、自己算进度、
 * 自己决定画在哪一层，不走 Mindustry 的 Effect 粒子系统。
 *
 * <p>为什么要单独一个渲染器：
 * <ul>
 *   <li><b>层</b>：Effect 一律画在 {@code Layer.effect}（110），也就是<b>单位之上</b>。
 *       落地扬尘绕着机体铺开时，这一层土会盖住机体，读成「机体上糊了一层泥」，
 *       单位走到哪糊到哪——问题不在位置，在遮挡关系。尘在地上，必须画在单位
 *       <b>下面</b>、地形上面。（层号见 {@link #layer}）</li>
 *   <li><b>顺序</b>：同一帧多门炮开火时，Effect 的播放顺序由 Renderer 决定；
 *       这里按出生顺序画，后起的尘盖在先起的上面，叠加关系是确定的。</li>
 *   <li><b>取色</b>：{@link FireFx.FloorTint} 每帧取一次地板色，单例 Effect 被多个
 *       落点同时播放时不会互相串色——绕开 EffectState 共享颜色的那套机制。</li>
 * </ul>
 *
 * <p>生命周期仍由 Effect 提供（{@code retain} 按 EffectState 的 id 保活），
 * 所以暂停、切图、单位死亡这些边界情况和普通特效完全一致，不需要另写一套回收逻辑。
 */
public class DustRenderer{

    /**
     * 尘画在这一层：地面单位（{@link Layer#groundUnit} = 60）下方 5 层。
     *
     * <p>夹在 turret(50) 和 groundUnit(60) 之间：盖得住炮塔和建筑，盖不住单位。
     * 再往下会钻到方块底下（{@code blockOver} = 35），土就跑到建筑下面去了，
     * 俯视角下看起来像土从地板缝里冒出来。
     */
    public static final float layer = Layer.groundUnit - 5f;

    /** 尘团绘制回调。{@code p} 是 0→1 的寿命进度 */
    public interface Painter{
        void paint(float x, float y, float rotation, float p, int seed);
    }

    public static class Puff{
        public int id;
        public float x, y, rotation;
        public float age, life;
        public int seed;
        public Painter painter;
    }

    /** 绘制顺序 = 出生顺序 */
    private static final Seq<Puff> order = new Seq<>();
    private static final IntMap<Puff> byId = new IntMap<>();
    private static final Seq<Puff> pool = new Seq<>();
    private static final int POOL_LIMIT = 128;
    private static boolean registered = false;

    public static void init(){
        if(registered) return;
        registered = true;
        Events.run(Trigger.update, DustRenderer::update);
        Events.run(Trigger.draw, DustRenderer::schedule);
    }

    /**
     * 按 id 保活一个尘团。由 {@code Effect.render} 每帧调用。
     *
     * <p>同一个 Effect 实例被多门炮同时播放时，每次播放的 EffectState id 不同，
     * 所以互不干扰；同一个 id 重复调用只是刷新位置，不会重复创建——
     * 这就是不另外写一套「出生 / 存活 / 回收」状态机的原因。
     */
    public static void retain(int id, float x, float y, float rotation, float life, int seed, Painter painter){
        Puff p = byId.get(id);
        if(p != null){
            // 位置和朝向每帧对齐一次：炮口相对机体的偏移会随单位转动变化
            p.x = x;
            p.y = y;
            p.rotation = rotation;
            return;
        }

        p = pool.size > 0 ? pool.pop() : new Puff();
        p.id = id;
        p.x = x;
        p.y = y;
        p.rotation = rotation;
        p.age = 0f;
        p.life = life;
        p.seed = seed;
        p.painter = painter;
        byId.put(id, p);
        order.add(p);
    }

    /** 立即清空（切图、卸载 mod 时调用） */
    public static void clear(){
        for(int i = 0; i < order.size; i++){
            recycle(order.get(i));
        }
        order.clear();
        byId.clear();
    }

    public static int activeCount(){
        return order.size;
    }

    private static void update(){
        for(int i = order.size - 1; i >= 0; i--){
            Puff p = order.get(i);
            // 和普通 Effect 同一个时间源：暂停时一起冻结，不出现「尘走了、单位没动」
            p.age += Time.delta;
            if(p.age >= p.life){
                order.remove(i);
                byId.remove(p.id);
                recycle(p);
            }
        }
    }

    private static void schedule(){
        if(order.size == 0) return;
        Draw.draw(layer, DustRenderer::drawAll);
    }

    private static void drawAll(){
        for(int i = 0; i < order.size; i++){
            Puff p = order.get(i);
            float prog = Math.min(1f, p.age / p.life);
            p.painter.paint(p.x, p.y, p.rotation, prog, p.seed);
        }
        // 每个 painter 各自切过混合模式，出栈前复位，免得把后面的绘制带脏
        Draw.blend();
        Draw.color();
    }

    private static void recycle(Puff p){
        p.painter = null;
        if(pool.size < POOL_LIMIT){
            pool.add(p);
        }
    }
}
