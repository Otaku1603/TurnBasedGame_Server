package com.game.fwork.repository;

import com.game.fwork.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 技能数据访问接口
 */
@Repository
public interface SkillRepository extends JpaRepository<Skill, Integer> {

    /**
     * 查询角色拥有的所有技能ID
     * 使用原生 SQL 直接查询关联表 t_character_skill，避免加载不必要的关联对象，提高性能
     */
    @Query(value = "SELECT skill_id FROM t_character_skill WHERE character_id = :characterId", nativeQuery = true)
    List<Integer> findSkillIdsByCharacterId(@Param("characterId") Long characterId);

    /**
     * 为角色分配技能
     * 使用原生 SQL 直接插入关联表
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO t_character_skill (character_id, skill_id, skill_level) VALUES (:characterId, :skillId, 1)", nativeQuery = true)
    void addSkillToCharacter(@Param("characterId") Long characterId, @Param("skillId") Integer skillId);
}