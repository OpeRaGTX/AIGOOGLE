package io.github.operagtx.hearthholm.entity.ai.tasks;

import io.github.operagtx.hearthholm.crafting.CraftPlanner;
import io.github.operagtx.hearthholm.crafting.Recipes.Option;
import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.entity.ai.Task;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Stands at the workstation and works the recipe batch by batch: arm swings, the workshop's
 * sound, item and smoke/spark particles. Each finished batch turns pocket ingredients into the
 * result, so an interruption only loses the batch in progress.
 */
public final class CraftAtStation implements Task {
    private final BlockPos station;
    private final Workshop kind;
    private final Option option;
    private final int batches;
    private final MoveTo approach;
    private int done;
    private int ticks;

    public CraftAtStation(BlockPos station, Workshop kind, Option option, int batches) {
        this.station = station.immutable();
        this.kind = kind;
        this.option = option;
        this.batches = batches;
        this.approach = MoveTo.block(station, 2.2);
    }

    @Override
    public Status tick(SettlerEntity s) {
        Status st = approach.tick(s);
        if (st != Status.DONE) return st;
        ServerLevel level = (ServerLevel) s.level();
        if (!(level.getBlockState(station).getBlock() instanceof io.github.operagtx.hearthholm.block.WorkshopBlock)) return Status.FAILED;
        s.getNavigation().stop();
        Vec3 top = Vec3.atCenterOf(station).add(0, 0.55, 0);
        s.getLookControl().setLookAt(top);
        int need = Mth.clamp(Math.round(40 / s.workSpeed()), 16, 120);
        ticks++;
        if (ticks % 6 == 1) s.swingArm(InteractionHand.MAIN_HAND);
        if (ticks % 12 == 1) level.playSound(null, station, kind.sound(), SoundSource.BLOCKS, 0.7f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        if (ticks % 8 == 4) {
            ItemStack shown = option.inputs().getFirst().items().findFirst().map(h -> new ItemStack(h.value())).orElse(option.output());
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shown.getItem()), top.x, top.y, top.z, 3, 0.15, 0.05, 0.15, 0.04);
            SimpleParticleType extra = switch (kind) {
                case KITCHEN -> ParticleTypes.SMOKE;
                case SMITHY -> ParticleTypes.CRIT;
                case MASONRY -> ParticleTypes.WHITE_ASH;
                case CARPENTRY -> ParticleTypes.WHITE_ASH;
                case HERBALISM, BARRACKS, TAVERN -> ParticleTypes.HAPPY_VILLAGER;
            };
            level.sendParticles(extra, top.x, top.y + 0.1, top.z, kind == Workshop.KITCHEN ? 2 : 4, 0.2, 0.1, 0.2, 0.02);
            if (kind == Workshop.SMITHY) io.github.operagtx.hearthholm.town.Fx.forge(level, top);
            if (kind == Workshop.KITCHEN) io.github.operagtx.hearthholm.town.Fx.cook(level, top);
        }
        if (ticks < need) return Status.RUNNING;
        ticks = 0;
        if (!finishBatch(s)) return done > 0 ? Status.DONE : Status.FAILED;
        done++;
        s.setStatus("crafting", option.output().getHoverName(), done, batches);
        return done >= batches ? Status.DONE : Status.RUNNING;
    }

    private boolean finishBatch(SettlerEntity s) {
        Map<Item, Integer> pocket = CraftPlanner.stock(s.inventory().getItems());
        Map<Item, Integer> used = CraftPlanner.allocate(option, pocket);
        if (used == null) return false;
        Settlement fuelFor = s.settlement();
        if (option.cooking() && fuelFor != null && !io.github.operagtx.hearthholm.crafting.Fuel.consume((ServerLevel) s.level(), fuelFor, io.github.operagtx.hearthholm.crafting.Fuel.BATCH)) {
            s.setStatus("no_fuel");
            return false;
        }
        used.forEach((item, n) -> s.takeItem(item, n));
        ItemStack out = option.output().copy();
        s.store(out);
        s.gainXp(2);
        Settlement set = s.settlement();
        if (set != null) {
            set.count("crafted", out.getCount());
            if (set.stats.getOrDefault("crafted", 0) % 24 < out.getCount()) set.addRenown(1);
            SettlementService.markDirty(s);
        }
        ServerLevel level = (ServerLevel) s.level();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, station.getX() + 0.5, station.getY() + 1.1, station.getZ() + 0.5, 3, 0.25, 0.1, 0.25, 0.02);
        return true;
    }

    public int done() { return done; }

    @Override
    public void stop(SettlerEntity s) {
        approach.stop(s);
    }
}
