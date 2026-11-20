package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.entity.ai.tasks.Wait;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import io.github.operagtx.hearthholm.zone.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Casts a rod at the water next to the fishing marker and rolls vanilla fishing loot. */
public final class FisherJob extends SiteJob {
    private static final Set<Item> JUNK = Set.of(Items.LILY_PAD, Items.BOWL, Items.LEATHER, Items.LEATHER_BOOTS,
            Items.ROTTEN_FLESH, Items.STICK, Items.STRING, Items.POTION, Items.BONE, Items.INK_SAC, Items.TRIPWIRE_HOOK, Items.BAMBOO);

    public FisherJob(SettlerEntity settler) {
        super(settler, WorkSiteTypes.FISHING_ID);
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        WorkSiteBlockEntity w = claim();
        if (w == null) return new Wait(100);
        Task tool = toolTrip();
        if (tool != null) return tool;
        if (pocketsFull()) {
            Task dep = depositTrip(st -> false);
            return dep == null ? new Wait(200) : dep;
        }
        if (!s.getMainHandItem().is(Items.FISHING_ROD)) {
            s.setStatus("need_rod");
            return new Wait(300);
        }
        ServerLevel level = (ServerLevel) s.level();
        BlockPos water = w.hasZone() ? findZoneWater(level, w.getBlockPos(), w.zone(), s) : findWater(level, w.getBlockPos());
        if (water == null) {
            s.setStatus("no_water");
            return new Wait(300);
        }
        BlockPos stand = findStand(level, water, w.getBlockPos());
        s.setStatus("fishing");
        return new Fish(stand, water, w.flag("keep_junk"));
    }

    /**
     * Wave L2: the water source nearest to the fisher inside the drawn 10 x 10 zone (a source block with air above it, at most 4 below to 2 above the marker);
     * the zone is small, so it is scanned column by column every time.
     */
    public static @Nullable BlockPos findZoneWater(ServerLevel level, BlockPos marker, WorkZone zone, SettlerEntity s) {
        BlockPos[] best = {null};
        double[] bestDist = {Double.MAX_VALUE};
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        zone.forEachCell(WorkZone.SURFACE, (ix, iz) -> {
            int x = zone.blockX(ix), z = zone.blockZ(iz);
            if (!level.hasChunkAt(mp.set(x, marker.getY(), z))) return;
            for (int dy = 2; dy >= -4; dy--) {
                BlockPos p = new BlockPos(x, marker.getY() + dy, z);
                if (!level.getFluidState(p).is(FluidTags.WATER) || !level.getFluidState(p).isSource() || !level.getBlockState(p.above()).isAir()) continue;
                double d = p.distSqr(s.blockPosition());
                if (d < bestDist[0]) { bestDist[0] = d; best[0] = p; }
                break;
            }
        });
        return best[0];
    }

    private static @Nullable BlockPos findWater(ServerLevel level, BlockPos c) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 1, 4))) {
            if (!level.getFluidState(p).is(FluidTags.WATER) || !level.getFluidState(p).isSource()) continue;
            if (!level.getBlockState(p.above()).isAir()) continue;
            double d = p.distSqr(c);
            if (d < bestDist) { bestDist = d; best = p.immutable(); }
        }
        return best;
    }

    private static BlockPos findStand(ServerLevel level, BlockPos water, BlockPos marker) {
        BlockPos best = marker;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(water.offset(-2, 0, -2), water.offset(2, 2, 2))) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) continue;
            if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) continue;
            if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
            double d = p.distSqr(water) * 2 + p.distSqr(marker);
            if (d < bestDist) { bestDist = d; best = p.immutable(); }
        }
        return best;
    }

    private final class Fish implements Task {
        private final BlockPos water;
        private final boolean keepJunk;
        private final MoveTo walk;
        private int ticks;
        private int wait = -1;

        Fish(BlockPos stand, BlockPos water, boolean keepJunk) {
            this.water = water;
            this.keepJunk = keepJunk;
            this.walk = MoveTo.block(stand, 1.2);
        }

        @Override
        public Status tick(SettlerEntity s) {
            Status st = walk.tick(s);
            if (st != Status.DONE) return st;
            ServerLevel level = (ServerLevel) s.level();
            Vec3 bob = Vec3.atCenterOf(water).add(0, 0.45, 0);
            s.getLookControl().setLookAt(bob);
            if (wait < 0) {
                wait = (int) ((260 + s.getRandom().nextInt(320)) / s.workSpeed());
                s.swingArm(InteractionHand.MAIN_HAND);
                level.playSound(null, s.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5f, 0.8f);
                level.sendParticles(ParticleTypes.SPLASH, bob.x, bob.y, bob.z, 6, 0.1, 0.0, 0.1, 0.1);
            }
            ticks++;
            if (ticks % 40 == 0) level.sendParticles(ParticleTypes.BUBBLE, bob.x, bob.y - 0.3, bob.z, 3, 0.1, 0.05, 0.1, 0.02);
            if (ticks < wait) return Status.RUNNING;

            level.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6f, 1f);
            level.sendParticles(ParticleTypes.FISHING, bob.x, bob.y, bob.z, 12, 0.2, 0.05, 0.2, 0.1);
            s.swingArm(InteractionHand.MAIN_HAND);
            ItemStack rod = s.getMainHandItem();
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, bob)
                    .withParameter(LootContextParams.TOOL, rod)
                    .withLuck(s.luck())
                    .create(LootContextParamSets.FISHING);
            LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
            for (ItemStack loot : table.getRandomItems(params)) {
                if (!keepJunk && JUNK.contains(loot.getItem())) continue;
                s.store(loot);
                if (loot.is(ItemTags.FISHES)) {
                    Settlement set = s.settlement();
                    if (set != null) {
                        set.count("fish", 1);
                        if (set.stats.getOrDefault("fish", 0) % 4 == 0) set.addRenown(1);
                        SettlementService.markDirty(s);
                    }
                }
            }
            if (rod.isDamageableItem()) rod.hurtAndBreak(1, s, EquipmentSlot.MAINHAND);
            s.gainXp(2);
            return Status.DONE;
        }

        @Override
        public void stop(SettlerEntity s) { walk.stop(s); }
    }
}
