# Cobblemon Smartphone

A Cobblemon addon that adds smartphones to enhance your Pokémon training experience in Cobblemon!


# ⚙️ [NEW] Cobblemon Smartphone Mod Addon API Example

This guide shows how to **create and register a custom smartphone action** using the Cobblemon Smartphone API in your own Fabric addon mod.

---

## 1. Add Cobblemon Smartphone as a Dependency

- Place the `cobblemon_smartphone` JAR in your `libs` folder.
- In your `build.gradle`:

```groovy
repositories {
    flatDir { dirs 'libs' }
}

dependencies {
    modImplementation name: 'cobblemon_smartphone-fabric-1.0.3' // Adjust for your version
}
```

- In your `fabric.mod.json`:

```json
"depends": {
  "fabricloader": ">=0.14",
  "minecraft": ">=1.20",
  "cobblemon_smartphone": "*"
}
```

---

## 2. Create a Smartphone Action

Create a Kotlin file (for example, `MyAction.kt`) in your addon mod:

```kotlin
package com.example.myaddon

import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object MyAction : SmartphoneAction {
    override val id = "myaddon:super_action"
    override val texture = ResourceLocation("myaddon", "textures/gui/buttons/super_action.png")
    override val hoverTexture = ResourceLocation("myaddon", "textures/gui/buttons/super_action_hover.png")

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        // Your action code here!
        player.sendMessage(net.minecraft.network.chat.Component.literal("Super Action!"), false)
        Minecraft.getInstance().setScreen(null) // Closes the smartphone screen
    }

    override fun isEnabled(): Boolean = true
}
```

---

## 3. Register Your Action

In your mod initializer (Fabric):

```kotlin
package com.example.myaddon

import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import net.fabricmc.api.ModInitializer

object MyAddonMod : ModInitializer {
    override fun onInitialize() {
        SmartphoneActionRegistry.register(MyAction)
    }
}
```

Or (if using a class):

```kotlin
class MyAddonMod : ModInitializer {
    override fun onInitialize() {
        SmartphoneActionRegistry.register(MyAction)
    }
}
```

---

## 4. Add Your Button Textures

- Place `super_action.png` and `super_action_hover.png` in:
  ```
  src/main/resources/assets/myaddon/textures/gui/buttons/
  ```
- Replace `myaddon` with your mod ID and update the ResourceLocation in your action code if needed.

---

## 5. Run and Test

- Launch Minecraft with both Cobblemon Smartphone and your addon in the `mods` folder.
- Open the smartphone in-game. Your custom action should appear as a new button!

---

## ℹ️ More Info

- You can register as many actions as you want.
- Actions are shown in the order they are registered.
- For advanced use, check the mod’s source for more API details.

---

## Features
The smartphone comes with three main features that can be toggled in the configuration:

1. Pokémon Healing - Quick access to heal your Pokémon team
2. PC Access - Open your Pokémon PC storage system on the go
3. Cloud Storage - Access your Ender Chest anywhere

## Available Colors
Express your style with different smartphone colors:
- Red
- Yellow
- Green
- Blue
- Pink
- Black
- White

  1.0.2 or later

- Brown
- Cyan
- Gray
- Light gray
- Light blue
- Lime
- Magenta
- Orange
- Purple

## Configuration Options

### Feature Toggles
You can enable or disable any of the main features in the config file:
- `enableHeal` - Toggle the healing feature
- `enablePC` - Toggle PC access
- `enableCloud` - Toggle Ender Chest access

### Cooldown Settings
Customize the cooldown times for each feature:
- Healing Button: 60 seconds (default)
- PC Access: 5 seconds (default)
- Cloud Storage: 5 seconds (default)

## Usage
- Smartphones can be found in the Tools and Utilities tab in the creative inventory
- Right-click while holding the smartphone to open its interface
- Click on the desired function button to activate it
- The interface includes hover effects for better user experience

## Technical Details
- Built for the Fabric mod loader
- Requires Cobblemon as a dependency
- Configurations are stored in `config/cobblemon_smartphone.json`
- Features server-side protection with cooldown systems

The mod seamlessly integrates with Cobblemon to provide convenient mobile access to essential Pokémon trainer functionalities while maintaining game balance through configurable cooldown periods.