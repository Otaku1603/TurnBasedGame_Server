package com.game.fwork.controller;

import com.game.fwork.entity.CharacterTemplate;
import com.game.fwork.entity.Item;
import com.game.fwork.entity.Skill;
import com.game.fwork.repository.CharacterTemplateRepository;
import com.game.fwork.repository.ItemRepository;
import com.game.fwork.repository.SkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 游戏静态数据字典接口
 * 前端在游戏启动时调用，一次性拉取技能、道具、职业模板等配置数据
 */
@RestController
@RequestMapping("/api/gamedata")
public class GameDataController {

    @Autowired private SkillRepository skillRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private CharacterTemplateRepository templateRepository;

    /**
     * 获取全量技能配置
     * 用于前端缓存技能描述、图标等静态资源，战斗时仅传输技能ID
     */
    @GetMapping("/skills")
    public Map<String, Object> getSkills() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Skill> skills = skillRepository.findAll();
            List<Map<String, Object>> skillList = skills.stream().map(skill -> {
                Map<String, Object> vo = new HashMap<>();
                vo.put("id", skill.getId());
                vo.put("name", skill.getSkillName());
                vo.put("description", skill.getDescription());
                vo.put("type", skill.getSkillType());
                vo.put("targetType", skill.getTargetType());
                return vo;
            }).collect(Collectors.toList());

            response.put("success", true);
            response.put("data", skillList);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * 获取所有道具配置
     */
    @GetMapping("/items")
    public Map<String, Object> getItems() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Item> items = itemRepository.findAll();
            List<Map<String, Object>> itemList = items.stream().map(item -> {
                Map<String, Object> vo = new HashMap<>();
                vo.put("id", item.getId());
                vo.put("name", item.getName());
                vo.put("description", item.getDescription());
                vo.put("type", item.getType());
                vo.put("price", item.getPrice());
                String iconPath = item.getIconPath();
                if (iconPath == null || iconPath.isEmpty()) iconPath = "/items/item_" + item.getId() + ".png";
                vo.put("iconPath", iconPath);
                vo.put("usableInBattle", "POTION".equals(item.getType()));
                return vo;
            }).collect(Collectors.toList());

            response.put("success", true);
            response.put("data", itemList);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * 获取职业模板 (用于注册页面展示)
     */
    @GetMapping("/templates")
    public Map<String, Object> getTemplates() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<CharacterTemplate> templates = templateRepository.findAll();

            List<Map<String, Object>> templateList = templates.stream().map(t -> {
                Map<String, Object> vo = new HashMap<>();
                vo.put("id", t.getId());
                vo.put("charType", t.getCharType()); // warrior, mage...
                vo.put("namePrefix", t.getCharNamePrefix()); // 见习战士...
                vo.put("baseMaxHp", t.getBaseMaxHp());
                vo.put("baseAttack", t.getBaseAttack());
                vo.put("baseSpeed", t.getBaseSpeed());
                return vo;
            }).collect(Collectors.toList());

            response.put("success", true);
            response.put("data", templateList);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }
}