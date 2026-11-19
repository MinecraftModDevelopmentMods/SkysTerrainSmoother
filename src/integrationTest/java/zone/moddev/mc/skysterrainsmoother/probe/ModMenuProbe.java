package zone.moddev.mc.skysterrainsmoother.probe;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.client.gui.widget.ModListWidget;

/** Uses NeoForge's actual Mods list and Config button, without desktop automation. */
final class ModMenuProbe {
    static Screen open(Minecraft minecraft, String modId, Screen parent) {
        ModListScreen mods = new ModListScreen(parent);
        minecraft.setScreen(mods);
        mods.tick();
        ModListWidget list = (ModListWidget) field(ModListScreen.class, mods, "modList");
        ModListWidget.ModEntry selected = null;
        for (ModListWidget.ModEntry entry : list.children()) {
            var info = entry.getInfo();
            if (info.getModId().equals(modId)) {
                RuntimeProbe.require(!info.getDisplayName().isBlank()
                        && !info.getDescription().isBlank(), "Player-facing mod metadata exists");
                selected = entry;
                break;
            }
        }
        RuntimeProbe.require(selected != null, "The Mods list includes " + modId);
        mods.setSelected(selected);
        Button button = (Button) field(ModListScreen.class, mods, "configButton");
        RuntimeProbe.require(button.visible && button.active, "The Config button is available for " + modId);
        button.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
        RuntimeProbe.require(minecraft.screen != mods && minecraft.screen != null,
                "The Config button opens " + modId);
        return minecraft.screen;
    }

    static Object field(Class<?> owner, Object instance, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect native screen field " + name, failure);
        }
    }

    private ModMenuProbe() {
    }
}
