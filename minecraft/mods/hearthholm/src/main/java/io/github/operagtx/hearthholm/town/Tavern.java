package io.github.operagtx.hearthholm.town;

import io.github.operagtx.hearthholm.crafting.Workshop;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.settlement.BuildingRecord;
import io.github.operagtx.hearthholm.settlement.Settlement;
import io.github.operagtx.hearthholm.settlement.Storage;
import io.github.operagtx.hearthholm.social.Social;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The tavern: a valid room with a keg. In the evening off-duty settlers gather there; the innkeeper serves food from storage. */
public final class Tavern {
    public static final int SERVE_RADIUS = 8, MEAL_POINTS = 4;

    private Tavern() {}

    /** The keg of the first valid tavern, or null. */
    public static @Nullable BlockPos keg(@Nullable Settlement st) {
        if (st == null) return null;
        for (BuildingRecord b : st.buildings) {
            if (b.valid() && b.is(Workshop.TAVERN.role()) && !b.stations().isEmpty()) return b.stations().getFirst();
        }
        return null;
    }

    /** Off duty and awake: evening for day workers (night shifts never are). */
    public static boolean offDuty(SettlerEntity s) {
        return !s.isSleepTime() && !s.isWorkTime();
    }

    public static boolean isPatron(SettlerEntity s) {
        return s.hasThought("tavern_evening");
    }

    /** A patron arrived: the gathering mood bonus and a line. */
    public static void arrive(SettlerEntity s) {
        if (!s.hasThought("tavern_evening")) {
            s.addThought("tavern_evening", io.github.operagtx.hearthholm.core.Balance.MOOD_TAVERN_EVENING, 24000);
            Social.say(s, "tavern", 4);
            if (s.settlement() != null) s.settlement().count("tavern_visits", 1);
        }
    }

    /** The innkeeper pours for one hungry patron near the keg. Returns true when somebody was served. */
    public static boolean serve(ServerLevel level, Settlement st, SettlerEntity keeper, BlockPos keg) {
        List<Container> storage = Storage.resolve(level, st.storagePositions());
        SettlerEntity guest = null;
        for (SettlerEntity e : level.getEntitiesOfClass(SettlerEntity.class, new net.minecraft.world.phys.AABB(keg).inflate(SERVE_RADIUS, 3, SERVE_RADIUS),
                x -> x != keeper && x.isAlive() && isPatron(x) && !x.hasThought("tavern_meal"))) {
            if (guest == null || e.food() < guest.food()) guest = e;
        }
        if (guest == null) return false;
        if (Storage.totalNutrition(storage) < MEAL_POINTS) {
            keeper.setStatus("tavern_empty");
            return false;
        }
        Storage.consumeNutrition(storage, MEAL_POINTS);
        guest.setFood(guest.food() + MEAL_POINTS);
        guest.addThought("tavern_meal", io.github.operagtx.hearthholm.core.Balance.MOOD_TAVERN_MEAL, 24000);
        keeper.swingArm(net.minecraft.world.InteractionHand.MAIN_HAND);
        level.playSound(null, keg, SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 0.7f, 1f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, guest.getX(), guest.getY() + 2, guest.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
        st.count("tavern_served", 1);
        keeper.gainXp(2);
        return true;
    }
}
