package com.game.fwork.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户详细资料传输对象
 * 用于前端展示玩家个人档案（包括自己和其他玩家）
 * 包含基础信息、统计数据及当前激活的角色快照
 */
@Getter
@Setter
public class UserProfileDTO {

    private Long userId;
    private String nickname;
    private Integer eloRating;
    private Integer gold;
    private Integer avatarFrameId;

    // 统计数据
    private Integer totalBattles;
    private Integer winCount;
    private String winRate;

    // 角色数据
    private CharacterDTO character;

    @Override
    public String toString() {
        return "UserProfileDTO{" +
                "userId=" + userId +
                ", nickname='" + nickname + '\'' +
                ", eloRating=" + eloRating +
                '}';
    }

    /**
     * 角色简要信息内部类
     */
    @Getter
    @Setter
    public static class CharacterDTO {
        private String charType;
        private String charName;
        private Integer level;
        private Integer maxHp;
        private Integer attack;
        private Integer defense;
        private Integer speed;

        @Override
        public String toString() {
            return "CharacterDTO{" +
                    "charType='" + charType + '\'' +
                    ", level=" + level +
                    '}';
        }
    }
}