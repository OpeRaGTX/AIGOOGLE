package io.github.operagtx.hearthholm.registry;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.menu.SettlerMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Inventory;

/** Wave J1: menu types. The opening data is the entity id of the settler. */
public final class ModMenus {
    public static ExtendedMenuType<SettlerMenu, Integer> SETTLER;

    private ModMenus() {}

    public static void init() {
        SETTLER = Registry.register(BuiltInRegistries.MENU, Hearthholm.id("settler_inventory"),
                new ExtendedMenuType<SettlerMenu, Integer>((id, inv, entityId) -> create(id, inv, entityId), ByteBufCodecs.VAR_INT));
    }

    /** The factory runs on the client only (the server builds the menu in the provider with the real entity). */
    private static SettlerMenu create(int id, Inventory inv, int entityId) {
        return new SettlerMenu(id, inv, entityId, null);
    }
}
