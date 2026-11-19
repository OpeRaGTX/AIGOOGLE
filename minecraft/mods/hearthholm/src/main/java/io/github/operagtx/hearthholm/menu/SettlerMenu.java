package io.github.operagtx.hearthholm.menu;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.registry.ModMenus;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.Nullable;

/**
 * Wave J1: the inventory of a settler, like a horse's. Slots: 0-17 the pockets (the settler's real {@code SimpleContainer}),
 * 18-23 equipment (helmet, chest, legs, boots, main hand, off hand), then the player's inventory (27) and hotbar (9).
 * On the server the containers are the settler's own, so every change is immediate; on the client they are plain mirrors that
 * the menu protocol keeps in sync. While the menu is open the settler is held still (see {@link SettlerEntity#holdForInventory}).
 */
public final class SettlerMenu extends AbstractContainerMenu {
    public static final int POCKETS = 18, EQUIP_START = 18, EQUIP = 6, PLAYER_START = 24, PLAYER_END = 60;
    /** Reach of the screen in blocks (squared compare below). */
    public static final double REACH = 6.5;
    public static final EquipmentSlot[] EQUIP_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    /** Slot positions relative to the panel (the background texture is drawn to match). */
    public static final int[][] EQUIP_POS = {{8, 20}, {26, 20}, {44, 20}, {62, 20}, {108, 20}, {126, 20}};
    public static final int POCKET_X = 8, POCKET_Y = 50, PLAYER_Y = 112, HOTBAR_Y = 170, WIDTH = 176, HEIGHT = 194;

    private final @Nullable SettlerEntity settler;
    private final int entityId;
    private final Container pockets;
    private final Container equipment;
    private final Player viewer;

    /** Client side (and the server through the provider): {@code settler} is null on the client. */
    public SettlerMenu(int containerId, Inventory playerInv, int entityId, @Nullable SettlerEntity settler) {
        super(ModMenus.SETTLER, containerId);
        this.entityId = entityId;
        this.viewer = playerInv.player;
        this.settler = settler;
        this.pockets = settler != null ? settler.inventory() : new SimpleContainer(POCKETS);
        this.equipment = settler != null ? new EquipmentContainer(settler) : new SimpleContainer(EQUIP);
        for (int i = 0; i < POCKETS; i++) addSlot(new Slot(pockets, i, POCKET_X + (i % 9) * 18, POCKET_Y + (i / 9) * 18));
        for (int i = 0; i < EQUIP; i++) addSlot(new EquipSlot(equipment, i, EQUIP_SLOTS[i], EQUIP_POS[i][0], EQUIP_POS[i][1]));
        addStandardInventorySlots(playerInv, 8, PLAYER_Y);
    }

    public int entityId() { return entityId; }

    public @Nullable SettlerEntity settler() { return settler; }

    /** Server rule for every tick the menu is open: alive, still hired, close enough and the player may manage the town. */
    @Override
    public boolean stillValid(Player player) {
        if (settler == null) return true;   // client mirror
        if (!settler.isAlive() || settler.isRemoved() || settler.isLeaving()) return false;
        if (player.distanceToSqr(settler) > REACH * REACH || settler.level() != player.level()) return false;
        Settlement s = settler.settlement();
        return s != null && player instanceof ServerPlayer sp && SettlementService.canManage(sp, s);
    }

    @Override
    public void broadcastChanges() {
        if (settler != null && settler.isAlive()) settler.holdForInventory(viewer);
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (settler != null) settler.releaseInventory();
    }

    /** Armor type of a stack (head/chest/legs/feet) or null. */
    public static @Nullable EquipmentSlot armorSlot(ItemStack st) {
        Equippable eq = st.get(DataComponents.EQUIPPABLE);
        return eq != null && eq.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? eq.slot() : null;
    }

    public static boolean isToolOrWeapon(ItemStack st) {
        return st.is(ItemTags.SWORDS) || st.is(ItemTags.AXES) || st.is(ItemTags.PICKAXES) || st.is(ItemTags.SHOVELS) || st.is(ItemTags.HOES)
                || st.is(Items.BOW) || st.is(Items.CROSSBOW) || st.is(Items.FISHING_ROD) || st.is(Items.SHEARS) || st.is(Items.TRIDENT);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack ret = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ret;
        ItemStack st = slot.getItem();
        ret = st.copy();
        if (index < PLAYER_START) {
            // settler -> player
            if (!moveItemStackTo(st, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else {
            // player -> settler: armor to its slot, tool/weapon to the main hand, shield to the off hand, everything else to the pockets
            boolean moved = false;
            EquipmentSlot armor = armorSlot(st);
            if (armor != null) {
                int i = EQUIP_START + java.util.Arrays.asList(EQUIP_SLOTS).indexOf(armor);
                if (!slots.get(i).hasItem()) moved = moveItemStackTo(st, i, i + 1, false);
            } else if (st.is(Items.SHIELD)) {
                if (!slots.get(EQUIP_START + 5).hasItem()) moved = moveItemStackTo(st, EQUIP_START + 5, EQUIP_START + 6, false);
            } else if (isToolOrWeapon(st)) {
                if (!slots.get(EQUIP_START + 4).hasItem()) moved = moveItemStackTo(st, EQUIP_START + 4, EQUIP_START + 5, false);
            }
            if (!moved && !moveItemStackTo(st, 0, POCKETS, false)) return ItemStack.EMPTY;
        }
        if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (st.getCount() == ret.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, st);
        return ret;
    }

    /** One equipment slot: only fitting gear, one item at a time. */
    private static final class EquipSlot extends Slot {
        private final EquipmentSlot type;

        EquipSlot(Container c, int i, EquipmentSlot type, int x, int y) {
            super(c, i, x, y);
            this.type = type;
        }

        @Override
        public boolean mayPlace(ItemStack st) {
            return switch (type) {
                case HEAD, CHEST, LEGS, FEET -> armorSlot(st) == type;
                default -> true;
            };
        }

        @Override public int getMaxStackSize() { return 1; }
        @Override public int getMaxStackSize(ItemStack st) { return 1; }
    }

    /** The settler's worn and held items as a container (server side). */
    private static final class EquipmentContainer implements Container {
        private final SettlerEntity e;

        EquipmentContainer(SettlerEntity e) { this.e = e; }

        @Override public int getContainerSize() { return EQUIP; }

        @Override
        public boolean isEmpty() {
            for (EquipmentSlot s : EQUIP_SLOTS) if (!e.getItemBySlot(s).isEmpty()) return false;
            return true;
        }

        @Override public ItemStack getItem(int i) { return e.getItemBySlot(EQUIP_SLOTS[i]); }

        @Override
        public ItemStack removeItem(int i, int amount) {
            ItemStack cur = e.getItemBySlot(EQUIP_SLOTS[i]);
            if (cur.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            ItemStack out = cur.copy();
            e.setItemSlot(EQUIP_SLOTS[i], ItemStack.EMPTY);
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            ItemStack cur = e.getItemBySlot(EQUIP_SLOTS[i]).copy();
            e.setItemSlot(EQUIP_SLOTS[i], ItemStack.EMPTY);
            return cur;
        }

        @Override public void setItem(int i, ItemStack st) { e.setItemSlot(EQUIP_SLOTS[i], st); }
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player p) { return true; }

        @Override
        public void clearContent() {
            for (EquipmentSlot s : EQUIP_SLOTS) e.setItemSlot(s, ItemStack.EMPTY);
        }
    }
}
