package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.registry.ModSounds;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Repairable;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The smith crafts like any crafter, and first looks after the settlers' tools: a worn tool
 * (60% or less left) is mended on the spot with its repair material from the warehouse
 * (each unit restores a quarter, like an anvil).
 */
public final class SmithJob extends CrafterJob {
    private long nextScan;
    private @Nullable UUID patient;

    public SmithJob(SettlerEntity settler) {
        super(settler, Workshop.SMITHY);
    }

    private static boolean worn(ItemStack tool) {
        return tool.isDamageableItem() && tool.get(DataComponents.REPAIRABLE) != null
                && tool.getDamageValue() >= tool.getMaxDamage() * 0.4;
    }

    @Override
    protected @Nullable Task duty(Settlement st) {
        ServerLevel level = (ServerLevel) settler.level();
        if (patient != null) {
            UUID id = patient;
            patient = null;
            if (level.getEntity(id) instanceof SettlerEntity e && worn(e.getMainHandItem())) {
                Repairable rep = e.getMainHandItem().get(DataComponents.REPAIRABLE);
                Predicate<ItemStack> material = s -> rep != null && rep.isValidRepairItem(s);
                if (settler.count(material) > 0) return new Mend(e);
            }
        }
        long now = level.getGameTime();
        if (now < nextScan) return null;
        nextScan = now + 600;
        for (SettlerEntity e : level.getEntitiesOfClass(SettlerEntity.class, settler.getBoundingBox().inflate(64),
                x -> x != settler && st.id.equals(x.settlementId()) && worn(x.getMainHandItem()))) {
            ItemStack tool = e.getMainHandItem();
            Repairable rep = tool.get(DataComponents.REPAIRABLE);
            if (rep == null) continue;
            Predicate<ItemStack> material = rep::isValidRepairItem;
            BlockPos chest = findWith(storage(), material);
            if (chest == null) continue;
            int units = Math.min(4, (tool.getDamageValue() * 4 + tool.getMaxDamage() - 1) / tool.getMaxDamage());
            patient = e.getUUID();
            settler.setStatus("fetching_repair", tool.getHoverName(), e.getName());
            return new ContainerTrip(chest, (s, c) -> s.store(Storage.take(List.of(c), material, units)));
        }
        return null;
    }

    /** Walks up to the settler and hammers the tool back into shape. */
    private final class Mend implements Task {
        private final SettlerEntity target;
        private MoveTo walk;
        private int ticks;

        Mend(SettlerEntity target) {
            this.target = target;
        }

        @Override
        public Status tick(SettlerEntity s) {
            if (!target.isAlive()) return Status.FAILED;
            if (s.distanceToSqr(target) > 2.5 * 2.5) {
                if (walk == null || ticks++ % 40 == 0) walk = MoveTo.block(target.blockPosition(), 1.8);
                Status st = walk.tick(s);
                return st == Status.FAILED ? Status.FAILED : Status.RUNNING;
            }
            s.getNavigation().stop();
            target.getNavigation().stop();
            s.getLookControl().setLookAt(target);
            settler.setStatus("repairing", target.getMainHandItem().getHoverName(), target.getName());
            ServerLevel level = (ServerLevel) s.level();
            ticks++;
            if (ticks % 8 == 1) {
                s.swingArm(InteractionHand.MAIN_HAND);
                level.playSound(null, target.blockPosition(), ModSounds.HAMMER, SoundSource.NEUTRAL, 0.7f, 1.1f);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
            }
            if (ticks < 60) return Status.RUNNING;
            ItemStack tool = target.getMainHandItem();
            Repairable rep = tool.get(DataComponents.REPAIRABLE);
            if (rep == null) return Status.FAILED;
            int quarter = Math.max(1, tool.getMaxDamage() / 4);
            while (tool.getDamageValue() > 0) {
                ItemStack unit = s.takeFirst(rep::isValidRepairItem, 1);
                if (unit.isEmpty()) break;
                tool.setDamageValue(Math.max(0, tool.getDamageValue() - quarter));
            }
            s.gainXp(3);
            Settlement st = s.settlement();
            if (st != null) {
                st.count("repaired", 1);
                SettlementService.markDirty(s);
            }
            return Status.DONE;
        }

        @Override
        public void stop(SettlerEntity s) {
            if (walk != null) walk.stop(s);
        }
    }
}
