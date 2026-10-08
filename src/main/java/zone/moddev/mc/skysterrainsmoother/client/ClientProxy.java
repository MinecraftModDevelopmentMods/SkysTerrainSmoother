package zone.moddev.mc.skysterrainsmoother.client;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;

public final class ClientProxy extends CommonProxy {
    @Override public void preInit() {
        for(int variant=0;variant<2;variant++)ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(SandContent.slab),variant*2,
                new ModelResourceLocation(new ResourceLocation("skysterrainsmoother",variant==0?"sand_slab":"red_sand_slab"),"inventory"));
    }
}
