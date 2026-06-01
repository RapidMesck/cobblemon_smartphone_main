# Mod API

Extend the Cobblemon Smartphone from your own mod using Kotlin or Java. The Mod API gives you full control — open custom GUIs, send network packets, integrate with other mods, and use the upgrade system.

## Adding the Dependency

### Gradle (Architectury Multi-Loader)

```gradle
// common/build.gradle
repositories {
    maven { url "https://maven.example.com/" } // Cobblemon Smartphone repository
}

dependencies {
    modImplementation("com.nbp:cobblemon-smartphone-common:1.0.8")
}
```

For Fabric/NeoForge specific subprojects, use the corresponding platform artifact.

## Quick Start: Registering an Action

### 1. Implement `SmartphoneAction`

```kotlin
package com.example.mymod.action

import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object HelloWorldAction : SmartphoneAction {
    override val id = "mymod:hello_world"
    override val texture = ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/buttons/hello.png")
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/buttons/hello_hover.png")

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.displayClientMessage(
            net.minecraft.network.chat.Component.literal("Hello from smartphone!"),
            false
        )
    }

    override fun isEnabled(): Boolean = true
}
```

### 2. Register the Action

In your mod's initialization (common code, called from both Fabric and NeoForge entry points):

```kotlin
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.example.mymod.action.HelloWorldAction

fun registerSmartphoneActions() {
    SmartphoneActionRegistry.register(HelloWorldAction)
}
```

Your button now appears in the smartphone GUI.

## `SmartphoneAction` Interface Reference

```kotlin
interface SmartphoneAction {
    val id: String                          // Unique ID: "namespace:path"
    val texture: ResourceLocation           // 36×36 PNG button texture
    val hoverTexture: ResourceLocation      // 36×36 PNG hover texture

    fun onClick()                           // Called when player clicks the button
    fun isEnabled(): Boolean = true         // Return false to hide the button
}
```

### `onClick()`

Called on the **client side** when the player clicks the button. Typical implementations:
- Send a network packet to the server
- Play a sound via `player.playSound(...)`
- Close the smartphone screen via `Minecraft.getInstance().setScreen(null)`

### `isEnabled()`

Called to determine if the button should appear. Return `false` to hide it. Common checks:
- Config flag enabled
- Required mod loaded
- Player has required item or upgrade

**Important**: `isEnabled()` runs on the **render thread**. Keep it fast — no network calls or file I/O.

## Server-Side Handling with Network Packets

Most actions need server-side logic (running commands, opening GUIs, etc.). Use network packets:

### 1. Create a Packet

Extend `CobblemonSmartphoneNetworkPacket`:

```kotlin
package com.example.mymod.network

import com.nbp.cobblemon_smartphone.network.packet.CobblemonSmartphoneNetworkPacket
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class MyActionPacket : CobblemonSmartphoneNetworkPacket<MyActionPacket> {
    companion object {
        val ID = smartphoneResource("mymod", "my_action")!!
        fun decode(buffer: RegistryFriendlyByteBuf) = MyActionPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // Write any data here
    }
}
```

### 2. Create a Handler

```kotlin
package com.example.mymod.network

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object MyActionHandler : ServerNetworkPacketHandler<MyActionPacket> {
    override fun handle(packet: MyActionPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            // Your server-side logic here
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("Executed on server!"),
                false
            )
        }
    }
}
```

### 3. Register the Packet

Create your own `NetworkManager` or register through your mod's packet system. See the [Cobblemon Network API](https://docs.cobblemon.com) for details on using `NetworkPacket` registration.

### 4. Call from Action

```kotlin
override fun onClick() {
    val player = Minecraft.getInstance().player ?: return
    player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
    MyActionPacket().sendToServer()
    Minecraft.getInstance().setScreen(null)
}
```

## Finding the Player's Smartphone

Use `SmartphoneHelper` to locate the smartphone a player currently has equipped (checks inventory, Trinkets, and Curios):

```kotlin
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper

// In your action's isEnabled():
val player = Minecraft.getInstance().player ?: return false
val smartphone = SmartphoneHelper.getSmartphone(player) ?: return false
// Now you can check NBT, upgrades, etc.
```

**Note**: For Fabric, `SmartphoneHelper` needs to be initialized with the platform-specific compat manager. If you are building your own mod that registers actions, this is already set up by the Cobblemon Smartphone mod during its init. You only need to set this if you are forking or embedding the API.

## Upgrade System

The upgrade system allows actions to be permanently unlocked via smithing table recipes. The smartphone stores upgrade state as NBT data.

### Key Classes

| Class | Package | Purpose |
|---|---|---|
| `SmartphoneUpgrade` | `com.nbp.cobblemon_smartphone.upgrade` | Data class defining an upgrade |
| `SmartphoneUpgradeRegistry` | `com.nbp.cobblemon_smartphone.upgrade` | Registry of all known upgrades |
| `hasUpgrade()` | `com.nbp.cobblemon_smartphone.upgrade` | Extension function on `ItemStack` |
| `addUpgrade()` | `com.nbp.cobblemon_smartphone.upgrade` | Extension function on `ItemStack` |
| `SimulatedItemUse` | `com.nbp.cobblemon_smartphone.upgrade` | Simulates using an item from another mod |

