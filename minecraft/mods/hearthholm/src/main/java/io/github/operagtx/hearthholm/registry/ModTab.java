package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class ModTab {
    private ModTab() {}

    public static void init() {
        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.hearthholm"))
                .icon(() -> new ItemStack(ModBlocks.TOWN_HALL_DESK))
                .displayItems((params, output) -> {
                    for (Block b : ModBlocks.TAB_ORDER) output.accept(b);
                    output.accept(ModItems.HEARTH_MARK);
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Hearthholm.id("main"), tab);
    }
}
