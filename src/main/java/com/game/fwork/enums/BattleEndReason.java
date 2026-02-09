package com.game.fwork.enums;

/**
 * 战斗结束原因枚举
 * 定义了所有可能的结算类型（正常击杀、投降、超时、断线）
 * 该字段会直接存储在数据库 t_battle_record 表中，用于后续的数据统计
 */
public enum BattleEndReason {

    /**
     * 正常结束 - 一方血量归零
     */
    NORMAL("NORMAL", "正常结束"),

    /**
     * 投降 - 玩家主动认输
     */
    SURRENDER("SURRENDER", "投降"),

    /**
     * 超时 - 长时间未操作
     */
    TIMEOUT("TIMEOUT", "超时"),

    /**
     * 断线 - 连接中断且未重连
     */
    DISCONNECT("DISCONNECT", "断线");

    /**
     * 枚举代码（存储到数据库）
     */
    private final String code;

    /**
     * 中文描述（用于日志输出）
     */
    private final String description;

    /**
     * 构造方法
     */
    BattleEndReason(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取枚举代码（用于存储到数据库）
     * @return 枚举代码（NORMAL/SURRENDER/TIMEOUT/DISCONNECT）
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取中文描述（用于日志输出）
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据代码查找枚举
     * @param code 枚举代码
     * @return 对应的枚举，找不到返回null
     */
    public static BattleEndReason fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (BattleEndReason reason : values()) {
            if (reason.code.equals(code)) {
                return reason;
            }
        }
        return null;
    }
}
