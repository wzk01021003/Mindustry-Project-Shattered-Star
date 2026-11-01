package project.content;

import arc.Core;

import mindustry.Vars;
import mindustry.mod.Mods;
import mindustry.type.Weapon;

import project.ShatteredStarMod;

/**
 * 修正 mod 自定义武器贴图找不到的问题。
 *
 * <p>Mindustry 打包 mod 的 {@code sprites/} 时，会给每个 region 名加上
 * mod 名作前缀（{@code Mods.packSprites} 里
 * {@code prefix = flag ? mod.name + "-" : ""}，sprites/ 传 true、
 * sprites-override/ 传 false——后者要保持原名才能覆盖原版图）。因为所有
 * mod 的图最终都进同一张全局 atlas，不加前缀必然重名。本 mod 名为
 * {@code ss}，所以 {@code large-weapon-allotropes.png} 打包后实际叫
 * {@code ss-large-weapon-allotropes}。
 *
 * <p>单位不受影响：它是 {@code Content}，名字会被
 * {@code ContentLoader.transformName()} 改写成 {@code ss-dagger-at}，
 * 于是 {@code Core.atlas.find("ss-dagger-at")} 正好命中打包结果——这也是
 * 语言包键写成 {@code unit.ss-dagger-at.name} 才对得上的原因。
 *
 * <p>{@code Weapon} 不是 {@code Content}，不经过 {@code transformName}，
 * 而 {@code Weapon.load()} 里是 {@code Core.atlas.find(name)} 直接照名字查，
 * 于是查的是 {@code large-weapon-allotropes} —— 落空，返回 error region，
 * 炮塔渲染成错误图或干脆不显示。
 *
 * <p>这里在 {@code super.load()} 之前补一次解析：只要本 mod 的 sprites 里
 * 存在同名文件（{@code ss-xxx} 能命中），就把 name 换成带前缀的版本。
 *
 * <p>顺序不能反过来做「原名优先」：{@code artillery} 和
 * {@code zenith-missiles} 这两个名字原版也有，原名查询会命中原版 atlas，
 * 于是拿到的是原版那张图，而配套的 {@code -heat} / {@code -outline}
 * 原版并不存在（只有 mod 里才有），结果是炮塔主体用原版图、热区和描边
 * 全部丢失。优先取 mod 自己那套才完整。
 */
public class SSWeapon extends Weapon {

    public SSWeapon(String name) {
        super(name);
    }

    @Override
    public void load() {
        resolveModPrefix();
        super.load();
    }

    /**
     * 把名字换成带 mod 前缀的版本。
     * 换掉之后 super.load() 里的 region / heatRegion / cellRegion /
     * outlineRegion 以及 parts 都会按新名字查，全部对得上。
     * mod 里没有对应文件时保持原名，交给 Core.atlas 正常回落原版。
     */
    private void resolveModPrefix() {
        if (name == null || name.isEmpty()) return;

        Mods.LoadedMod self = selfMod();
        if (self == null) return;

        String prefixed = self.name + "-" + name;
        if (Core.atlas.find(prefixed).found()) {
            name = prefixed;
        }
    }

    /** 取本 mod 的加载记录，拿不到就返回 null（此时不做任何替换）。 */
    private static Mods.LoadedMod selfMod() {
        if (Vars.mods == null) return null;
        return Vars.mods.getMod(ShatteredStarMod.class);
    }
}
