package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.threat.Defense;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Alarm bell: ringing it sends civilians indoors and combat settlers to the bell; ring again for all-clear. */
public class AlarmBellBlock extends HorizontalBlock {
    public AlarmBellBlock(Properties properties) {
        super(properties, Block.box(2, 0, 2, 14, 16, 14));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) Defense.onBellPlaced(server, pos, placer instanceof ServerPlayer p ? p : null);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) Defense.ringBell(server, pos, player instanceof ServerPlayer sp ? sp : null);
        return InteractionResult.SUCCESS;
    }
}
