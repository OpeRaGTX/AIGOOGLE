package io.github.operagtx.hearthholm.town;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.settlement.SettlementService;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Wave I2: drops of hostile mobs killed by a settler or inside a settlement get a claim tag; guards and archers pick up tagged
 * drops (and the arrows an archer shot that lie on the ground) around their posts and carry them to the warehouse.
 */
public final class Loot {
    public static final String TAG = "hh_loot";

    private Loot() {}

    public static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity.level() instanceof ServerLevel sl) || !(entity instanceof Enemy)) return;
            boolean town = source.getEntity() instanceof SettlerEntity || SettlementService.manager(sl).at(sl.dimension(), entity.blockPosition()) != null;
            if (town) tagDrops(sl, entity.position());
        });
    }

    /** Marks fresh item drops around a point as town loot. */
    public static void tagDrops(ServerLevel level, Vec3 at) {
        for (ItemEntity i : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3, 2, 3), e -> e.tickCount < 40)) i.addTag(TAG);
    }

    /** The closest claimable drop or loose own arrow within {@code radius} of {@code centre}. */
    public static @Nullable Entity nearest(SettlerEntity s, Vec3 centre, double radius) {
        AABB box = new AABB(centre, centre).inflate(radius, 4, radius);
        List<Entity> found = new java.util.ArrayList<>(s.level().getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && e.entityTags().contains(TAG) && !e.getItem().isEmpty()));
        if (io.github.operagtx.hearthholm.core.Professions.isShooter(s.professionId())) {
            found.addAll(s.level().getEntitiesOfClass(AbstractArrow.class, box, a -> a.isAlive() && a.getOwner() == s
                    && a.tickCount > 40 && a.getDeltaMovement().lengthSqr() < 1.0E-4));
        }
        return found.stream().min(Comparator.comparingDouble(s::distanceToSqr)).orElse(null);
    }

    public static boolean isLoot(Entity e) {
        return e instanceof ItemEntity || e instanceof AbstractArrow;
    }

    /** Puts what the entity holds into the settler's pockets; the rest of a stack stays on the ground. */
    public static void collect(SettlerEntity s, Entity e) {
        if (e instanceof ItemEntity item) {
            var rest = s.inventory().addItem(item.getItem().copy());
            if (rest.isEmpty()) item.discard(); else item.setItem(rest);
        } else if (e instanceof AbstractArrow arrow) {
            s.inventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW));
            arrow.discard();
        }
        s.swingArm(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
}
