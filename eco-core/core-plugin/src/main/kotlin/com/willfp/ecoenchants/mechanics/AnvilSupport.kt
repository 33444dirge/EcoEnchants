package com.willfp.ecoenchants.mechanics

import com.willfp.eco.core.fast.fast
import com.willfp.eco.core.proxy.ProxyConstants
import com.willfp.eco.util.StringUtils
import com.willfp.ecoenchants.EcoEnchantsScheduler
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.enchant.wrap
import com.willfp.ecoenchants.plugin
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.GameMode
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.AnvilInventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class AnvilResult(
    val result: ItemStack?,
    val xp: Int?,
    val onlyRename: Boolean = false
)

fun Int.infiniteIfNegative() = if (this < 1) Int.MAX_VALUE else this

private val FAIL = AnvilResult(null, null)

private const val VANILLA_TOO_EXPENSIVE_COST = 40

interface OpenInventoryProxy {
    fun getOpenInventory(player: Player): Any
}

interface ClientGameModeProxy {
    fun sendClientGameMode(player: Player, gameMode: GameMode)
}

private val repair = mutableMapOf<Collection<Material>, Collection<Material>>(
    Pair(
        Tag.PLANKS.values,
        listOf(
            Material.WOODEN_SWORD,
            Material.WOODEN_PICKAXE,
            Material.WOODEN_AXE,
            Material.WOODEN_SHOVEL,
            Material.WOODEN_HOE,
            Material.SHIELD
        )
    ),
    Pair(
        listOf(Material.LEATHER),
        listOf(
            Material.LEATHER_HELMET,
            Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS,
            Material.LEATHER_BOOTS
        )
    ),
    Pair(
        listOf(
            Material.COBBLESTONE,
            Material.COBBLED_DEEPSLATE,
            Material.BLACKSTONE
        ),
        listOf(
            Material.STONE_SWORD,
            Material.STONE_PICKAXE,
            Material.STONE_AXE,
            Material.STONE_SHOVEL,
            Material.STONE_HOE
        )
    ),
    Pair(
        listOf(
            Material.IRON_INGOT
        ),
        listOf(
            Material.IRON_HELMET,
            Material.IRON_CHESTPLATE,
            Material.IRON_LEGGINGS,
            Material.IRON_BOOTS,
            Material.CHAINMAIL_HELMET,
            Material.CHAINMAIL_CHESTPLATE,
            Material.CHAINMAIL_LEGGINGS,
            Material.CHAINMAIL_BOOTS,
            Material.IRON_SWORD,
            Material.IRON_PICKAXE,
            Material.IRON_AXE,
            Material.IRON_SHOVEL,
            Material.IRON_HOE
        )
    ),
    Pair(
        listOf(
            Material.GOLD_INGOT
        ),
        listOf(
            Material.GOLDEN_HELMET,
            Material.GOLDEN_CHESTPLATE,
            Material.GOLDEN_LEGGINGS,
            Material.GOLDEN_BOOTS,
            Material.GOLDEN_SWORD,
            Material.GOLDEN_PICKAXE,
            Material.GOLDEN_AXE,
            Material.GOLDEN_SHOVEL,
            Material.GOLDEN_HOE
        )
    ),
    Pair(
        listOf(
            Material.DIAMOND
        ),
        listOf(
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS,
            Material.DIAMOND_SWORD,
            Material.DIAMOND_PICKAXE,
            Material.DIAMOND_AXE,
            Material.DIAMOND_SHOVEL,
            Material.DIAMOND_HOE
        )
    ),
    Pair(
        listOf(
            Material.NETHERITE_INGOT
        ),
        listOf(
            Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_BOOTS,
            Material.NETHERITE_SWORD,
            Material.NETHERITE_PICKAXE,
            Material.NETHERITE_AXE,
            Material.NETHERITE_SHOVEL,
            Material.NETHERITE_HOE
        )
    ),
    Pair(
        listOf(
            Material.TURTLE_SCUTE
        ),
        listOf(
            Material.TURTLE_HELMET
        )
    ),
    Pair(
        listOf(
            Material.PHANTOM_MEMBRANE
        ),
        listOf(
            Material.ELYTRA
        )
    )
)

