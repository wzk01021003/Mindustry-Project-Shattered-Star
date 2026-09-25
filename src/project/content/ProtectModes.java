package project.content;

/** 保护模式的类型 */
public enum ProtectModes {
    /** 常规攻击：跟随目标 + 打范围内敌人 */
    ATTACK,
    /** 推开：无武器单位用，冲向敌人用物理碰撞推开 */
    PUSH,
    /** 护盾：挡在被保护单位和敌人之间，做肉盾 */
    SHIELD,
    /** 被动：只跟随，不主动做事 */
    PASSIVE
}