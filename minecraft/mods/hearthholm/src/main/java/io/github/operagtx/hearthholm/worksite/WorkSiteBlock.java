package io.github.operagtx.hearthholm.worksite;

import io.github.operagtx.hearthholm.block.FacingEntityBlock;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** One block class for every work-zone marker; behaviour comes from its {@link WorkSiteType}. */
public class WorkSiteBlock extends FacingEntityBlock {
    private static final VoxelShape POST = box(5, 0, 5, 11, 15, 11);
    /** Wave J2: the lumberjack's chopping block is a wide stump with an axe, so its outline is wider than the sign post. */
    private static final VoxelShape STUMP = box(1, 0, 1, 15, 16, 15);
    private final Identifier typeId;

    public WorkSiteBlock(Properties properties, Identifier typeId) {
        super(properties);
        this.typeId = typeId;
    }

    public WorkSiteType type() {
        return WorkSiteTypes.get(typeId);
    }

    public Identifier typeId() {
        return typeId;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return typeId.equals(WorkSiteTypes.FOREST_ID) || typeId.equals(WorkSiteTypes.MINE_ID) ? STUMP : POST;
    }

    /** Walk-through like a sign: the outline (getShape) stays so the marker can be clicked and broken. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorkSiteBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) {
            SettlementService.onWorkSitePlaced(server, pos, placer instanceof ServerPlayer p ? p : null);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) SettlementService.openWorkSite(sp, pos);
        return InteractionResult.SUCCESS;
    }
}