### Registering an Upgrade

Register your upgrade during mod initialization:

```kotlin
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgrade
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgradeRegistry
import net.minecraft.network.chat.Component

fun registerUpgrades() {
    SmartphoneUpgradeRegistry.register(
        SmartphoneUpgrade(
            id = "upgrade_my_feature",
            nbtKey = "upgrade_my_feature",
            requiredModId = "mymod",  // Optional: hide if mod not present
            displayName = Component.translatable("upgrade.mymod.my_feature")
        )
    )
}
```

### Checking Upgrade in isEnabled()

```kotlin
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft

override fun isEnabled(): Boolean {
    val player = Minecraft.getInstance().player ?: return false
    val smartphone = SmartphoneHelper.getSmartphone(player) ?: return false
    return smartphone.hasUpgrade("upgrade_my_feature")
}
```

### Checking Upgrade Server-Side

In your packet handler:

```kotlin
override fun handle(packet: MyPacket, server: MinecraftServer, player: ServerPlayer) {
    server.execute {
        val smartphone = SmartphoneHelper.getSmartphone(player)
        if (smartphone == null || !smartphone.hasUpgrade("upgrade_my_feature")) {
            player.displayClientMessage(
                Component.translatable("message.mymod.need_upgrade").withColor(0xfd0100),
                true
            )
            return@execute
        }
        // Proceed with action...
    }
}
```

### NBT Structure

```kotlin
import com.nbp.cobblemon_smartphone.upgrade.addUpgrade

// Adding an upgrade to an ItemStack:
smartphoneStack.addUpgrade("upgrade_my_feature")

// Checking:
smartphoneStack.hasUpgrade("upgrade_my_feature")  // returns Boolean
```

Internally stored as:

```
custom_data
  └── cobblemon_smartphone:upgrades
        └── upgrade_my_feature: 1b (true)
```

### Displaying Upgrades in Tooltips

If you registered upgrades with `SmartphoneUpgradeRegistry`, the smartphone item automatically shows installed upgrades in its tooltip (via `SmartphoneItem.appendHoverText()`). Use `displayName` in your `SmartphoneUpgrade` to provide a translatable component.

### Cumulative Upgrades

Multiple upgrades can coexist on one smartphone. Each smithing operation adds one upgrade without removing existing ones.

## SimulatedItemUse

When your action requires using an item from another mod (e.g., opening a specific GUI), use `SimulatedItemUse` to temporarily place a synthetic item in the player's hand, execute the use action, and restore the original hand.

### Generic API

```kotlin
import com.nbp.cobblemon_smartphone.upgrade.SimulatedItemUse
import net.minecraft.server.level.ServerPlayer

// In your server-side handler:
val success = SimulatedItemUse.simulate(
    player = player,
    itemPredicate = { id ->
        // Match items from a specific mod
        id.namespace == "other_mod" && id.path == "special_item"
    },
    useAction = { stack, p ->
        // How to use the item
        stack.item.use(p.level(), p, InteractionHand.MAIN_HAND)
    }
)
```

### Built-in Convenience Methods

`SimulatedItemUse` includes pre-built methods for common mods:

```kotlin
// PokeNav (CobbleNav)
SimulatedItemUse.usePokenav(player)    // Returns Boolean

// Waystones Warp Stone
SimulatedItemUse.useWaystone(player)   // Returns Boolean
```

### Writing Your Own Convenience Method

```kotlin
object MyModSimulations {
    fun useMyItem(player: ServerPlayer): Boolean {
        return SimulatedItemUse.simulate(
            player,
            itemPredicate = { id ->
                id.namespace == "other_mod" && id.path.endsWith("_my_item")
            },
            useAction = { stack, p ->
                stack.item.use(p.level(), p, InteractionHand.MAIN_HAND)
            }
        )
    }
}
```

### Safety

- **Original hand is always restored** — uses `try/finally`
- **Fails gracefully** — returns `false` if item not found or exception occurs
- **No class references to optional mods** — items found by registry key matching only

## Creating the Smithing Recipe

For upgrades, create a smithing recipe JSON so players can install the upgrade:

```json
{
  "type": "minecraft:smithing_transform",
  "fabric:load_conditions": [
    { "condition": "fabric:all_mods_loaded", "values": ["mymod"] }
  ],
  "neoforge:conditions": [
    { "type": "neoforge:mod_loaded", "modid": "mymod" }
  ],
  "template": { "item": "cobblemon:upgrade" },
  "base": { "tag": "cobblemon_smartphone:smartphones" },
  "addition": { "item": "mymod:my_upgrade_item" },
  "result": {
    "id": "cobblemon_smartphone:red_smartphone",
    "count": 1,
    "components": {
      "minecraft:custom_data": {
        "cobblemon_smartphone:upgrades": {
          "upgrade_my_feature": true
        }
      }
    }
  }
}
```

