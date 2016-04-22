package com.almasb.zeph.entity.character.control

import com.almasb.ents.AbstractControl
import com.almasb.ents.Entity
import com.almasb.zeph.combat.Attribute
import com.almasb.zeph.combat.Experience
import com.almasb.zeph.combat.Stat
import com.almasb.zeph.entity.Data
import com.almasb.zeph.entity.DescriptionComponent
import com.almasb.zeph.entity.EntityManager
import com.almasb.zeph.entity.character.EquipPlace
import com.almasb.zeph.entity.character.PlayerEntity
import com.almasb.zeph.entity.character.component.AttributesComponent
import com.almasb.zeph.entity.item.ArmorEntity
import com.almasb.zeph.entity.item.ArmorType
import com.almasb.zeph.entity.item.WeaponEntity
import com.almasb.zeph.entity.item.WeaponType
import com.almasb.zeph.entity.skill.SkillType
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyIntegerWrapper
import javafx.beans.property.SimpleObjectProperty
import java.util.*

/**
 * TODO: we actually have PlayerControl and CharacterControl
 * on player entity, good / bad ? maybe leave both and separate responsibilities
 *
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
class PlayerControl : CharacterControl() {

    private lateinit var player: PlayerEntity

    override fun onAdded(entity: Entity) {
        super.onAdded(entity)

        player = entity as PlayerEntity

        EquipPlace.values().forEach {
            val item: Entity = if (it.isWeapon) EntityManager.getWeapon(it.emptyID) else EntityManager.getArmor(it.emptyID)

            if (item is WeaponEntity)
                item.data.onEquip(player)
            else if (item is ArmorEntity)
                item.data.onEquip(player)

            equip.put(it, item)
            equipProperties.put(it, SimpleObjectProperty(item))
        }
    }

    fun rewardMoney(amount: Int) {
        player.money.value += amount
    }

    /**
     * Gameplay constants
     */
    private val MAX_LEVEL_BASE = 100
    private val MAX_LEVEL_STAT = 100
    private val MAX_LEVEL_JOB = 60
    private val MAX_LEVEL_SKILL = 10
    private val MAX_ATTRIBUTE = 100
    private val ATTRIBUTE_POINTS_PER_LEVEL = 3

    /**
     * Holds experience needed for each level
     */
    private val EXP_NEEDED_BASE = IntArray(MAX_LEVEL_BASE)
    private val EXP_NEEDED_STAT = IntArray(MAX_LEVEL_STAT)
    private val EXP_NEEDED_JOB = IntArray(MAX_LEVEL_JOB)

    init {
        /**
         * By what value should experience needed for next level
         * increase per level
         */
        val EXP_NEEDED_INC_BASE = 1.75f;
        val EXP_NEEDED_INC_STAT = 1.5f;
        val EXP_NEEDED_INC_JOB  = 2.25f;

        val EXP_NEEDED_FOR_LEVEL2 = 10;

        EXP_NEEDED_BASE[0] = EXP_NEEDED_FOR_LEVEL2;
        EXP_NEEDED_STAT[0] = EXP_NEEDED_FOR_LEVEL2;
        EXP_NEEDED_JOB[0] = EXP_NEEDED_FOR_LEVEL2;

        for (i in 1..EXP_NEEDED_BASE.size - 1) {
            EXP_NEEDED_BASE[i] = (EXP_NEEDED_BASE[i - 1] * EXP_NEEDED_INC_BASE + 2 * i).toInt();

            if (i < EXP_NEEDED_STAT.size)
                EXP_NEEDED_STAT[i] = (EXP_NEEDED_STAT[i - 1] * EXP_NEEDED_INC_STAT + i).toInt();

            if (i < EXP_NEEDED_JOB.size)
                EXP_NEEDED_JOB[i] = (EXP_NEEDED_JOB[i - 1] * EXP_NEEDED_INC_JOB + 3 * i).toInt();
        }
    }

    /**
     * Increases base [attribute].
     */
    fun increaseAttribute(attribute: Attribute) {
        if (player.attributePoints.value == 0)
            return

        val value = player.attributes.getBaseAttribute(attribute)
        if (value < MAX_ATTRIBUTE) {
            player.attributes.setAttribute(attribute, value + 1)
            player.attributePoints.value--
        }
    }

    fun increaseSkillLevel(index: Int) {
        if (player.skillPoints.value == 0)
            return

        val skill = player.skills[index]

        if (skill.level.value < MAX_LEVEL_SKILL) {
            skill.level.value++
            player.skillPoints.value--

            // apply passive skills immediately
            if (skill.data.type == SkillType.PASSIVE) {
                skill.data.onCast(player, player, skill.level.value)
            }
        }
    }

    fun expNeededForNextBaseLevel(): Int {
        return EXP_NEEDED_BASE[player.baseLevel.value - 1]
    }

    fun expNeededForNextStatLevel(): Int {
        return EXP_NEEDED_STAT[player.statLevel.value - 1]
    }

    fun expNeededForNextJobLevel(): Int {
        return EXP_NEEDED_JOB[player.jobLevel.value - 1]
    }

    /**
     * Increases player's experience by [gainedXP].
     *
     * @return true if player gained new base level
     */
    fun rewardXP(gainedXP: Experience): Boolean {
        var baseLevelUp = false

        if (player.statLevel.value < MAX_LEVEL_STAT) {
            player.statXP.value += gainedXP.stat

            if (player.statXP.value >= expNeededForNextStatLevel()) {
                player.statXP.value = 0
                statLevelUp();
            }
        }

        if (player.jobLevel.value < MAX_LEVEL_JOB) {
            player.jobXP.value += gainedXP.job

            if (player.jobXP.value >= expNeededForNextJobLevel()) {
                player.jobXP.value = 0
                jobLevelUp();
            }
        }

        if (player.baseLevel.value < MAX_LEVEL_BASE) {
            player.baseXP.value += gainedXP.base

            if (player.baseXP.value >= expNeededForNextBaseLevel()) {
                player.baseXP.value = 0
                baseLevelUp();
                baseLevelUp = true
            }
        }

        return baseLevelUp
    }

    private fun baseLevelUp() {
        player.baseLevel.value++

        player.hp.restorePercentageMax(100.0)
        player.sp.restorePercentageMax(100.0)
    }

    private fun statLevelUp() {
        player.statLevel.value++
        player.attributePoints.value += ATTRIBUTE_POINTS_PER_LEVEL
    }

    private fun jobLevelUp() {
        player.jobLevel.value++
        player.skillPoints.value++
    }

    val equip = HashMap<EquipPlace, Entity>()
    val equipProperties = HashMap<EquipPlace, ObjectProperty<Entity> >()

    fun getEquip(place: EquipPlace) = equip[place]!!
    fun equipProperty(place: EquipPlace) = equipProperties[place]!!

    fun setEquip(place: EquipPlace, item: Entity) {
        equip.put(place, item)
        equipProperties[place]!!.set(item)
    }

    fun getRightWeapon() = getEquip(EquipPlace.RIGHT_HAND) as WeaponEntity
    fun getLeftWeapon() = getEquip(EquipPlace.LEFT_HAND) as WeaponEntity

    fun equipWeapon(weapon: WeaponEntity) {
        player.inventory.removeItem(weapon)

        if (weapon.data.type.isTwoHanded()) {

            if (30 - player.inventory.getItems().size == 1
                && !isFree(EquipPlace.RIGHT_HAND)
                && !isFree(EquipPlace.LEFT_HAND)) {
                // ex case, when inventory is full and player tries to equip 2H weapon
                // but holds two 1H weapons
                player.inventory.addItem(weapon)
                return
            }

            unEquipItem(EquipPlace.RIGHT_HAND)
            unEquipItem(EquipPlace.LEFT_HAND)
            setEquip(EquipPlace.RIGHT_HAND, weapon)
            setEquip(EquipPlace.LEFT_HAND, weapon)

        } else if (weapon.data.type == WeaponType.SHIELD || !isFree(EquipPlace.RIGHT_HAND)) {
            unEquipItem(EquipPlace.LEFT_HAND)
            setEquip(EquipPlace.LEFT_HAND, weapon)
        } else {    // normal 1H weapon
            unEquipItem(EquipPlace.RIGHT_HAND)
            setEquip(EquipPlace.RIGHT_HAND, weapon)
        }

        weapon.data.onEquip(player)
    }

    fun equipArmor(armor: ArmorEntity) {
        player.inventory.removeItem(armor)

        val place = when (armor.data.armorType) {
            ArmorType.BODY -> EquipPlace.BODY
            ArmorType.HELM -> EquipPlace.HELM
            ArmorType.SHOES -> EquipPlace.SHOES
        }

        unEquipItem(place)
        setEquip(place, armor)
        armor.data.onEquip(player)
    }

    fun unEquipItem(place: EquipPlace) {
        if (isFree(place) || player.inventory.isFull())
            return

        val item = getEquip(place)

        if (item is WeaponEntity) {
            if (item.data.type.isTwoHanded()) {
                if (place == EquipPlace.RIGHT_HAND)
                    setEquip(EquipPlace.LEFT_HAND, EntityManager.getWeapon(place.emptyID))
                else
                    setEquip(EquipPlace.RIGHT_HAND, EntityManager.getWeapon(place.emptyID))
            }

            item.data.onUnEquip(player)
        } else if (item is ArmorEntity) {
            item.data.onUnEquip(player)
        }

        player.inventory.addItem(item)

        // replace with default
        if (place.isWeapon) {
            setEquip(place, EntityManager.getWeapon(place.emptyID))
        } else {
            setEquip(place, EntityManager.getArmor(place.emptyID))
        }
    }

    fun isFree(place: EquipPlace) = getEquip(place)
            .getComponentUnsafe(DescriptionComponent::class.java).id == place.emptyID

//    override fun canAttack(): Boolean {
//        val w1 = getEquip(EquipPlace.RIGHT_HAND) as WeaponEntity;
//        val w2 = getEquip(EquipPlace.LEFT_HAND) as WeaponEntity;
//
//        return atkTick >= 2.0
//        //return atkTick >= 50 / (1 + stats.getTotalStat(Stat.ASPD) *w1.data.type.aspdFactor*w2.data.type.aspdFactor/1000.0);
//    }
    //
    //    @Override
    //    public final Element getWeaponElement() {
    //        return getEquip(EquipPlace.RIGHT_HAND).getElement();
    //    }
    //
    //    @Override
    //    public final Element getArmorElement() {
    //        return getEquip(EquipPlace.BODY).getElement();
    //    }
}