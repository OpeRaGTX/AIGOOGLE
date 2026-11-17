package io.github.operagtx.hearthholm.block;

import io.github.operagtx.hearthholm.threat.Defense;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Trophy taken from the Ash Chieftain. Standing in the town hall it cheers every settler up. */
public class ChieftainBannerBlock extends HorizontalBlock {
    public ChieftainBannerBlock(Properties properties) {
        super(properties, Block.box(3, 0, 3, 13, 16, 13));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) Defense.onTrophyPlaced(server, pos);
    }
}
