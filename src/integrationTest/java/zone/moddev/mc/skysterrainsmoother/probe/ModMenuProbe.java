package zone.moddev.mc.skysterrainsmoother.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.client.gui.widget.ModListWidget;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;

/** Uses Forge's actual Mods list and Config button, without desktop automation. */
final class ModMenuProbe {
    static Screen open(Minecraft minecraft, String modId, Screen parent) {
        ModListScreen mods = new ModListScreen(parent);
        minecraft.gui.setScreen(mods);
        ModListWidget list = (ModListWidget) field(ModListScreen.class, mods, "modList");
        Object selected = null;
        for (Object entry : list.children()) {
            try {
                Method infoMethod = entry.getClass().getDeclaredMethod("getInfo");
                infoMethod.setAccessible(true);
                ModInfo info = (ModInfo) infoMethod.invoke(entry);
                if (info.getModId().equals(modId)) {
                    RuntimeProbe.require(!info.getDisplayName().trim().isEmpty()
                            && !info.getDescription().trim().isEmpty(), "Player-facing mod metadata exists");
                    selected = entry;
                    break;
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot inspect the native mod entry", failure);
            }
        }
        RuntimeProbe.require(selected != null, "The Mods list includes " + modId);
        try {
            Method select = ModListScreen.class.getDeclaredMethod("setSelected", selected.getClass());
            select.invoke(mods, selected);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot select the native mod entry", failure);
        }
        Button button = (Button) field(ModListScreen.class, mods, "configButton");
        RuntimeProbe.require(button.visible && button.active, "The Config button is available for " + modId);
        button.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
        RuntimeProbe.require(minecraft.gui.screen() != mods && minecraft.gui.screen() != null,
                "The Config button opens " + modId);
        return minecraft.gui.screen();
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
