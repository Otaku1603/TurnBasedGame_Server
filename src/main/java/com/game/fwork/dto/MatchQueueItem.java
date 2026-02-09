package com.game.fwork.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * 匹配队列元素
 * 存储在 Redis List 中的数据结构
 * 包含玩家的基础匹配信息（ELO分、入队时间），用于 MatchService 进行算法匹配
 */
@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchQueueItem {

    private Long userId;
    private Integer eloRating;
    private String nickname;
    private Long joinTime;
    private Long characterId;

    public MatchQueueItem() {
        this.joinTime = System.currentTimeMillis();
    }

    public MatchQueueItem(Long userId, Integer eloRating, String nickname, Long characterId) {
        this.userId = userId;
        this.eloRating = eloRating;
        this.nickname = nickname;
        this.characterId = characterId;
        this.joinTime = System.currentTimeMillis();
    }

    public boolean canMatchWith(MatchQueueItem other, int range) {
        if (other == null) return false;
        if (this.userId.equals(other.userId)) return false;
        int eloDiff = Math.abs(this.eloRating - other.eloRating);
        return eloDiff <= range;
    }

    @JsonIgnore
    public long getWaitingTimeInSeconds() {
        return (System.currentTimeMillis() - joinTime) / 1000;
    }

    // ================= toString =================
    @Override
    public String toString() {
        return "MatchQueueItem{" +
                "userId=" + userId +
                ", elo=" + eloRating +
                ", waitTime=" + getWaitingTimeInSeconds() + "s" +
                '}';
    }
}