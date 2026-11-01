package zone.moddev.mc.skysterrainsmoother.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraftforge.fml.client.gui.GuiModList;
import net.minecraftforge.fml.client.gui.GuiSlotModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;

/** Uses Forge's actual Mods list and Config button, without desktop automation. */
final class ModMenuProbe {
    static Screen open(Minecraft minecraft, String modId, Screen parent) {
        GuiModList mods = new GuiModList(parent);
        minecraft.displayGuiScreen(mods);
        GuiSlotModList list = (GuiSlotModList) field(GuiModList.class, mods, "modList");
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
            Method select = GuiModList.class.getDeclaredMethod("setSelected", selected.getClass());
            select.invoke(mods, selected);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot select the native mod entry", failure);
        }
        ConfigScreenProbe.render(minecraft, mods, 0, 0);
        ConfigScreenProbe.screenshot(minecraft, modId + "-mod-info.png");
        Button button = (Button) field(GuiModList.class, mods, "configButton");
        RuntimeProbe.require(button.visible && button.active, "The Config button is available for " + modId);
        button.onPress();
        RuntimeProbe.require(minecraft.currentScreen != mods && minecraft.currentScreen != null,
                "The Config button opens " + modId);
        return minecraft.currentScreen;
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
