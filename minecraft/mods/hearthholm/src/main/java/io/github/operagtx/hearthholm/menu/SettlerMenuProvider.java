package io.github.operagtx.hearthholm.menu;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/** Opens {@link SettlerMenu} for a player; the opening data is the settler's entity id. */
public record SettlerMenuProvider(SettlerEntity settler) implements ExtendedMenuProvider<Integer> {
    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.hearthholm.inventory_title", settler.getName());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SettlerMenu(id, inv, settler.getId(), settler);
    }

    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return settler.getId();
    }
}
