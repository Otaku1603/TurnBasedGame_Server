package com.game.fwork.repository;

import com.game.fwork.entity.CharacterTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CharacterTemplateRepository extends JpaRepository<CharacterTemplate, Integer> {

    /**
     * 职业模板数据访问接口
     * 用于在创建新角色时读取初始属性（血量、攻击力等）
     */
    Optional<CharacterTemplate> findByCharType(String charType);
}