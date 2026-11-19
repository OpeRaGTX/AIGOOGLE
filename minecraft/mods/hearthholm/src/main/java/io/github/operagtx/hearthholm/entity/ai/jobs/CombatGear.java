package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.WeakHashMap;

/** Guards and warriors pick up a weapon and missing armour pieces from the warehouse. */
final class CombatGear {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final WeakHashMap<SettlerEntity, Long> NEXT = new WeakHashMap<>();

    private CombatGear() {}

    static @Nullable Task trip(SiteJob job, SettlerEntity s) {
        Task tool = job.toolTrip();
        if (tool != null) return tool;
        long now = s.level().getGameTime();
        if (NEXT.getOrDefault(s, 0L) > now) return null;
        NEXT.put(s, now + 2400);
        for (EquipmentSlot slot : ARMOR) {
            if (!s.getItemBySlot(slot).isEmpty()) continue;
            BlockPos chest = job.findWith(job.storage(), st -> fits(st, slot));
            if (chest != null) {
                s.setStatus("fetching_armor");
                return new ContainerTrip(chest, CombatGear::equipAll);
            }
        }
        return null;
    }

    /** What a fighter keeps in the pockets when carrying loot home: weapons, arrows, armour, food, salves. */
    static boolean keep(SettlerEntity s, ItemStack st) {
        return st.is(net.minecraft.world.item.Items.ARROW) || s.profession().isTool(st) || st.has(DataComponents.EQUIPPABLE)
                || st.has(DataComponents.FOOD) || st.is(io.github.operagtx.hearthholm.registry.ModItems.HEALING_SALVE);
    }

    private static boolean fits(ItemStack st, EquipmentSlot slot) {
        Equippable eq = st.get(DataComponents.EQUIPPABLE);
        return eq != null && eq.slot() == slot;
    }

    private static void equipAll(SettlerEntity s, Container c) {
        for (EquipmentSlot slot : ARMOR) {
            if (!s.getItemBySlot(slot).isEmpty()) continue;
            ItemStack piece = Storage.take(List.of(c), st -> fits(st, slot), 1);
            if (!piece.isEmpty()) s.setItemSlot(slot, piece);
        }
    }
}
