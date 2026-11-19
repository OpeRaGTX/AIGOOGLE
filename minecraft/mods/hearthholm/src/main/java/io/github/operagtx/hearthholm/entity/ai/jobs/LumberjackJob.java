package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.*;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import io.github.operagtx.hearthholm.zone.WorkZone;
import net.minecraft.world.level.levelgen.Heightmap;
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
    /** Wave L0: trees the lumberjack could not reach (leaves or terrain in the way) and the game time until which he leaves them alone. */
    private final Map<BlockPos, Long> resting = new HashMap<>();
    /** Wave J2: trees found in the drawn zone (base logs), rescanned every few seconds - the zone is too big to scan per task. */
    private List<BlockPos> zoneTrees = List.of();
    private long nextZoneScan;

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
        WorkZone mapZone = w.hasZone() ? w.zone() : null;   // wave J2: a drawn zone replaces the radius box
        AABB zone = mapZone != null ? mapZone.bounds(c.getY() - 40, c.getY() + 60) : new AABB(c).inflate(r + 1, 6, r + 1);
        List<ItemEntity> items = s.level().getEntitiesOfClass(ItemEntity.class, zone,
                e -> e.isAlive() && wanted(e.getItem()) && (mapZone == null || mapZone.containsColumn(e.getBlockX(), e.getBlockZ())));
        if (!items.isEmpty()) {
            ItemEntity item = Collections.min(items, Comparator.comparingDouble(e -> e.distanceToSqr(s)));
            s.setStatus("collecting");
            return new Sequence(new MoveTo(item.position(), 1.3, 0.7), new Run(LumberjackJob::pickUpAround));
        }
        BlockPos tree = mapZone != null ? findZoneTree((ServerLevel) s.level(), mapZone) : findTree((ServerLevel) s.level(), c, r);
        if (tree == null) {
            Task plant = mapZone != null ? plantInZone((ServerLevel) s.level(), w, mapZone) : plantSpare((ServerLevel) s.level(), w);
            if (plant != null) return plant;
            s.setStatus("no_trees");
            return new Wait(200, Vec3.atCenterOf(c));
        }
        s.setStatus("chopping");
        return new Fell(tree, w.flag("replant"));
    }

    /** Wave K2 (reverts the J1 filter): the sweep picks up every drop again, junk included; the shift-end unload empties the pockets. */
    public static boolean wanted(ItemStack st) {
        return !st.isEmpty();
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
        long now = level.getGameTime();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -3, -r), c.offset(r, 8, r))) {
            BlockState st = level.getBlockState(p);
            if (!st.is(BlockTags.LOGS) || ignored.contains(p) || resting(p, now)) continue;
            if (!level.getBlockState(p.below()).is(BlockTags.DIRT)) continue;
            BlockState up = level.getBlockState(p.above());
            if (!up.is(BlockTags.LOGS) && !(up.getBlock() instanceof LeavesBlock)) continue;
            double d = p.distSqr(settler.blockPosition());
            if (d < bestDist) { bestDist = d; best = p.immutable(); }
        }
        return best;
    }

    /** Wave J2: the nearest tree whose base log is in a drawn cell; the zone is rescanned at most every 100 ticks. */
    private @Nullable BlockPos findZoneTree(ServerLevel level, WorkZone zone) {
        long now = level.getGameTime();
        if (now >= nextZoneScan) {
            nextZoneScan = now + 100;
            zoneTrees = scanZoneTrees(level, zone);
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : zoneTrees) {
            if (ignored.contains(p) || resting(p, now) || !level.getBlockState(p).is(BlockTags.LOGS)) continue;
            double d = p.distSqr(settler.blockPosition());
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    /** Base logs (log on dirt with logs or leaves above) found through the heightmap, one lookup per block column of the zone. */
    private List<BlockPos> scanZoneTrees(ServerLevel level, WorkZone zone) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        zone.forEachCell(WorkZone.SURFACE, (ix, iz) -> {
            for (int dx = 0; dx < zone.cell(); dx++) {
                for (int dz = 0; dz < zone.cell(); dz++) {
                    int x = zone.blockX(ix) + dx, z = zone.blockZ(iz) + dz;
                    if (!level.hasChunkAt(m.set(x, level.getMinY(), z))) continue;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    if (!level.getBlockState(m.set(x, y, z)).is(BlockTags.LOGS)) continue;
                    while (y > level.getMinY() && level.getBlockState(m.set(x, y - 1, z)).is(BlockTags.LOGS)) y--;
                    if (!level.getBlockState(m.set(x, y - 1, z)).is(BlockTags.DIRT)) continue;
                    BlockState up = level.getBlockState(m.set(x, y + 1, z));
                    if (!up.is(BlockTags.LOGS) && !(up.getBlock() instanceof LeavesBlock)) continue;
                    out.add(new BlockPos(x, y, z));
                }
            }
        });
        return out;
    }

    /** Wave J2: with the toggle on, plants a sapling (pocket, else the site chest or warehouse) on a free dirt cell of the zone. */
    private @Nullable Task plantInZone(ServerLevel level, WorkSiteBlockEntity w, WorkZone zone) {
        if (!w.flag("plant")) return null;
        ItemStack sapling = ItemStack.EMPTY;
        for (ItemStack st : settler.inventory().getItems()) if (st.is(ItemTags.SAPLINGS)) { sapling = st; break; }
        if (sapling.isEmpty()) return fetch(st -> st.is(ItemTags.SAPLINGS), 8, "fetching_saplings");
        if (!(sapling.getItem() instanceof BlockItem bi)) return null;
        for (int tries = 0; tries < 40; tries++) {
            BlockPos col = zone.randomColumn(WorkZone.SURFACE, settler.getRandom(), w.getBlockPos().getY());
            if (col == null) return null;
            if (!level.hasChunkAt(col)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, col.getX(), col.getZ());
            BlockPos q = new BlockPos(col.getX(), y, col.getZ());
            if (!level.getBlockState(q).isAir() || !level.getBlockState(q.below()).is(BlockTags.DIRT)) continue;
            if (crowded(level, q)) continue;
            BlockState state = bi.getBlock().defaultBlockState();
            if (!state.canSurvive(level, q)) continue;
            settler.setStatus("planting");
            return new PlaceBlock(q, state, sapling.getItem(), null);
        }
        return null;
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
    private static final double CHOP_DY = 4.5;

    /** Wave L0: the eye-to-trunk distance the lumberjack chops from (a little more than a plain arm's reach, so a tree behind leaves is still felled). */
    private static double chopReach() { return Balance.LUMBER_CHOP_REACH_X10 / 10.0; }

    private boolean resting(BlockPos p, long now) {
        Long until = resting.get(p);
        if (until == null) return false;
        if (now >= until) { resting.remove(p); return false; }
        return true;
    }

    /**
     * Wave L0: walk to a tree and fell it. When leaves or terrain keep him from getting close he chops from where he stands as long as the trunk is within
     * {@link #chopReach()}; after {@code LUMBER_APPROACH_TICKS} of walking he does so as soon as the trunk is in reach. A tree he cannot reach at all
     * rests for {@code LUMBER_TREE_REST_TICKS}, so he picks another one instead of staring at it forever (the old behaviour: the same tree again and again).
     */
    private final class Fell implements Task {
        private final BlockPos base;
        private final MoveTo walk;
        private final Chop chop;
        private boolean chopping;
        private int ticks;

        Fell(BlockPos base, boolean replant) {
            this.base = base.immutable();
            this.walk = MoveTo.block(base, 2.6);
            this.chop = new Chop(base, replant);
        }

        @Override
        public Status tick(SettlerEntity s) {
            if (!chopping) {
                ticks++;
                boolean inReach = s.canReach(base, chopReach(), CHOP_DY);
                Status st = inReach && ticks >= Balance.LUMBER_APPROACH_TICKS ? Status.DONE : walk.tick(s);
                if (st == Status.RUNNING) return st;
                walk.stop(s);
                if (st == Status.FAILED && !inReach) return giveUp(s);
                chopping = true;
            }
            Status st = chop.tick(s);
            return st == Status.FAILED ? giveUp(s) : st;
        }

        private Status giveUp(SettlerEntity s) {
            resting.put(base, s.level().getGameTime() + Balance.LUMBER_TREE_REST_TICKS);
            return Status.FAILED;
        }

        @Override
        public void stop(SettlerEntity s) {
            walk.stop(s);
            chop.stop(s);
        }
    }

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
            if (!s.canReach(base, chopReach(), CHOP_DY) && !s.hopNear(base)) return Status.FAILED;
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
