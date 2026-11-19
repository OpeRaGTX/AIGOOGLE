package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.threat.Defense;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import org.jetbrains.annotations.Nullable;

/** Two-high log gate: settlers and players open it like a door, raiders cannot; it shuts when the alarm rings. */
public class PalisadeGateBlock extends DoorBlock {
    public PalisadeGateBlock(Properties properties) {
        super(BlockSetType.OAK, properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) Defense.onGatePlaced(server, pos);
    }
}
