package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.town.Decor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Settlement banner: right-click with a dye to recolour the cloth, with an empty hand to cycle the emblem colour. */
public class SettlementBannerBlock extends DecorBlock {
    public static final EnumProperty<DyeColor> CLOTH = EnumProperty.create("cloth", DyeColor.class);
    public static final IntegerProperty EMBLEM = IntegerProperty.create("emblem", 0, 3);

    public SettlementBannerBlock(Properties properties) {
        super(properties, Block.box(2, 0, 2, 14, 16, 14));
        registerDefaultState(defaultBlockState().setValue(CLOTH, DyeColor.RED).setValue(EMBLEM, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CLOTH, EMBLEM);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        DyeColor dye = stack.get(DataComponents.DYE);
        if (dye == null) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide() && dye != state.getValue(CLOTH)) {
            level.setBlock(pos, state.setValue(CLOTH, dye), 3);
            if (!player.isCreative()) stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) level.setBlock(pos, state.setValue(EMBLEM, (state.getValue(EMBLEM) + 1) % 4), 3);
        return InteractionResult.SUCCESS;
    }
}