object AnvilSupport : Listener {
    init {
        if (is_1_21_11() && repair[Tag.PLANKS.values]?.contains(Material.WOODEN_SPEAR) != true) {

            repair[Tag.PLANKS.values] = repair[Tag.PLANKS.values]!! + listOf(
                Material.WOODEN_SPEAR
            )

            repair[listOf(Material.COBBLESTONE, Material.COBBLED_DEEPSLATE, Material.BLACKSTONE)] =
                repair[listOf(Material.COBBLESTONE, Material.COBBLED_DEEPSLATE, Material.BLACKSTONE)]!! + listOf(
                    Material.STONE_SPEAR
                )

            repair[listOf(Material.COPPER_INGOT)] = listOf(
                Material.COPPER_HELMET,
                Material.COPPER_CHESTPLATE,
                Material.COPPER_LEGGINGS,
                Material.COPPER_BOOTS,
                Material.COPPER_SWORD,
                Material.COPPER_PICKAXE,
                Material.COPPER_AXE,
                Material.COPPER_SHOVEL,
                Material.COPPER_HOE,
                Material.COPPER_SPEAR
            )

            repair[listOf(Material.IRON_INGOT)] = repair[listOf(Material.IRON_INGOT)]!! + listOf(
                Material.IRON_SPEAR
            )

            repair[listOf(Material.GOLD_INGOT)] = repair[listOf(Material.GOLD_INGOT)]!! + listOf(
                Material.GOLDEN_SPEAR
            )

            repair[listOf(Material.DIAMOND)] = repair[listOf(Material.DIAMOND)]!! + listOf(
                Material.DIAMOND_SPEAR
            )

            repair[listOf(Material.NETHERITE_INGOT)] = repair[listOf(Material.NETHERITE_INGOT)]!! + listOf(
                Material.NETHERITE_SPEAR
            )
        }
    }

    private val latestPreviewGeneration = ConcurrentHashMap<UUID, Int>()

    private val renderedPreviewGeneration = ConcurrentHashMap<UUID, Int>()

    private val expensiveCostClientViewers = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * Class for AnvilGUI wrappers to ignore them.
     */
    private val anvilGuiClass: Class<*>? = try {
        Class.forName(
            "net.wesjd.anvilgui.version.Wrapper" +
                    ProxyConstants.NMS_VERSION.substring(1) +
                    $$"$AnvilContainer"
        )
    } catch (_: ClassNotFoundException) {
        null
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onAnvilResultClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.view.topInventory as? AnvilInventory ?: return

        if (event.rawSlot != 2) {
            return
        }

        if (plugin.getProxy(OpenInventoryProxy::class.java)
                .getOpenInventory(player)::class.java == anvilGuiClass
        ) {
            return
        }

        val latestGeneration = latestPreviewGeneration[player.uniqueId] ?: return
        val renderedGeneration = renderedPreviewGeneration[player.uniqueId]

        if (latestGeneration == renderedGeneration) {
            return
        }

        event.isCancelled = true
        event.currentItem = null
        inventory.setItem(2, null)
    }

