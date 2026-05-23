package llc.redstone.htslreborn.htslio

import llc.redstone.systemsapi.importer.ActionContainer
import llc.redstone.systemsapi.importer.ConditionContainer
import llc.redstone.systemsapi.importer.PropertySettings
import llc.redstone.systemsapi.util.InputUtils
import llc.redstone.systemsapi.util.MenuUtils
import llc.redstone.systemsdata.Action
import llc.redstone.systemsdata.ActionDefinition
import llc.redstone.systemsdata.Condition
import llc.redstone.systemsdata.DisplayName
import llc.redstone.systemsdata.FishingEnvironment as FishingEnv
import llc.redstone.systemsdata.Keyed
import llc.redstone.systemsdata.KeyedCycle
import llc.redstone.systemsdata.PropertyHolder
import net.minecraft.screen.slot.Slot
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

internal object HTSLActionImporter {
    private val slots = mapOf(
        0 to 10,
        1 to 11,
        2 to 12,
        3 to 13,
        4 to 14,
        5 to 15,
        6 to 16,
        7 to 19,
        8 to 20,
        9 to 21,
        10 to 22,
        11 to 23,
        12 to 24,
        13 to 25,
        14 to 28,
        15 to 29,
        16 to 30,
        17 to 31,
        18 to 32,
        19 to 33,
        20 to 34,
    )

    fun needsLocalImport(action: Action): Boolean {
        return when (action) {
            is Action.Conditional -> true
            is Action.RandomAction -> action.actions.any(::needsLocalImport)
            else -> false
        }
    }

    suspend fun addAction(actionContainer: ActionContainer, action: Action) {
        addActions(actionContainer, listOf(action))
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun addActions(actionContainer: ActionContainer, actions: List<Action>) {
        for (action in actions) {
            MenuUtils.onOpen(actionContainer.title)

            if (action is Action.CustomAction) {
                action.function(action.parameters)
                continue
            }

            MenuUtils.clickItems(ActionContainer.MenuItems.ADD_ACTION)
            MenuUtils.onOpen("Add Action")

            val parameters = action::class.primaryConstructor!!.parameters.toMutableList()
            val actionProperties = action.javaClass.kotlin.memberProperties
            val properties = mutableListOf<KProperty1<Action, *>>()

            for (parameter in parameters) {
                properties.add(actionProperties.find { it.name == parameter.name } as? KProperty1<Action, *> ?: continue)
            }

            val displayName = (action::class.annotations.find { it is ActionDefinition } as ActionDefinition).displayName
            MenuUtils.clickItems(displayName, paginated = true)

            if (action is Action.ChangeVariable) {
                properties.add(0, actionProperties.find { it.name == "holder" } as? KProperty1<Action, *> ?: continue)
            }

            for ((index, property) in properties.withIndex()) {
                val value = property.get(action)

                MenuUtils.onOpen("Action Settings")

                val slot = MenuUtils.getSlot(slots[index]!!)
                importActionProperty(property as KProperty1<PropertyHolder, *>, slot, value)
            }

            if (properties.isNotEmpty()) {
                MenuUtils.onOpen("Action Settings")
                MenuUtils.clickItems(ActionContainer.MenuItems.BACK)
            }
            MenuUtils.onOpen(actionContainer.title)
        }
    }

    private suspend fun importActionProperty(property: KProperty1<PropertyHolder, *>, slot: Slot, value: Any?) {
        if (value is KeyedCycle) {
            InputUtils.setKeyedCycle(slot, value.key)
            return
        }

        if (value is List<*> && value.isNotEmpty()) {
            when {
                value.first() is Action -> {
                    val actions = value.filterIsInstance<Action>()
                    if (actions.size != value.size) error("List contains non-action entries")

                    MenuUtils.packetClick(slot.id)
                    ActionContainer.updateTime = false
                    try {
                        addActions(ActionContainer("Edit Actions"), actions)
                    } finally {
                        ActionContainer.updateTime = true
                    }
                    MenuUtils.onOpen("Edit Actions")
                    MenuUtils.clickItems(ActionContainer.MenuItems.BACK)
                    MenuUtils.onOpen("Action Settings")
                    return
                }

                value.first() is Condition -> {
                    val conditions = value.filterIsInstance<Condition>()
                    if (conditions.size != value.size) error("List contains non-condition entries")

                    MenuUtils.packetClick(slot.id)
                    addConditions(conditions)
                    MenuUtils.onOpen("Edit Conditions")
                    MenuUtils.clickItems(ActionContainer.MenuItems.BACK)
                    MenuUtils.onOpen("Action Settings")
                    return
                }
            }
        }

        PropertySettings.import(property, slot, value)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun addConditions(conditions: List<Condition>) {
        for (condition in conditions) {
            MenuUtils.onOpen("Edit Conditions")

            MenuUtils.clickItems(ConditionContainer.MenuItems.ADD_CONDITION)
            MenuUtils.onOpen("Add Condition")

            val parameters = condition::class.primaryConstructor!!.parameters.toMutableList()
            val conditionProperties = condition.javaClass.kotlin.memberProperties
            val properties = mutableListOf<KProperty1<Condition, *>>()

            for (parameter in parameters) {
                properties.add(conditionProperties.find { it.name == parameter.name } as? KProperty1<Condition, *> ?: continue)
            }

            val displayName = (condition::class.annotations.find { it is DisplayName } as DisplayName).value
            MenuUtils.clickItems(displayName, paginated = true)

            properties.add(0, conditionProperties.find { it.name == "inverted" } as? KProperty1<Condition, *> ?: continue)

            if (condition is Condition.VariableRequirement) {
                properties.add(1, conditionProperties.find { it.name == "holder" } as? KProperty1<Condition, *> ?: continue)
            }

            for ((index, property) in properties.withIndex()) {
                val value = property.get(condition)

                MenuUtils.onOpen("Settings")

                val slot = MenuUtils.getSlot(slots[index]!!)
                importConditionProperty(property as KProperty1<PropertyHolder, *>, slot, value)
            }

            if (properties.isNotEmpty()) {
                MenuUtils.onOpen("Settings")
                MenuUtils.clickItems(ConditionContainer.MenuItems.BACK)
            }
            MenuUtils.onOpen("Edit Conditions")
        }
    }

    private suspend fun importConditionProperty(property: KProperty1<PropertyHolder, *>, slot: Slot, value: Any?) {
        if (value is KeyedCycle || value is FishingEnv) {
            val keyed = value as Keyed
            InputUtils.setKeyedCycle(slot, keyed.key)
            return
        }

        PropertySettings.import(property, slot, value)
    }
}
