package project.content;

public enum ProtectModes {
    /** 常规攻击：跟随锚点 + 打范围内敌人 */
    ATTACK,
    /** 推开：无武器单位冲向敌人用物理推开 */
    PUSH,
    /** 护盾：挡在被保护单位和敌人之间做肉盾 */
    SHIELD,
    /** 被动：只跟随 */
    PASSIVE
}