### How the Recipe Works

1. The recipe uses vanilla `minecraft:smithing_transform` so JEI/EMI/REI display it automatically
2. `base` uses the tag `cobblemon_smartphone:smartphones` which includes all 16 smartphone colors
3. The `result.id` is `red_smartphone` — this is a **placeholder** for recipe viewers
4. The **Mixin on `SmithingTransformRecipe.assemble()`** intercepts crafting and copies the actual base item (preserving color) to the result with the upgrade NBT
5. Recipe conditions (`fabric:load_conditions` / `neoforge:conditions`) hide the recipe when your mod isn't installed

This means **one recipe JSON handles all 16 smartphone colors** — you don't need 16 separate recipe files.

## Complete Example: Full Action with Upgrade + Simulated Item

```kotlin
package com.example.mymod.action

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.upgrade.SimulatedItemUse
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgrade
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgradeRegistry
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.example.mymod.network.MyActionPacket
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

object MyUpgradedAction : SmartphoneAction {
    private const val UPGRADE_KEY = "upgrade_my_feature"

    override val id = "mymod:my_action"
    override val texture = ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/buttons/my_action.png")
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/buttons/my_action_hover.png")

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        MyActionPacket().sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    override fun isEnabled(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val smartphone = SmartphoneHelper.getSmartphone(player) ?: return false
        return smartphone.hasUpgrade(UPGRADE_KEY)
    }
}

// --- In your mod init ---

fun init() {
    // Register the upgrade
    SmartphoneUpgradeRegistry.register(
        SmartphoneUpgrade(
            id = UPGRADE_KEY,
            nbtKey = UPGRADE_KEY,
            requiredModId = "mymod",
            displayName = Component.translatable("upgrade.mymod.my_feature")
        )
    )

    // Register the action
    SmartphoneActionRegistry.register(MyUpgradedAction)
}
```

```kotlin
// --- Server-side handler ---
package com.example.mymod.network

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.nbp.cobblemon_smartphone.upgrade.SimulatedItemUse
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object MyActionHandler : ServerNetworkPacketHandler<MyActionPacket> {
    override fun handle(packet: MyActionPacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            // Check upgrade
            val smartphone = SmartphoneHelper.getSmartphone(player)
            if (smartphone == null || !smartphone.hasUpgrade("upgrade_my_feature")) {
                player.displayClientMessage(
                    Component.translatable("message.mymod.need_upgrade").withColor(0xfd0100),
                    true
                )
                return@execute
            }

            // Try physical item first (backward compat)
            val physicalItem = player.inventory.items.firstOrNull {
                it.item == MyModItems.MY_ITEM.get()
            }
            if (physicalItem != null) {
                physicalItem.item.use(player.level(), player, InteractionHand.MAIN_HAND)
                return@execute
            }

            // Fall back to simulated item use
            if (!SimulatedItemUse.simulate(player,
                { id -> id.namespace == "other_mod" && id.path == "special_item" },
                { stack, p -> stack.item.use(p.level(), p, InteractionHand.MAIN_HAND) }
            )) {
                player.displayClientMessage(
                    Component.translatable("message.mymod.action_failed").withColor(0xfd0100),
                    true
                )
            }
        }
    }
}
```

## Utility Classes Reference

### `SmartphoneHelper` (`com.nbp.cobblemon_smartphone.util`)

```kotlin
object SmartphoneHelper {
    // Set by platform init. Fallback to inventory search.
    var getSmartphoneImpl: ((Player) -> ItemStack?)?

    // Find player's smartphone (Trinkets → Curios → Inventory)
    fun getSmartphone(player: Player): ItemStack?
}
```

### `hasUpgrade()` / `addUpgrade()` (`com.nbp.cobblemon_smartphone.upgrade`)

```kotlin
// Extension functions on ItemStack
fun ItemStack.hasUpgrade(nbtKey: String): Boolean
fun ItemStack.addUpgrade(nbtKey: String)
fun ItemStack.isSmartphone(): Boolean
```

### `SimulatedItemUse` (`com.nbp.cobblemon_smartphone.upgrade`)

```kotlin
object SimulatedItemUse {
    fun simulate(
        player: ServerPlayer,
        itemPredicate: (ResourceLocation) -> Boolean,
        useAction: (ItemStack, ServerPlayer) -> Unit
    ): Boolean

    fun usePokenav(player: ServerPlayer): Boolean
    fun useWaystone(player: ServerPlayer): Boolean
}
```

### `SmartphoneUpgradeRegistry` (`com.nbp.cobblemon_smartphone.upgrade`)

```kotlin
object SmartphoneUpgradeRegistry {
    fun register(upgrade: SmartphoneUpgrade)
    fun getUpgrade(id: String): SmartphoneUpgrade?
    fun getUpgradeByNbtKey(nbtKey: String): SmartphoneUpgrade?
    fun getAllUpgrades(): Collection<SmartphoneUpgrade>
    fun getInstalledUpgrades(stack: ItemStack): List<SmartphoneUpgrade>
}
```
