--[[
    伤害计算公式
--]]

function calculateDamage(context)
    local attackerAttack = context.attackerAttack
    local defenderDefense = context.defenderDefense
    local multiplier = context.skillMultiplier
    local defBreak = context.skillDefBreak

    local attackerCrit = context.attackerCrit
    local defenderDodge = context.defenderDodge
    local isDefending = context.defenderDefending

    -- 1. 判定闪避 (优先级最高)
    -- 生成 1-100 的随机数
    local dodgeRoll = math.random(1, 100)
    if dodgeRoll <= defenderDodge then
        return 0 -- 闪避成功，伤害为0
    end

    -- 2. 判定暴击
    local critRoll = math.random(1, 100)
    local isCrit = false
    if critRoll <= attackerCrit then
        isCrit = true
    end

    -- 3. 计算基础伤害
    local attackPower = attackerAttack * multiplier

    -- 如果暴击，攻击力翻倍 (或者1.5倍，这里设为1.5)
    if isCrit then
        attackPower = attackPower * 1.5
    end

    -- 4. 计算防御减伤
    local defenseReduction = defenderDefense * defBreak

    -- 5. 判定防御状态 (防御状态下，受到伤害减半)
    local finalDamage = attackPower - defenseReduction
    if isDefending == true or isDefending == 1 then
        finalDamage = finalDamage * 0.5
    end

    -- 6. 保底伤害
    finalDamage = math.floor(finalDamage + 0.5)
    if finalDamage < 1 then
        finalDamage = 1
    end

    -- 返回伤害值
    return finalDamage
end

function calculateHeal(context)
    local healerMaxHp = context.healerMaxHp
    local multiplier = context.skillMultiplier

    local rawHeal = healerMaxHp * multiplier
    local finalHeal = math.floor(rawHeal + 0.5)

    if finalHeal < 1 then finalHeal = 1 end
    return finalHeal
end