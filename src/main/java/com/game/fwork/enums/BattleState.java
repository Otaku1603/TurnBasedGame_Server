package com.game.fwork.enums;

/**
 * 战斗状态枚举
 * 管理战斗生命周期：从 WAITING（匹配成功） -> FIGHTING（战斗中） -> FINISHED（结算）
 * 用于状态机控制，防止在错误的阶段执行操作（如已经结束了还能攻击）
 */
public enum BattleState {

    /**
     * 等待玩家准备
     * 匹配成功后进入此状态，等待双方发送READY消息
     */
    WAITING("等待准备"),

    /**
     * 战斗进行中
     * 双方都准备完毕，开始轮流行动
     */
    FIGHTING("战斗中"),

    /**
     * 战斗已结束
     * 某一方血量归零或超时/投降
     */
    FINISHED("已结束"),

    /**
     * 战斗异常终止
     * 双方都掉线或其他异常情况
     */
    ABORTED("异常终止");

    private final String description;

    BattleState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否可以进行战斗操作（攻击、技能）
     * 只有FIGHTING状态才允许
     */
    public boolean canPerformAction() {
        return this == FIGHTING;
    }

    /**
     * 判断战斗是否已经结束（包括正常结束和异常终止）
     */
    public boolean isEnded() {
        return this == FINISHED || this == ABORTED;
    }
}