    @EventHandler
    fun onAnvilClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        latestPreviewGeneration.remove(player.uniqueId)
        renderedPreviewGeneration.remove(player.uniqueId)
        restoreClientGameMode(player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onGameModeChange(event: PlayerGameModeChangeEvent) {
        if (event.player.uniqueId !in expensiveCostClientViewers) {
            return
        }

        EcoEnchantsScheduler.runAtPlayer(event.player) {
            if (event.player.openInventory.topInventory is AnvilInventory) {
                plugin.getProxy(ClientGameModeProxy::class.java)
                    .sendClientGameMode(event.player, GameMode.CREATIVE)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        latestPreviewGeneration.remove(event.player.uniqueId)
        renderedPreviewGeneration.remove(event.player.uniqueId)
        expensiveCostClientViewers.remove(event.player.uniqueId)
    }

    private fun restoreClientGameMode(player: Player) {
        updateExpensiveCostBypass(player, false)
    }

    private fun updateExpensiveCostBypass(player: Player, enabled: Boolean) {
        val playerId = player.uniqueId
        val changed = if (enabled && player.gameMode != GameMode.CREATIVE) {
            expensiveCostClientViewers.add(playerId)
        } else {
            expensiveCostClientViewers.remove(playerId)
        }
        if (!changed) {
            return
        }

        EcoEnchantsScheduler.runAtPlayer(player) {
            if (playerId in expensiveCostClientViewers) {
                if (player.openInventory.topInventory is AnvilInventory && player.gameMode != GameMode.CREATIVE) {
                    plugin.getProxy(ClientGameModeProxy::class.java)
                        .sendClientGameMode(player, GameMode.CREATIVE)
                }
            } else {
                plugin.getProxy(ClientGameModeProxy::class.java)
                    .sendClientGameMode(player, player.gameMode)
            }
        }
    }

    @Suppress("UnstableApiUsage")
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAnvilPrepare(event: PrepareAnvilEvent) {
        val player = event.viewers.getOrNull(0) as? Player ?: return
        val generation = (latestPreviewGeneration[player.uniqueId] ?: 0) + 1
        latestPreviewGeneration[player.uniqueId] = generation
        renderedPreviewGeneration.remove(player.uniqueId)
        val permanenceCurse = EcoEnchants.getByID("permanence_curse")
        val leftItem = event.inventory.getItem(0)
        val rightItem = event.inventory.getItem(1)
        if (permanenceCurse != null) {
            if ((leftItem != null && leftItem.fast().getEnchants(true).containsKey(permanenceCurse.enchantment)) ||
                (rightItem != null && rightItem.fast().getEnchants(true).containsKey(permanenceCurse.enchantment))
            ) {
                event.result = null
                event.inventory.setItem(2, null)
                restoreClientGameMode(player)
                return
            }
        }

        if (plugin.getProxy(OpenInventoryProxy::class.java)
                .getOpenInventory(player)::class.java == anvilGuiClass
        ) {
            return
        }

        event.result = null
        event.inventory.setItem(2, null)

        // The merge is rebuilt on the scheduler below. Clear the old vanilla
        // cost while there is no result so a previous preview cannot be shown
        // with a different pickup cost during that rebuild.
        val configuredMaxRepairCost = plugin.configYml.getInt("anvil.max-repair-cost").coerceAtLeast(1)
        event.inventory.maximumRepairCost = configuredMaxRepairCost + 1
        event.inventory.repairCost = 0
        event.view.maximumRepairCost = configuredMaxRepairCost + 1
        event.view.repairCost = 0

        plugin.scheduler.run {
            if (latestPreviewGeneration[player.uniqueId] != generation) {
                return@run
            }

            val left = event.inventory.getItem(0)?.clone()
            val old = left?.clone()
            val right = event.inventory.getItem(1)?.clone()

            val result = doMerge(
                left,
                right,
                @Suppress("REMOVAL", "DEPRECATION")
                event.inventory.renameText ?: "",
                player
            )

            if (result == FAIL) {
                restoreClientGameMode(player)
                return@run
            }

            event.result = null
            event.inventory.setItem(2, null)

            val price = result.xp ?: 0
            val outItem = result.result ?: ItemStack(Material.AIR)

            val oldLeft = event.inventory.getItem(0)

            if (oldLeft == null || oldLeft.type != outItem.type) {
                restoreClientGameMode(player)
                return@run
            }

            if (left == old) {
                restoreClientGameMode(player)
                return@run
            }

            val cost = price

            // Cost could be less than zero at times, so I include that here.
            if (cost <= 0) {
                restoreClientGameMode(player)
                return@run
            }

            /*
            Transplanted anti-dupe bodge from pre-recode.
             */
            val leftEnchants = left?.fast()?.getEnchants(true) ?: emptyMap()
            val outEnchants = outItem.fast().getEnchants(true)

            if (event.inventory.getItem(1) == null && leftEnchants != outEnchants) {
                restoreClientGameMode(player)
                return@run
            }

            if (plugin.configYml.getBool("anvil.use-rework-penalty") && !result.onlyRename) {
                val repairCost = outItem.fast().repairCost
                outItem.fast().repairCost = (repairCost + 1) * 2 - 1
            }

            val maxRepairCost = plugin.configYml.getInt("anvil.max-repair-cost").coerceAtLeast(1)

            if (latestPreviewGeneration[player.uniqueId] != generation) {
                return@run
            }

            // Keep the value used by the client and by the server's anvil
            // pickup check identical.  Setting only InventoryView.repairCost
            // can leave the underlying AnvilInventory with the vanilla cost
            // after another prepare pass, which makes the displayed level
            // differ from the level actually required on click.
            val finalCost = cost.coerceAtMost(maxRepairCost)
            event.inventory.maximumRepairCost = maxRepairCost + 1
            event.inventory.repairCost = finalCost
            event.view.maximumRepairCost = maxRepairCost + 1
            event.view.repairCost = finalCost

            if (latestPreviewGeneration[player.uniqueId] != generation) {
                return@run
            }

            event.result = outItem
            event.inventory.setItem(2, outItem)
            renderedPreviewGeneration[player.uniqueId] = generation
            updateExpensiveCostBypass(player, cost >= VANILLA_TOO_EXPENSIVE_COST)
        }
    }

    private fun doMerge(
        left: ItemStack?,
        right: ItemStack?,
        itemName: String,
        player: Player
    ): AnvilResult {
        if (left == null || left.type == Material.AIR) {
            return FAIL
        }

        var experience = if (plugin.configYml.getBool("anvil.use-rework-penalty")) {
            left.fast().repairCost.toDouble()
        } else {
            0.0
        }
        experience += plugin.configYml.getInt("anvil.rename-cost")

        val formattedItemName = if (player.hasPermission("ecoenchants.anvil.color")) {
            StringUtils.format(itemName)
        } else {
            @Suppress("DEPRECATION")
            org.bukkit.ChatColor.stripColor(itemName)
        }.let { if (it.isNullOrEmpty()) left.fast().displayName else it }

        if (right == null || right.type == Material.AIR) {
            if (left.fast().displayName == formattedItemName) {
                return FAIL
            }

            left.fast().displayName =
                formattedItemName.let { "§o$it" } // Not a great way to make it italic, but it works

            return AnvilResult(left, experience.roundToInt(), onlyRename = true)
        }

        // Vanilla only merges an enchanted book into an item with durability or into
        // another enchanted book; a plain minecraft:book is not a valid target. Letting
        // it through enchants the *whole* left stack, yields a non-vanilla "book with
        // enchantments" item, and that item is then accepted as a sacrifice again -
        // one enchanted book is enough to duplicate its enchantments onto a whole stack.
        if (left.type == Material.BOOK && right.fast().getEnchants(true).isNotEmpty()) {
            return FAIL
        }

        val leftMeta = left.itemMeta
        val rightMeta = right.itemMeta

        var unitRepairCost = 0

        // Unit repair
        if (left.type != right.type) {
            if (right.type.canUnitRepair(left.type) && leftMeta is Damageable) {
                val perUnit = ceil(left.type.maxDurability / 4.0).toInt()

                val max = ceil(leftMeta.damage.toDouble() / perUnit).toInt()
                val toDeduct = min(max, right.amount)

                unitRepairCost = toDeduct

                if (toDeduct <= 0) {
                    return FAIL
                } else {
                    val newDamage = leftMeta.damage - toDeduct * perUnit
                    leftMeta.damage = newDamage.coerceAtLeast(0) // Prevent negative damage

                    right.amount -= toDeduct
                    experience += plugin.configYml.getInt("anvil.repair-cost")
                }
            } else {
                if (right.type != Material.ENCHANTED_BOOK) {
                    return FAIL
                }
            }
        }

        left.fast().displayName = formattedItemName.let { "§o$it" } // Same again, it works though

        val leftEnchants = left.fast().getEnchants(true)
        val rightEnchants = right.fast().getEnchants(true)

        val outEnchants = leftEnchants.toMutableMap()
        val allowUnsafeLevel = plugin.configYml.getBool("anvil.allow-unsafe-level")
        val blockOnUnsafeLevel = plugin.configYml.getBool("anvil.block-on-unsafe-level")
        val allowUnsafeCombine = plugin.configYml.getBool("anvil.allow-unsafe-combine")
        val enchantLimit = if (left.type == Material.BOOK || left.type == Material.ENCHANTED_BOOK) {
            plugin.configYml.getInt("anvil.book-enchant-limit")
        } else {
            plugin.configYml.getInt("anvil.enchant-limit")
        }.infiniteIfNegative()

        for ((enchant, level) in rightEnchants) {
            val wrapped = enchant.wrap()
            if (!wrapped.isEnabled) {
                continue
            }

            if (outEnchants.containsKey(enchant)) {
                val currentLevel = outEnchants[enchant]!!
                val maxLevel = wrapped.maximumLevel.coerceAtLeast(1)
                outEnchants[enchant] = if (level == currentLevel) {
                    if (level >= maxLevel && allowUnsafeCombine) {
                        level + 1
                    } else {
                        min(maxLevel, level + 1)
                    }
                } else {
                    val highestLevel = max(level, currentLevel)
                    if (highestLevel > maxLevel && !allowUnsafeLevel) {
                        if (blockOnUnsafeLevel) {
                            return FAIL
                        }
                        maxLevel
                    } else {
                        highestLevel
                    }
                }
            } else {
                // Running .wrap() to use EcoEnchantLike canEnchantItem logic
                if (wrapped.canEnchantItem(left, outEnchants.keys)) {
                    if (outEnchants.size < enchantLimit) {
                        outEnchants[enchant] = level
                        experience += plugin.configYml.getInt("anvil.new-enchant-extra-cost")
                    }
                }
            }
        }

        // Item repair - extra check for unit repair cost to prevent weird damage
        // Enchanted books seem to be damageable? Not quite sure why. Anyway, there's an extra check.
        if (leftMeta is Damageable && rightMeta is Damageable && unitRepairCost == 0 && rightMeta !is EnchantmentStorageMeta) {
            val maxDamage = left.type.maxDurability.toInt()
            val leftDurability = maxDamage - leftMeta.damage
            val rightDurability = maxDamage - rightMeta.damage
            val repairBonus = ceil(maxDamage * 0.12).toInt()
            val damage = maxDamage - min(maxDamage, leftDurability + rightDurability + repairBonus)

            leftMeta.damage = damage.coerceAtLeast(0) // Prevent negative damage
            experience += plugin.configYml.getInt("anvil.combine-repair-cost")
        }

        if (leftEnchants != outEnchants) {
            val basePerLevel = plugin.configYml.getDouble("anvil.enchant-cost-per-level")
            for ((enchant, level) in outEnchants) {
                val previousLevel = leftEnchants[enchant] ?: 0
                val addedLevels = level - previousLevel
                if (addedLevels > 0) {
                    experience += basePerLevel / enchant.maxLevel.coerceAtLeast(1) * addedLevels
                }
            }
        }

        if (!validateAndNormalizeAnvilEnchants(outEnchants, leftEnchants)) {
            return FAIL
        }

        if (leftMeta is EnchantmentStorageMeta) {
            for (storedEnchant in leftMeta.storedEnchants.keys.toSet()) {
                leftMeta.removeStoredEnchant(storedEnchant)
            }

            for ((enchant, level) in outEnchants) {
                leftMeta.addStoredEnchant(enchant, level, true)
            }
        } else {
            for (storedEnchant in leftMeta.enchants.keys.toSet()) {
                leftMeta.removeEnchant(storedEnchant)
            }

            for ((enchant, level) in outEnchants) {
                leftMeta.addEnchant(enchant, level, true)
            }
        }

        left.itemMeta = leftMeta

        return AnvilResult(left, finalAiyatsbusCost(experience, player))
    }

    private fun validateAndNormalizeAnvilEnchants(
        enchants: MutableMap<Enchantment, Int>,
        originalEnchants: Map<Enchantment, Int>
    ): Boolean {
        val allowUnsafeLevel = plugin.configYml.getBool("anvil.allow-unsafe-level")
        val blockOnUnsafeLevel = plugin.configYml.getBool("anvil.block-on-unsafe-level")

        for ((enchant, level) in enchants.toMap()) {
            val wrapped = enchant.wrap()
            if (!wrapped.isEnabled || level < 1) {
                return false
            }

            val maxLevel = wrapped.maximumLevel.coerceAtLeast(1)
            if (level > maxLevel && !allowUnsafeLevel) {
                // Existing unsafe levels must not make unrelated, otherwise-valid
                // book applications impossible. They are preserved, but this merge
                // may not use them to create or increase another unsafe level.
                val originalLevel = originalEnchants[enchant] ?: 0
                if (originalLevel > maxLevel && level <= originalLevel) {
                    continue
                }
                if (blockOnUnsafeLevel) {
                    return false
                }
                enchants[enchant] = maxLevel
            }
        }

        val enchantments = enchants.keys.toList()
        for (enchant in enchantments) {
            val custom = enchant.wrap() as? EcoEnchant ?: continue
            if (!custom.hasRequiredEnchantments(enchantments)) {
                return false
            }
        }

        for (firstIndex in enchantments.indices) {
            for (secondIndex in firstIndex + 1 until enchantments.size) {
                if (hasAnvilConflict(enchantments[firstIndex], enchantments[secondIndex])) {
                    return false
                }
            }
        }

        return true
    }

    private fun hasAnvilConflict(first: Enchantment, second: Enchantment): Boolean {
        if (first.conflictsWith(second) || second.conflictsWith(first)) {
            return true
        }

        val firstCustom = first.wrap() as? EcoEnchant
        val secondCustom = second.wrap() as? EcoEnchant
        return firstCustom?.conflictsWithDirectly(second) == true ||
                secondCustom?.conflictsWithDirectly(first) == true
    }

    private fun finalAiyatsbusCost(origin: Double, player: Player): Int {
        val discounts = buildList {
            add(0)
            if (player.hasPermission(plugin.configYml.getString("anvil.vip-permission"))) {
                add(plugin.configYml.getInt("anvil.vip-discount"))
            }
            if (player.hasPermission(plugin.configYml.getString("anvil.svip-permission"))) {
                add(plugin.configYml.getInt("anvil.svip-discount"))
            }
        }
        val cost = origin.roundToInt() - discounts.max()

        return cost.coerceIn(1, plugin.configYml.getInt("anvil.max-repair-cost").coerceAtLeast(1))
    }
}

private fun is_1_21_11(): Boolean {
    return try {
        Material.IRON_SPEAR
        true
    } catch (_: NoSuchFieldError) {
        false
    }
}

fun Material.canUnitRepair(other: Material): Boolean {
    for ((units, repairable) in repair) {
        if (this in units) {
            return other in repairable
        }
    }

    return false
}
