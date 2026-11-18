package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.*;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Fells whole trees inside the forest zone, clears their leaves, collects drops and replants. */
public final class LumberjackJob extends SiteJob {
    private final Set<BlockPos> ignored = new HashSet<>();

    public LumberjackJob(SettlerEntity settler) {
        super(settler, WorkSiteTypes.FOREST_ID);
    }

    @Override
    protected boolean requiresTool() { return true; }

    @Override
    protected String toolKey() { return "axe"; }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        WorkSiteBlockEntity w = claim();
        if (w == null) return new Wait(100);
        Task tool = toolTrip();
        if (tool != null) return tool;
        if (pocketsFull()) {
            int[] kept = {0};
            Task dep = depositTrip(st -> Storage.isFood(st) || (st.is(ItemTags.SAPLINGS) && kept[0]++ == 0));
            return dep == null ? new Wait(200) : dep;
        }
        int r = w.option("radius");
        BlockPos c = w.getBlockPos();
        AABB zone = new AABB(c).inflate(r + 1, 6, r + 1);
        List<ItemEntity> items = s.level().getEntitiesOfClass(ItemEntity.class, zone, e -> e.isAlive() && wanted(e.getItem()));
        if (!items.isEmpty()) {
            ItemEntity item = Collections.min(items, Comparator.comparingDouble(e -> e.distanceToSqr(s)));
            s.setStatus("collecting");
            return new Sequence(new MoveTo(item.position(), 1.3, 0.7), new Run(LumberjackJob::pickUpAround));
        }
        BlockPos tree = findTree((ServerLevel) s.level(), c, r);
        if (tree == null) {
            Task plant = plantSpare((ServerLevel) s.level(), w);
            if (plant != null) return plant;
            s.setStatus("no_trees");
            return new Wait(200, Vec3.atCenterOf(c));
        }
        s.setStatus("chopping");
        return new Sequence(MoveTo.block(tree, 2.6), new Chop(tree, w.flag("replant")));
    }

    /** Wave J1: the zone sweep only takes what a forest gives: wood, saplings, sticks and apples. */
    static boolean wanted(ItemStack st) {
        return !st.isEmpty() && (st.is(ItemTags.LOGS) || st.is(ItemTags.PLANKS) || st.is(ItemTags.SAPLINGS) || st.is(net.minecraft.world.item.Items.STICK) || st.is(net.minecraft.world.item.Items.APPLE));
    }

    static void pickUpAround(SettlerEntity s) {
        for (ItemEntity e : s.level().getEntitiesOfClass(ItemEntity.class, s.getBoundingBox().inflate(1.8), x -> wanted(x.getItem()))) {
            ItemStack rest = s.inventory().addItem(e.getItem().copy());
            if (rest.isEmpty()) e.discard(); else e.setItem(rest);
        }
        s.level().playSound(null, s.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3f, 1.4f);
    }

    private @Nullable BlockPos findTree(ServerLevel level, BlockPos c, int r) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -3, -r), c.offset(r, 8, r))) {
            BlockState st = level.getBlockState(p);
            if (!st.is(BlockTags.LOGS) || ignored.contains(p)) continue;
            if (!level.getBlockState(p.below()).is(BlockTags.DIRT)) continue;
            BlockState up = level.getBlockState(p.above());
            if (!up.is(BlockTags.LOGS) && !(up.getBlock() instanceof LeavesBlock)) continue;
            double d = p.distSqr(settler.blockPosition());
            if (d < bestDist) { bestDist = d; best = p.immutable(); }
        }
        return best;
    }

    private @Nullable Task plantSpare(ServerLevel level, WorkSiteBlockEntity w) {
        if (!w.flag("replant")) return null;
        ItemStack sapling = ItemStack.EMPTY;
        for (ItemStack st : settler.inventory().getItems()) if (st.is(ItemTags.SAPLINGS)) { sapling = st; break; }
        if (sapling.isEmpty() || !(sapling.getItem() instanceof BlockItem bi)) return null;
        int r = w.option("radius");
        BlockPos c = w.getBlockPos();
        for (int tries = 0; tries < 24; tries++) {
            BlockPos p = c.offset(settler.getRandom().nextInt(2 * r + 1) - r, 0, settler.getRandom().nextInt(2 * r + 1) - r);
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos q = p.above(dy);
                if (!level.getBlockState(q).isAir() || !level.getBlockState(q.below()).is(BlockTags.DIRT)) continue;
                if (crowded(level, q)) break;
                BlockState state = bi.getBlock().defaultBlockState();
                if (!state.canSurvive(level, q)) break;
                settler.setStatus("planting");
                return new PlaceBlock(q, state, sapling.getItem(), null);
            }
        }
        return null;
    }

    private static boolean crowded(ServerLevel level, BlockPos p) {
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -1, -2), p.offset(2, 3, 2))) {
            BlockState st = level.getBlockState(q);
            if (st.is(BlockTags.LOGS) || st.is(BlockTags.SAPLINGS)) return true;
        }
        return false;
    }

    /** Chops at the trunk, then the whole tree comes down at once. */
    private final class Chop implements Task {
        private final BlockPos base;
        private final boolean replant;
        private int ticks;
        private int needed = -1;
        private List<BlockPos> logs;

        Chop(BlockPos base, boolean replant) {
            this.base = base;
            this.replant = replant;
        }

        @Override
        public Status tick(SettlerEntity s) {
            ServerLevel level = (ServerLevel) s.level();
            BlockState baseState = level.getBlockState(base);
            if (!baseState.is(BlockTags.LOGS)) return Status.DONE;
            if (!s.canReach(base) && !s.hopNear(base)) return Status.FAILED;
            if (logs == null) {
                logs = connectedLogs(level, base);
                if (countLeaves(level, logs) < 3) {
                    ignored.add(base);
                    return Status.FAILED;
                }
                float tool = Math.max(1f, s.getMainHandItem().getDestroySpeed(baseState));
                needed = (int) ((40 + logs.size() * 10) / (s.workSpeed() * (0.6f + tool * 0.25f)));
            }
            s.getNavigation().stop();
            s.getLookControl().setLookAt(Vec3.atCenterOf(base));
            if (++ticks % 6 == 1) {
                s.swingArm(InteractionHand.MAIN_HAND);
                io.github.operagtx.hearthholm.town.Fx.chop(level, base, baseState);
            }
            level.destroyBlockProgress(s.getId(), base, Math.min(9, ticks * 10 / Math.max(1, needed)));
            if (ticks < needed) return Status.RUNNING;
            level.destroyBlockProgress(s.getId(), base, -1);
            fell(s, level, baseState);
            return Status.DONE;
        }

        private void fell(SettlerEntity s, ServerLevel level, BlockState baseState) {
            ItemStack tool = s.getMainHandItem();
            Block saplingHint = null;
            int n = 0;
            Set<BlockPos> leaves = leavesAround(level, logs);
            for (BlockPos p : logs) {
                BlockState st = level.getBlockState(p);
                s.store(Block.getDrops(st, level, p, null, s, tool));
                if (n++ % 3 == 0) level.destroyBlock(p, false, s); else level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            for (BlockPos p : leaves) {
                BlockState st = level.getBlockState(p);
                for (ItemStack d : Block.getDrops(st, level, p, null, s, ItemStack.EMPTY)) {
                    if (saplingHint == null && d.is(ItemTags.SAPLINGS) && d.getItem() instanceof BlockItem bi) saplingHint = bi.getBlock();
                    s.store(d);
                }
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            level.playSound(null, base, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.5f, 0.7f);
            if (!tool.isEmpty() && tool.isDamageableItem()) tool.hurtAndBreak(Math.max(1, logs.size() / 2), s, EquipmentSlot.MAINHAND);
            if (replant && level.getBlockState(base).isAir() && level.getBlockState(base.below()).is(BlockTags.DIRT)) {
                ItemStack sap = ItemStack.EMPTY;
                for (ItemStack st : s.inventory().getItems()) {
                    if (st.is(ItemTags.SAPLINGS) && (saplingHint == null || (st.getItem() instanceof BlockItem bi && bi.getBlock() == saplingHint))) { sap = st; break; }
                }
                if (sap.isEmpty()) for (ItemStack st : s.inventory().getItems()) if (st.is(ItemTags.SAPLINGS)) { sap = st; break; }
                if (!sap.isEmpty() && sap.getItem() instanceof BlockItem bi) {
                    level.setBlock(base, bi.getBlock().defaultBlockState(), 3);
                    sap.shrink(1);
                    s.inventory().setChanged();
                }
            }
            s.gainXp(Math.max(1, logs.size() / 2));
            Settlement st = s.settlement();
            if (st != null) {
                st.count("logs", logs.size());
                st.addRenown(Math.max(1, logs.size() / 12));
                SettlementService.markDirty(s);
            }
        }
    }

    static List<BlockPos> connectedLogs(ServerLevel level, BlockPos base) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(base);
        seen.add(base);
        while (!q.isEmpty() && out.size() < 256) {
            BlockPos p = q.poll();
            out.add(p);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = 0; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (Math.abs(n.getX() - base.getX()) > 8 || Math.abs(n.getZ() - base.getZ()) > 8 || n.getY() - base.getY() > 32) continue;
                        if (seen.add(n) && level.getBlockState(n).is(BlockTags.LOGS)) q.add(n);
                    }
        }
        return out;
    }

    static int countLeaves(ServerLevel level, List<BlockPos> logs) {
        int n = 0;
        for (BlockPos p : logs)
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                BlockState st = level.getBlockState(q);
                if (st.getBlock() instanceof LeavesBlock && !st.getValue(LeavesBlock.PERSISTENT) && ++n >= 3) return n;
            }
        return n;
    }

    static Set<BlockPos> leavesAround(ServerLevel level, List<BlockPos> logs) {
        Set<BlockPos> out = new HashSet<>();
        for (BlockPos p : logs)
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-3, -2, -3), p.offset(3, 3, 3))) {
                if (out.size() > 800) return out;
                BlockState st = level.getBlockState(q);
                if (st.getBlock() instanceof LeavesBlock && !st.getValue(LeavesBlock.PERSISTENT)) out.add(q.immutable());
            }
        return out;
    }
}
