package io.github.operagtx.hearthholm.worksite;

import com.mojang.serialization.Codec;
import io.github.operagtx.hearthholm.registry.ModBlockEntities;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.zone.WorkZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * State of a work-zone marker: option values, job progress (an index the job interprets),
 * the settlement it belongs to and the settler currently working it.
 */
public class WorkSiteBlockEntity extends BlockEntity {
    private static final Codec<Map<String, Integer>> CONFIG_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);

    private final Map<String, Integer> config = new HashMap<>();
    private int progress;
    private @Nullable UUID settlementId;
    private @Nullable UUID workerId;
    private String status = "";
    /** Wave J2: the hand-drawn map zone (null = none drawn, the old radius applies). Saved with the block, gone with it. */
    private @Nullable WorkZone zone;

    public WorkSiteBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WORK_SITE, pos, state);
    }

    public WorkSiteType type() {
        return ((WorkSiteBlock) getBlockState().getBlock()).type();
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(WorkSiteBlock.FACING) ? state.getValue(WorkSiteBlock.FACING) : Direction.NORTH;
    }

    public int option(String key) {
        Integer v = config.get(key);
        if (v != null) return v;
        SiteOption o = type().option(key);
        return o == null ? 0 : o.def();
    }

    public boolean flag(String key) {
        return option(key) != 0;
    }

    public void setOption(String key, int value) {
        SiteOption o = type().option(key);
        if (o == null) return;
        config.put(key, o.clamp(value));
        // Plans are recomputed from options; restarting the cursor is safe because jobs skip finished steps.
        progress = 0;
        sync();
    }

    /** The drawn zone, or null when nothing is drawn. */
    public @Nullable WorkZone zone() { return zone; }

    /** Whether a non-empty zone is drawn; jobs fall back to the radius otherwise. */
    public boolean hasZone() { return zone != null && !zone.isEmpty(); }

    /** The zone to edit: the existing one or a new empty one centred on the block (grid size from the type's {@link WorkZone.Spec}). */
    public WorkZone zoneForEdit() {
        if (zone == null) zone = WorkZone.centred(zoneSpec(), worldPosition);
        return zone;
    }

    public WorkZone.Spec zoneSpec() {
        WorkZone.Spec spec = WorkZone.specOf(type().id());
        return spec == null ? new WorkZone.Spec(WorkZone.DEFAULT_SIZE, 1, false) : spec;
    }

    /** Call after changing the zone: forgets an emptied zone and saves. */
    public void zoneChanged() {
        if (zone != null && zone.isEmpty()) zone = null;
        setChanged();
    }

    public int progress() { return progress; }

    public void setProgress(int progress) {
        if (this.progress != progress) {
            this.progress = progress;
            setChanged();
        }
    }

    public @Nullable UUID settlementId() { return settlementId; }

    public void setSettlementId(@Nullable UUID id) {
        this.settlementId = id;
        sync();
    }

    public @Nullable UUID workerId() { return workerId; }

    public void setWorkerId(@Nullable UUID id) {
        if (!java.util.Objects.equals(workerId, id)) {
            this.workerId = id;
            sync();
        }
    }

    public String status() { return status; }

    public void setStatus(String status) {
        if (!this.status.equals(status)) {
            this.status = status;
            setChanged();
        }
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Config", CONFIG_CODEC, Map.copyOf(config));
        out.putInt("Progress", progress);
        out.storeNullable("Settlement", UUIDUtil.CODEC, settlementId);
        out.storeNullable("Worker", UUIDUtil.CODEC, workerId);
        out.putString("Status", status);
        if (zone != null && !zone.isEmpty()) out.store("Zone", WorkZone.CODEC, zone);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        config.clear();
        in.read("Config", CONFIG_CODEC).ifPresent(config::putAll);
        progress = in.getIntOr("Progress", 0);
        settlementId = in.read("Settlement", UUIDUtil.CODEC).orElse(null);
        workerId = in.read("Worker", UUIDUtil.CODEC).orElse(null);
        status = in.getStringOr("Status", "");
        zone = in.read("Zone", WorkZone.CODEC).orElse(null);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server && settlementId != null) {
            SettlementService.onWorkSiteRemoved(server, pos, settlementId);
        }
    }
}
