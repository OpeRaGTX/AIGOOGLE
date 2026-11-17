package io.github.operagtx.hearthholm.entity.ai.jobs;

import io.github.operagtx.hearthholm.crafting.CraftPlanner;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.entity.ai.tasks.ContainerTrip;
import io.github.operagtx.hearthholm.entity.ai.tasks.MoveTo;
import io.github.operagtx.hearthholm.entity.ai.tasks.Sequence;
import io.github.operagtx.hearthholm.entity.ai.tasks.Wait;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.settlement.BuildingRecord;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The healer (Znakhar). First priority: walk to the most hurt settler (below 70 % health) and mend them over
 * time. Otherwise: brew healing salves at the herbalist table from warehouse herbs and glass bottles until the
 * warehouse holds {@link #SALVE_STOCK} of them; salves are what the wounded use when no healer is around.
 */
public final class HealerJob extends BaseJob {
    public static final float HURT = 0.7f;
    public static final int SALVE_STOCK = 8, HERBS_PER_SALVE = 2;
    public static final Set<Item> HERBS = Set.of(Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.KELP, Items.DANDELION, Items.POPPY,
            Items.BLUE_ORCHID, Items.AZURE_BLUET, Items.OXEYE_DAISY, Items.CORNFLOWER, Items.FERN, Items.LILY_OF_THE_VALLEY);
    public static final Predicate<ItemStack> HERB = st -> HERBS.contains(st.getItem());
    public static final Predicate<ItemStack> BOTTLE = st -> st.is(Items.GLASS_BOTTLE);
    public static final Predicate<ItemStack> SALVE = st -> st.is(ModItems.HEALING_SALVE);

    private long nextBrewCheck;

    public HealerJob(SettlerEntity settler) {
        super(settler);
    }

    /** Hurt settlers of the same settlement near this one, most hurt first. */
    public static @Nullable SettlerEntity findPatient(SettlerEntity healer, double range) {
        SettlerEntity best = null;
        float bestRatio = HURT;
        for (SettlerEntity o : healer.level().getEntitiesOfClass(SettlerEntity.class, healer.getBoundingBox().inflate(range, 12, range),
                x -> x != healer && x.isAlive() && !x.isLeaving() && x.hasArrived() && java.util.Objects.equals(x.settlementId(), healer.settlementId()))) {
            float ratio = o.getHealth() / o.getMaxHealth();
            if (ratio < bestRatio) { bestRatio = ratio; best = o; }
        }
        return best;
    }

    /** Whether a healer of this settlement is awake within {@code range} blocks of {@code who}. */
    public static boolean healerNear(SettlerEntity who, double range) {
        for (SettlerEntity o : who.level().getEntitiesOfClass(SettlerEntity.class, who.getBoundingBox().inflate(range, 8, range),
                x -> x != who && x.isAlive() && !x.isSleeping() && x.professionId().equals(io.github.operagtx.hearthholm.core.Professions.HEALER.id())
                        && java.util.Objects.equals(x.settlementId(), who.settlementId()))) return true;
        return false;
    }

    @Override
    public @Nullable Task next(SettlerEntity s) {
        Settlement st = s.settlement();
        if (st == null || !(s.level() instanceof ServerLevel level)) return new Wait(100);
        SettlerEntity patient = findPatient(s, 48);
        if (patient != null) {
            s.setStatus("healing", patient.getName());
            return new Heal(patient);
        }
        BuildingRecord shop = CraftPlanner.workshop(st, Workshop.HERBALISM);
        BlockPos station = shop == null ? null : station(shop);
        if (station == null) {
            s.setStatus("need_workshop", Workshop.HERBALISM.label());
            return cargo() ? depositTrip(x -> false) : new Wait(200);
        }
        int bottles = s.count(BOTTLE), herbs = s.count(HERB);
        if (bottles > 0 && herbs >= HERBS_PER_SALVE) {
            int n = Math.min(bottles, herbs / HERBS_PER_SALVE);
            s.setStatus("brewing");
            return new Sequence(MoveTo.block(station, 1.8), new Brew(station, n));
        }
        if (s.count(SALVE) > 0 || bottles > 0 || herbs > 0) {
            Task t = depositTrip(x -> false);
            if (t != null) return t;
        }
        long now = level.getGameTime();
        if (now >= nextBrewCheck) {
            nextBrewCheck = now + 200;
            List<net.minecraft.world.Container> stock = Storage.resolve(level, st.storagePositions());
            if (Storage.count(stock, SALVE) < SALVE_STOCK) {
                BlockPos bottle = findWith(storage(), BOTTLE);
                BlockPos herb = findWith(storage(), HERB);
                if (bottle != null && herb != null && Storage.count(stock, HERB) >= HERBS_PER_SALVE) {
                    s.setStatus("fetching_herbs");
                    int want = Math.min(4, SALVE_STOCK - Storage.count(stock, SALVE));
                    return new Sequence(
                            new ContainerTrip(bottle, (who, c) -> who.store(Storage.take(List.of(c), BOTTLE, want))),
                            new ContainerTrip(herb, (who, c) -> who.store(Storage.take(List.of(c), HERB, want * HERBS_PER_SALVE))));
                }
            }
        }
        s.setStatus("healer_idle");
        Vec3 look = Vec3.atCenterOf(station);
        return new Sequence(MoveTo.block(station, 2.5).noHop(), new Wait(100, look));
    }

    private @Nullable BlockPos station(BuildingRecord shop) {
        BlockPos best = null;
        for (BlockPos p : shop.stations()) {
            if (!(settler.level().getBlockState(p).getBlock() instanceof io.github.operagtx.hearthholm.block.WorkshopBlock wb) || wb.kind() != Workshop.HERBALISM) continue;
            if (best == null || p.distSqr(settler.blockPosition()) < best.distSqr(settler.blockPosition())) best = p;
        }
        return best;
    }

    private boolean cargo() {
        return settler.freeSlots() < settler.inventory().getContainerSize();
    }

    /** Walks up to a hurt settler and mends them: +1 health every half second with hearts. */
    private static final class Heal implements Task {
        private final SettlerEntity patient;
        private int ticks, repath;

        Heal(SettlerEntity patient) { this.patient = patient; }

        @Override
        public Status tick(SettlerEntity s) {
            if (!patient.isAlive() || patient.isRemoved() || ++ticks > 900) return Status.DONE;
            if (patient.getHealth() >= patient.getMaxHealth() * 0.95f) {
                patient.addThought("patched_up", 3, 12000);
                s.gainXp(2);
                if (s.settlement() != null) s.settlement().stats.merge("healed", 1, Integer::sum);
                patient.say("healed");
                return Status.DONE;
            }
            if (s.distanceToSqr(patient) > 6.25) {
                if (--repath <= 0) {
                    repath = 15;
                    s.getNavigation().moveTo(patient, 1.1 * s.moveFactor());
                }
                if (ticks > 300 && ticks % 100 == 0) s.hopNear(patient.blockPosition());
                return Status.RUNNING;
            }
            s.getNavigation().stop();
            s.getLookControl().setLookAt(patient, 30f, 30f);
            if (ticks % 10 == 0 && s.level() instanceof ServerLevel sl) {
                s.swingArm(InteractionHand.MAIN_HAND);
                patient.heal(Math.max(0.5f, s.workSpeed()));
                sl.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight() + 0.2, patient.getZ(), 2, 0.25, 0.1, 0.25, 0.02);
                if (ticks % 40 == 0) sl.playSound(null, patient.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.5f, 1.4f);
            }
            return Status.RUNNING;
        }

        @Override
        public void stop(SettlerEntity s) {
            s.getNavigation().stop();
        }
    }

    /** Brews {@code batches} salves at the table: one glass bottle and two herbs each. */
    private static final class Brew implements Task {
        private final BlockPos station;
        private final int batches;
        private int ticks, done;

        Brew(BlockPos station, int batches) { this.station = station; this.batches = batches; }

        @Override
        public Status tick(SettlerEntity s) {
            if (!(s.level() instanceof ServerLevel sl)) return Status.FAILED;
            Vec3 top = Vec3.atCenterOf(station).add(0, 0.6, 0);
            s.getLookControl().setLookAt(top.x, top.y, top.z);
            ticks++;
            if (ticks % 8 == 1) s.swingArm(InteractionHand.MAIN_HAND);
            if (ticks % 10 == 4) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, top.x, top.y, top.z, 3, 0.2, 0.1, 0.2, 0.02);
                sl.sendParticles(ParticleTypes.SPLASH, top.x, top.y, top.z, 3, 0.15, 0.05, 0.15, 0.04);
            }
            if (ticks % 20 == 1) sl.playSound(null, station, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 1.1f);
            int need = Math.max(24, Math.round(70 / s.workSpeed()));
            if (ticks < need) return Status.RUNNING;
            ticks = 0;
            if (s.count(BOTTLE) < 1 || s.count(HERB) < HERBS_PER_SALVE) return Status.DONE;
            s.takeItem(Items.GLASS_BOTTLE, 1);
            int left = HERBS_PER_SALVE;
            while (left > 0) {
                ItemStack herb = s.takeFirst(HERB, left);
                if (herb.isEmpty()) break;
                left -= herb.getCount();
            }
            s.store(new ItemStack(ModItems.HEALING_SALVE));
            s.gainXp(2);
            if (s.settlement() != null) s.settlement().stats.merge("brewed", 1, Integer::sum);
            return ++done >= batches ? Status.DONE : Status.RUNNING;
        }
    }
}
