package io.github.operagtx.hearthholm.entity.trader;

import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import io.github.operagtx.hearthholm.town.Caravan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/** The caravan trader: walks to the market, stands there with his llama and trades when right-clicked. Leaves after a day. */
public class TraderEntity extends PathfinderMob {
    private @Nullable UUID settlementId;
    private @Nullable BlockPos market;

    public TraderEntity(EntityType<? extends TraderEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 24);
    }

    public void setCaravan(UUID settlement, BlockPos market) {
        this.settlementId = settlement;
        this.market = market.immutable();
    }

    public @Nullable BlockPos market() { return market; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new GoToMarketGoal());
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean requiresCustomPersistence() { return true; }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND) Caravan.openFromTrader(sp, this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel sl && tickCount % 100 == 50) {
            Settlement st = settlementId == null ? null : SettlementService.manager(sl).byId(settlementId);
            if (st == null || !st.civic.caravanHere(sl.getGameTime()) || !getUUID().equals(st.civic.trader)) Caravan.discardWithLlama(sl, this);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.storeNullable("Settlement", UUIDUtil.CODEC, settlementId);
        out.storeNullable("Market", BlockPos.CODEC, market);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        settlementId = in.read("Settlement", UUIDUtil.CODEC).orElse(null);
        market = in.read("Market", BlockPos.CODEC).orElse(null);
    }

    private final class GoToMarketGoal extends Goal {
        private int repath;

        GoToMarketGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            return market != null && distanceToSqr(market.getX() + 0.5, market.getY(), market.getZ() + 0.5) > 9;
        }

        @Override
        public boolean canContinueToUse() { return canUse(); }

        @Override
        public void tick() {
            if (market != null && --repath <= 0) {
                repath = 20;
                getNavigation().moveTo(market.getX() + 0.5, market.getY(), market.getZ() + 1.5, 0.9);
            }
        }
    }
}
