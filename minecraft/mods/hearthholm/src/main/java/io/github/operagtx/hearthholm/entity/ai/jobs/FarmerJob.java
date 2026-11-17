package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.entity.ai.tasks.Wait;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** Tills the field around the farm marker, sows, harvests ripe crops and replants. */
public final class FarmerJob extends SiteJob {
    private static final List<Item> SEEDS = List.of(Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS);
    private static final Set<Item> SEED_SET = Set.copyOf(SEEDS);
    private enum Action { HARVEST, PLANT, TILL }
    private long nextSeedFetch;

    public FarmerJob(SettlerEntity settler) {
        super(settler, WorkSiteTypes.FARM_ID);
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        WorkSiteBlockEntity w = claim();
        if (w == null) return new Wait(100);
        Task tool = toolTrip();
        if (tool != null) return tool;
        if (pocketsFull()) {
            int[] kept = {0};
            Task dep = depositTrip(st -> SEED_SET.contains(st.getItem()) && kept[0]++ < 2);
            return dep == null ? new Wait(200) : dep;
        }
        ServerLevel level = (ServerLevel) s.level();
        Item seed = chooseSeed(w);
        if (seed == null && level.getGameTime() >= nextSeedFetch) {
            nextSeedFetch = level.getGameTime() + 1200;
            int crop = w.option("crop");
            Task f = fetch(st -> crop == 0 ? SEED_SET.contains(st.getItem()) : st.is(SEEDS.get(crop - 1)), 32, "fetching_seeds");
            if (f != null) return f;
        }
        BlockPos center = w.getBlockPos();
        int r = w.option("radius");
        BlockPos bestPos = null;
        Action best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos ground : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, -1, r))) {
            if (ground.getX() == center.getX() && ground.getZ() == center.getZ()) continue;
            Action a = actionFor(level, ground, seed != null, w.flag("till"));
            if (a == null) continue;
            double d = ground.distSqr(s.blockPosition()) + (a == Action.HARVEST ? 0 : 6);
            if (d < bestDist) { bestDist = d; bestPos = ground.immutable(); best = a; }
        }
        if (best == null) {
            s.setStatus(seed == null ? "need_seeds" : "field_tended");
            return new Wait(160, Vec3.atCenterOf(center));
        }
        s.setStatus("farming");
        return new Work(bestPos, best, seed);
    }

    private @Nullable Item chooseSeed(WorkSiteBlockEntity w) {
        int crop = w.option("crop");
        if (crop > 0) {
            Item it = SEEDS.get(crop - 1);
            return settler.has(it) ? it : null;
        }
        Item best = null;
        int bestCount = 0;
        for (Item it : SEEDS) {
            int c = settler.count(st -> st.is(it));
            if (c > bestCount) { bestCount = c; best = it; }
        }
        return best;
    }

    private static @Nullable Action actionFor(ServerLevel level, BlockPos ground, boolean haveSeed, boolean till) {
        BlockPos above = ground.above();
        BlockState top = level.getBlockState(above);
        if (top.getBlock() instanceof CropBlock crop) return crop.isMaxAge(top) ? Action.HARVEST : null;
        BlockState g = level.getBlockState(ground);
        boolean freeAbove = top.isAir() || (top.canBeReplaced() && top.getFluidState().isEmpty());
        if (g.getBlock() instanceof FarmlandBlock) return haveSeed && top.isAir() ? Action.PLANT : null;
        if (till && freeAbove && (g.is(Blocks.DIRT) || g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.DIRT_PATH))) return Action.TILL;
        return null;
    }

    private final class Work implements Task {
        private final BlockPos ground;
        private final Action action;
        private final @Nullable Item seed;
        private final MoveTo walk;
        private int ticks;

        Work(BlockPos ground, Action action, @Nullable Item seed) {
            this.ground = ground;
            this.action = action;
            this.seed = seed;
            this.walk = new MoveTo(Vec3.atBottomCenterOf(ground.above()), 1.9, 0.6);
        }

        @Override
        public Status tick(SettlerEntity s) {
            Status st = walk.tick(s);
            if (st != Status.DONE) return st;
            s.getLookControl().setLookAt(Vec3.atCenterOf(ground));
            if (++ticks % 8 == 1) s.swingArm(InteractionHand.MAIN_HAND);
            if (ticks < Math.max(6, (int) (18 / s.workSpeed()))) return Status.RUNNING;
            ServerLevel level = (ServerLevel) s.level();
            BlockPos above = ground.above();
            switch (action) {
                case HARVEST -> {
                    BlockState crop = level.getBlockState(above);
                    if (!(crop.getBlock() instanceof CropBlock cb) || !cb.isMaxAge(crop)) return Status.DONE;
                    List<ItemStack> drops = Block.getDrops(crop, level, above, null, s, s.getMainHandItem());
                    Item replantItem = crop.getBlock().asItem();
                    boolean replanted = false;
                    for (ItemStack d : drops) {
                        if (!replanted && d.is(replantItem)) { d.shrink(1); replanted = true; }
                        s.store(d);
                    }
                    if (!replanted) replanted = s.takeItem(replantItem, 1);
                    level.destroyBlock(above, false, s);
                    if (replanted) level.setBlock(above, cb.getStateForAge(0), 3);
                    s.gainXp(1);
                    Settlement set = s.settlement();
                    if (set != null) {
                        set.count("crops", 1);
                        if (set.stats.getOrDefault("crops", 0) % 12 == 0) set.addRenown(1);
                        SettlementService.markDirty(s);
                    }
                }
                case PLANT -> {
                    if (seed == null || !level.getBlockState(above).isAir() || !(level.getBlockState(ground).getBlock() instanceof FarmlandBlock)) return Status.DONE;
                    if (!(seed instanceof net.minecraft.world.item.BlockItem bi)) return Status.DONE;
                    BlockState state = bi.getBlock().defaultBlockState();
                    if (!state.canSurvive(level, above) || !s.takeItem(seed, 1)) return Status.DONE;
                    level.setBlock(above, state, 3);
                    level.playSound(null, above, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8f, 1f);
                }
                case TILL -> {
                    BlockState top = level.getBlockState(above);
                    if (!top.isAir()) level.destroyBlock(above, true, s);
                    level.setBlock(ground, Blocks.FARMLAND.defaultBlockState(), 3);
                    level.playSound(null, ground, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 0.9f, 1f);
                    ItemStack hoe = s.getMainHandItem();
                    if (!hoe.isEmpty() && hoe.isDamageableItem()) hoe.hurtAndBreak(1, s, EquipmentSlot.MAINHAND);
                }
            }
            return Status.DONE;
        }

        @Override
        public void stop(SettlerEntity s) { walk.stop(s); }
    }

}
