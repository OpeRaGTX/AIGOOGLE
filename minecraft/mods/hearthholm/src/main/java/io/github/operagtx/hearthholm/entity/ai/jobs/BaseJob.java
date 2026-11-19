package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.menu.SettlerMenu;
import io.github.operagtx.hearthholm.entity.ai.Job;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.entity.ai.tasks.Wait;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Shared helpers for jobs: trips to chests and the warehouse, tools, deposits. */
public abstract class BaseJob implements Job {
    protected final SettlerEntity settler;
    private long nextToolTry, nextToolSay;

    protected BaseJob(SettlerEntity settler) {
        this.settler = settler;
    }

    // ---- containers -----------------------------------------------------------------------

    protected List<BlockPos> chestsNear(BlockPos pos) {
        return chestsNear(pos, 1);
    }

    protected List<BlockPos> chestsNear(BlockPos pos, int r) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -1, -r), pos.offset(r, 1, r))) {
            if (settler.level().getBlockEntity(p) instanceof Container) out.add(p.immutable());
        }
        return out;
    }

    protected List<BlockPos> storage() {
        Settlement st = settler.settlement();
        return st == null ? List.of() : st.storagePositions();
    }

    /** Where results go and supplies come from; site jobs put their marker chest first. */
    protected List<BlockPos> dropOffs() {
        return storage();
    }

    protected @Nullable BlockPos findWith(List<BlockPos> positions, Predicate<ItemStack> filter) {
        if (!(settler.level() instanceof ServerLevel sl)) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : positions) {
            if (!(sl.getBlockEntity(p) instanceof Container c) || Storage.count(List.of(c), filter) == 0) continue;
            double d = p.distSqr(settler.blockPosition());
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    /** The container holding the best tool of the profession (best material, then least worn); the nearest one on a tie. */
    protected @Nullable BlockPos findBestTool(List<BlockPos> positions) {
        if (!(settler.level() instanceof ServerLevel sl)) return null;
        BlockPos best = null;
        long bestScore = -1;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : positions) {
            if (!(sl.getBlockEntity(p) instanceof Container c)) continue;
            long score = -1;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.getItem(i);
                if (settler.profession().isTool(st)) score = Math.max(score, (long) st.getMaxDamage() * 10000L + (st.getMaxDamage() - st.getDamageValue()));
            }
            double d = p.distSqr(settler.blockPosition());
            if (score >= 0 && (score > bestScore || (score == bestScore && d < bestDist))) { bestScore = score; bestDist = d; best = p; }
        }
        return best;
    }

    protected @Nullable BlockPos findSpace(List<BlockPos> positions) {
        if (!(settler.level() instanceof ServerLevel sl)) return null;
        for (BlockPos p : positions) {
            if (!(sl.getBlockEntity(p) instanceof Container c)) continue;
            for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) return p;
        }
        return null;
    }

    protected boolean pocketsFull() {
        return settler.freeSlots() <= 2;
    }

    /** Unloads everything except what {@code keep} wants to carry. */
    protected @Nullable Task depositTrip(Predicate<ItemStack> keep) {
        BlockPos target = findSpace(dropOffs());
        if (target == null) {
            settler.setStatus("storage_full");
            return null;
        }
        settler.setStatus("depositing");
        return new ContainerTrip(target, (s, c) -> {
            for (int i = 0; i < s.inventory().getContainerSize(); i++) {
                ItemStack st = s.inventory().getItem(i);
                if (st.isEmpty() || keep.test(st)) continue;
                s.inventory().setItem(i, Storage.insert(c, st));
            }
            s.inventory().setChanged();
        });
    }

    // ---- wave K2: end of shift ------------------------------------------------------------

    @Override
    public @Nullable Task endOfShift(SettlerEntity s) {
        if (!hasUnloadable(s, this)) return null;
        BlockPos target = findSpace(dropOffs());   // the chest by the work site first, then the warehouse
        if (target == null) {
            s.setStatus("storage_full");
            return null;
        }
        s.setStatus("depositing");
        return new ContainerTrip(target, (st, c) -> unloadInto(st, this, c));
    }

    /** What a settler always keeps for the night: tools of the profession, weapons, armour, the shield and (archers) arrows. */
    static boolean keptGear(SettlerEntity s, ItemStack st) {
        return s.profession().isTool(st) || SettlerMenu.isToolOrWeapon(st) || SettlerMenu.armorSlot(st) != null || st.is(Items.SHIELD)
                || (s.professionId().equals(Professions.ARCHER.id()) && st.is(ItemTags.ARROWS));
    }

    /**
     * Wave M1: single source of truth for the shift-end unload. For every pocket slot, how many items stay with the settler: gear in full, the
     * job's food budget ({@link Job#shiftEndFood}), plus the job's own supplies ({@link Job#shiftEndKeep}, e.g. the miner's torches and ladders).
     * {@link #hasUnloadable} and {@link #unloadInto} both read this plan, so they can never disagree.
     */
    public static int[] keepPlan(SettlerEntity s, Job job) {
        int n = s.inventory().getContainerSize();
        int[] keep = new int[n];
        int food = job.shiftEndFood();
        java.util.Map<net.minecraft.world.item.Item, Integer> keptByItem = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            ItemStack st = s.inventory().getItem(i);
            if (st.isEmpty()) continue;
            int k = 0;
            if (keptGear(s, st)) k = st.getCount();
            else if (Storage.isFood(st)) { k = Math.min(food, st.getCount()); food -= k; }
            int supply = job.shiftEndKeep(s, st, keptByItem.getOrDefault(st.getItem(), 0));
            k = Math.max(k, Math.min(st.getCount(), Math.max(0, supply)));
            keep[i] = k;
            keptByItem.merge(st.getItem(), k, Integer::sum);
        }
        return keep;
    }

    /** Whether any pocket holds something the shift-end unload would hand over. */
    public static boolean hasUnloadable(SettlerEntity s, Job job) {
        int[] keep = keepPlan(s, job);
        for (int i = 0; i < keep.length; i++) if (s.inventory().getItem(i).getCount() > keep[i]) return true;
        return false;
    }

    /** Moves everything the {@link #keepPlan} does not keep into {@code c}; what does not fit stays in the pockets. */
    static void unloadInto(SettlerEntity s, Job job, Container c) {
        int[] keep = keepPlan(s, job);
        for (int i = 0; i < keep.length; i++) {
            ItemStack st = s.inventory().getItem(i);
            if (st.isEmpty() || st.getCount() <= keep[i]) continue;
            if (keep[i] == 0) { s.inventory().setItem(i, Storage.insert(c, st)); continue; }
            ItemStack rest = Storage.insert(c, st.copyWithCount(st.getCount() - keep[i]));
            st.setCount(keep[i] + rest.getCount());
        }
        s.inventory().setChanged();
    }

    /** Wave I1: jobs that cannot work without their tool (miner, lumberjack) return true and name it via {@link #toolKey()}. */
    protected boolean requiresTool() { return false; }

    /** Pause between look-ups for an optional tool (fighters re-arm faster: wave I2). */
    protected int toolRetryTicks() { return 2400; }

    /** "pickaxe", "axe"...: suffix of the {@code tool.hearthholm.*} name and of the {@code need_*} speech bubble. */
    protected String toolKey() { return "tool"; }

    /**
     * Makes sure a fitting tool is in the hand: takes one from the pockets, else walks to a chest next to the work site or the
     * warehouse and takes the best one there. Returns null when the settler holds a tool (or the job does not need one); for jobs that
     * require a tool and found none it shows a thought and a bubble and returns a waiting task.
     */
    protected @Nullable Task toolTrip() {
        if (settler.profession().isTool(settler.getMainHandItem())) return null;
        if (settler.equipToolFromPockets()) return null;
        boolean req = requiresTool();
        long now = settler.level().getGameTime();
        if (now < nextToolTry) return req ? noTool() : null;
        nextToolTry = now + (req ? 200 : toolRetryTicks());
        BlockPos p = findBestTool(dropOffs());
        if (p == null) return req ? noTool() : null;
        settler.setStatus("fetching_tool");
        return new ContainerTrip(p, (s, c) -> s.equipToolFrom(c));
    }

    private Task noTool() {
        complainNoTool();
        return new Wait(100);
    }

    /** Status, thought and (rarely) a speech bubble: "I need a pickaxe". */
    protected void complainNoTool() {
        settler.setStatus("need_tool", Component.translatable("tool.hearthholm." + toolKey()));
        settler.addThought("no_tool", -4, 2400);
        long now = settler.level().getGameTime();
        if (now >= nextToolSay) {
            nextToolSay = now + 1200;
            io.github.operagtx.hearthholm.social.Social.say(settler, "need_" + toolKey(), 99);
        }
    }

    protected @Nullable Task fetch(Predicate<ItemStack> filter, int count, String statusKey) {
        BlockPos p = findWith(dropOffs(), filter);
        if (p == null) return null;
        settler.setStatus(statusKey);
        return new ContainerTrip(p, (s, c) -> s.store(Storage.take(List.of(c), filter, count)));
    }
}
