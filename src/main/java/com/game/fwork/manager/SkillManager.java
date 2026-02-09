package com.game.fwork.manager;

import com.game.fwork.entity.Skill;
import com.game.fwork.repository.SkillRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SkillManager {

    private static final Logger logger = LoggerFactory.getLogger(SkillManager.class);

    @Autowired
    private SkillRepository skillRepository;

    // 缓存所有技能 ID -> Skill
    private final Map<Integer, Skill> skillCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 刷新缓存（从数据库重新加载）
     */
    public void refresh() {
        // 先读取新数据，防止读取过程中清空缓存导致短暂的“无数据”状态
        List<Skill> skills = skillRepository.findAll();

        skillCache.clear();
        for (Skill skill : skills) {
            skillCache.put(skill.getId(), skill);
        }
        logger.info("技能数据加载/刷新完成，共加载 {} 个技能", skills.size());
    }

    public Skill getSkill(Integer skillId) {
        return skillCache.get(skillId);
    }
}