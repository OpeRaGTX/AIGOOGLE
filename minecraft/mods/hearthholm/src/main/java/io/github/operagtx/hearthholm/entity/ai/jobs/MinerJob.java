package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.*;
import io.github.operagtx.hearthholm.entity.ai.jobs.MinePlanner.Kind;
import io.github.operagtx.hearthholm.entity.ai.jobs.MinePlanner.Plan;
import io.github.operagtx.hearthholm.entity.ai.jobs.MinePlanner.Step;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Digs the layout from {@link MinePlanner}: seals lava and water with filler blocks, fills
 * holes in the floor, lights the way with torches, places ladders in ladder shafts and hauls
 * the loot to the chest standing next to the marker.
 */
public final class MinerJob extends SiteJob {
    private static final Set<Item> FILLER = Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.STONE,
            Items.DEEPSLATE, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF, Items.NETHERRACK, Items.BLACKSTONE,
            Items.COARSE_DIRT, Items.SANDSTONE, Items.CALCITE);
    private static final Set<Item> PRECIOUS = Set.of(Items.DIAMOND, Items.EMERALD, Items.ANCIENT_DEBRIS, Items.RAW_GOLD, Items.LAPIS_LAZULI);
    private long nextFetch;
    private final java.util.ArrayDeque<BlockPos> vein = new java.util.ArrayDeque<>();
    private final java.util.Map<BlockPos, Integer> veinTries = new java.util.HashMap<>();

    private static final java.util.List<net.minecraft.tags.TagKey<Block>> ORE_TAGS = java.util.stream.Stream.of(
            "c:ores", "minecraft:coal_ores", "minecraft:iron_ores", "minecraft:copper_ores", "minecraft:gold_ores",
            "minecraft:diamond_ores", "minecraft:emerald_ores", "minecraft:lapis_ores", "minecraft:redstone_ores")
            .map(n -> net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.Identifier.parse(n)))
            .toList();

    /** Any ore: the common {@code c:ores} tag plus the vanilla ore tags. */
    public static boolean isOre(BlockState st) {
        for (var tag : ORE_TAGS) if (st.is(tag)) return true;
        return st.is(Blocks.ANCIENT_DEBRIS);
    }

    /** After a cell was dug: ore touching it starts a flood fill over the connected ore (capped), queued nearest first. */
    private void findVein(ServerLevel level, BlockPos origin) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>(vein);
        for (Direction d : Direction.values()) {
            BlockPos n = origin.relative(d);
            if (isOre(level.getBlockState(n)) && seen.add(n)) q.add(n);
        }
        int added = 0;
        while (!q.isEmpty() && added < Balance.MINE_VEIN_CAP) {
            BlockPos p = q.poll();
            vein.add(p);
            added++;
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                if (!seen.contains(n) && isOre(level.getBlockState(n))) {
                    BlockPos im = n.immutable();
                    seen.add(im);
                    q.add(im);
                }
            }
        }
    }

    public MinerJob(SettlerEntity settler) {
        super(settler, WorkSiteTypes.MINE_ID);
    }

    public static boolean isFiller(ItemStack s) {
        return FILLER.contains(s.getItem());
    }

    @Override
    protected boolean requiresTool() { return true; }

    @Override
    protected String toolKey() { return "pickaxe"; }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        WorkSiteBlockEntity w = claim();
        if (w == null) return new Wait(100);
        Plan plan = MinePlanner.plan(w);
        Task tool = toolTrip();
        if (tool != null) return surface(plan, tool, w);
        if (pocketsFull()) {
            int[] keptFiller = {0};
            Predicate<ItemStack> keep = st -> Storage.isFood(st) || st.is(Items.TORCH) || st.is(Items.LADDER)
                    || (isFiller(st) && keptFiller[0]++ == 0);
            Task dep = depositTrip(keep);
            return dep == null ? new Wait(200) : surface(plan, dep, w);
        }

        ServerLevel level = (ServerLevel) s.level();
        // wave I1: an ore was found, so mine the whole connected vein before going on with the plan
        while (!vein.isEmpty() && !isOre(level.getBlockState(vein.peek()))) vein.poll();
        if (!vein.isEmpty()) {
            BlockPos p = vein.peek();
            if (veinTries.merge(p, 1, Integer::sum) > 3) {
                vein.poll();
            } else {
                if (!seal(level, plan, p)) return needFiller(plan, w);
                s.setStatus("mining_vein", vein.size());
                return wrap(plan, new BreakBlock(p, drops -> { vein.remove(p); onMined(drops); }), p, w);
            }
        }
        int i = w.progress();
        for (int guard = 0; guard < 96 && i < plan.steps().size(); guard++) {
            Step st = plan.steps().get(i);
            if (!done(level, st, w)) break;
            i++;
        }
        w.setProgress(i);
        if (i >= plan.steps().size()) {
            s.setStatus("mine_done");
            return new Wait(200);
        }
        Step step = plan.steps().get(i);
        s.setStatus("mining", i * 100 / plan.steps().size());
        return switch (step.kind()) {
            case DIG -> {
                if (!seal(level, plan, step.pos())) yield needFiller(plan, w);
                yield wrap(plan, new BreakBlock(step.pos(), drops -> { onMined(drops); findVein(level, step.pos()); }), step.pos(), w);
            }
            case FLOOR -> {
                ItemStack filler = firstFiller();
                if (filler.isEmpty()) yield needFiller(plan, w);
                BlockState state = Block.byItem(filler.getItem()).defaultBlockState();
                yield wrap(plan, new PlaceBlock(step.pos(), state, filler.getItem(), null), step.pos(), w);
            }
            case TORCH -> {
                BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, step.dir());
                if (!s.has(Items.TORCH)) {   // wave I1: torches come from storage
                    Task f = fetchLimited(st -> st.is(Items.TORCH), 24, "fetching_torches");
                    if (f != null) yield surface(plan, f, w);
                }
                if (!s.has(Items.TORCH) || !torch.canSurvive(level, step.pos())) {
                    w.setProgress(i + 1);
                    yield new Wait(1);
                }
                yield wrap(plan, new PlaceBlock(step.pos(), torch, Items.TORCH, null), step.pos(), w);
            }
            case LADDER -> {
                if (!s.has(Items.LADDER)) {
                    Task f = fetchLimited(st -> st.is(Items.LADDER), 32, "fetching_ladders");
                    if (f != null) yield surface(plan, f, w);
                    s.setStatus("need_ladders");
                    yield new Wait(200);
                }
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, step.dir());
                if (!ladder.canSurvive(level, step.pos())) {
                    w.setProgress(i + 1);
                    yield new Wait(1);
                }
                yield wrap(plan, new PlaceBlock(step.pos(), ladder, Items.LADDER, null), step.pos(), w);
            }
        };
    }

    private boolean done(ServerLevel level, Step st, WorkSiteBlockEntity w) {
        if (st.pos().getY() <= level.getMinY()) return true;
        BlockState state = level.getBlockState(st.pos());
        return switch (st.kind()) {
            case DIG -> state.getCollisionShape(level, st.pos()).isEmpty() && state.getFluidState().isEmpty();
            case FLOOR -> state.isFaceSturdy(level, st.pos(), Direction.UP) || state.is(Blocks.LADDER);
            case TORCH -> !w.flag("torches") || state.getBlock() instanceof WallTorchBlock;
            case LADDER -> state.is(Blocks.LADDER);
        };
    }

    /** Replaces fluids next to a cell that will be opened. False when filler is missing. */
    private boolean seal(ServerLevel level, Plan plan, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (plan.dug().contains(n) || level.getFluidState(n).isEmpty()) continue;
            ItemStack filler = firstFiller();
            if (filler.isEmpty()) return false;
            BlockState st = Block.byItem(filler.getItem()).defaultBlockState();
            settler.takeItem(filler.getItem(), 1);
            level.setBlock(n, st, 3);
            level.playSound(null, n, st.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7f, 1f);
        }
        return true;
    }

    private ItemStack firstFiller() {
        for (ItemStack st : settler.inventory().getItems()) if (isFiller(st)) return st;
        return ItemStack.EMPTY;
    }

    private Task needFiller(Plan plan, WorkSiteBlockEntity w) {
        Task f = fetchLimited(MinerJob::isFiller, 64, "fetching_filler");
        if (f != null) return surface(plan, f, w);
        settler.setStatus("need_filler");
        return new Wait(200);
    }

    private @Nullable Task fetchLimited(Predicate<ItemStack> filter, int count, String status) {
        long now = settler.level().getGameTime();
        if (now < nextFetch) return null;
        nextFetch = now + 600;
        return fetch(filter, count, status);
    }

    private void onMined(List<ItemStack> drops) {
        settler.gainXp(1);
        Settlement st = settler.settlement();
        if (st != null) {
            st.count("mined", 1);
            if (st.stats.getOrDefault("mined", 0) % 25 == 0) st.addRenown(1);
            SettlementService.markDirty(settler);
        }
        for (ItemStack d : drops) {
            if (PRECIOUS.contains(d.getItem())) {
                SettlementService.notifyOwner(settler, Component.translatable("message.hearthholm.found_precious",
                        settler.getName(), d.getHoverName()).withStyle(ChatFormatting.AQUA));
                break;
            }
        }
    }

    // ---- ladder-shaft travel --------------------------------------------------------------

    /** In ladder mines, climbs the shaft before tasks that are at another depth. */
    private Task wrap(Plan plan, Task task, BlockPos target, WorkSiteBlockEntity w) {
        if (plan.mode() != MinePlanner.LADDER) return task;
        boolean inColumn = target.getX() == plan.shaft().getX() && target.getZ() == plan.shaft().getZ();
        int standY = inColumn ? target.getY() + 1 : plan.bottomY();
        standY = Math.min(standY, plan.topY());
        if (settler.getY() > standY + 1.5) return new Sequence(new Climb(plan, standY, w), task);
        if (settler.getY() < standY - 1.5) return new Sequence(new Climb(plan, standY, w), task);
        return task;
    }

    /** Wraps tasks that happen at the surface (chests). */
    private Task surface(Plan plan, Task task, WorkSiteBlockEntity w) {
        if (plan.mode() == MinePlanner.LADDER && settler.getY() < plan.topY() - 1.5) {
            return new Sequence(new Climb(plan, plan.topY(), w), task);
        }
        return task;
    }

    /** Moves straight up or down the ladder column. */
    private static final class Climb implements Task {
        private final Plan plan;
        private final int targetY;
        private final BlockPos exit;
        private MoveTo enter;
        private int ticks;

        Climb(Plan plan, int targetY, WorkSiteBlockEntity w) {
            this.plan = plan;
            this.targetY = targetY;
            this.exit = w.getBlockPos().relative(w.facing());
        }

        @Override
        public Status tick(SettlerEntity s) {
            if (++ticks > 900) return s.hopNear(plan.shaft().atY(targetY)) ? Status.DONE : Status.FAILED;
            double cx = plan.shaft().getX() + 0.5, cz = plan.shaft().getZ() + 0.5;
            double dxz = Math.hypot(s.getX() - cx, s.getZ() - cz);
            if (dxz > 0.45) {
                if (s.getY() >= plan.topY() - 0.5) {
                    if (enter == null) enter = new MoveTo(new Vec3(cx, plan.topY(), cz), 0.4, 0.5);
                    Status st = enter.tick(s);
                    if (st == Status.FAILED) return Status.FAILED;
                    if (st == Status.RUNNING) return Status.RUNNING;
                }
                s.setDeltaMovement((cx - s.getX()) * 0.3, s.getDeltaMovement().y, (cz - s.getZ()) * 0.3);
                return Status.RUNNING;
            }
            s.getNavigation().stop();
            double dy = targetY - s.getY();
            if (dy > 0.1) {
                s.setDeltaMovement((cx - s.getX()) * 0.3, 0.2, (cz - s.getZ()) * 0.3);
                if (targetY >= plan.topY() && s.getY() >= plan.topY() - 0.3) {
                    s.teleportTo(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5);
                    return Status.DONE;
                }
                return Status.RUNNING;
            }
            if (dy < -0.1) {
                s.setDeltaMovement((cx - s.getX()) * 0.3, Math.max(-0.15, s.getDeltaMovement().y), (cz - s.getZ()) * 0.3);
                return Status.RUNNING;
            }
            return s.onGround() || s.onClimbable() ? Status.DONE : Status.RUNNING;
        }
    }
}
