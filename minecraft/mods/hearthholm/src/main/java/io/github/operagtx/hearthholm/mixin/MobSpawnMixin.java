package io.github.operagtx.hearthholm.mixin;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.block.RiftLanternBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rift lanterns: no natural hostile spawns within 24 blocks (a cheap POI lookup per spawn attempt). */
/* PathfinderMob overrides Mob#checkSpawnRules without calling super, so both are hooked. */
@Mixin({Mob.class, net.minecraft.world.entity.PathfinderMob.class})
public abstract class MobSpawnMixin {
    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private void hearthholm$riftLantern(LevelAccessor level, EntitySpawnReason reason, CallbackInfoReturnable<Boolean> cir) {
        if (reason != EntitySpawnReason.NATURAL || !((Object) this instanceof Enemy) || Hearthholm.RIFT_LANTERN_POI == null) return;
        ServerLevel server = level instanceof ServerLevel sl ? sl : null;
        if (server == null) return;
        Mob self = (Mob) (Object) this;
        if (server.getPoiManager().getCountInRange(h -> h.value() == Hearthholm.RIFT_LANTERN_POI, self.blockPosition(),
                RiftLanternBlock.RADIUS, PoiManager.Occupancy.ANY) > 0) cir.setReturnValue(false);
    }
}
