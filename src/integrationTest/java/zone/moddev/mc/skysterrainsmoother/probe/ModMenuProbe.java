package zone.moddev.mc.skysterrainsmoother.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.gui.modlist.ModDisplayInfo;
import net.neoforged.neoforge.client.gui.modlist.ModListScreen;

/** Uses NeoForge's actual Mods list and Config button, without desktop automation. */
final class ModMenuProbe {
    static Screen open(Minecraft minecraft, String modId, Screen parent) {
        ModListScreen mods = ModListScreen.create(parent);
        minecraft.gui.setScreen(mods);
        mods.tick();
        AbstractSelectionList<?> list = (AbstractSelectionList<?>) field(ModListScreen.class, mods, "displayList");
        Object selected = null;
        for (Object entry : list.children()) {
            ModDisplayInfo info = (ModDisplayInfo) field(entry.getClass(), entry, "displayInfo");
            if (info.id().equals(modId)) {
                RuntimeProbe.require(!info.displayName().getString().isBlank()
                        && !info.description().getString().isBlank(), "Player-facing mod metadata exists");
                selected = entry;
                break;
            }
        }
        RuntimeProbe.require(selected != null, "The Mods list includes " + modId);
        try {
            Method select = list.getClass().getDeclaredMethod("setSelected", selected.getClass());
            select.setAccessible(true);
            select.invoke(list, selected);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot select the native mod entry", failure);
        }
        Object panel = field(ModListScreen.class, mods, "displayPanel");
        Button button = (Button) field(panel.getClass(), panel, "configButton");
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
