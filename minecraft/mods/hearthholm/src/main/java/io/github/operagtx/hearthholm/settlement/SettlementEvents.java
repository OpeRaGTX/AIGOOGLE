package io.github.operagtx.hearthholm.settlement;

import io.github.operagtx.hearthholm.entity.SettlerEntity;
import io.github.operagtx.hearthholm.mixin.MobAccessor;
import io.github.operagtx.hearthholm.social.Social;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** Periodic settlement upkeep and making hostile mobs see settlers as prey. */
public final class SettlementEvents {
    private SettlementEvents() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SettlementEvents::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Zombie || entity instanceof AbstractSkeleton || entity instanceof Spider || entity instanceof AbstractIllager) {
                Mob mob = (Mob) entity;
                ((MobAccessor) mob).hearthholm$targetSelector().addGoal(3, new NearestAttackableTargetGoal<>(mob, SettlerEntity.class, true));
            }
        });
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        SettlementManager m = SettlementManager.get(server);
        for (Settlement s : m.all()) {
            ServerLevel level = server.getLevel(s.dimension);
            if (level == null || !level.isLoaded(s.center)) continue;
            if (level.getGameTime() - s.lastScan >= 600) {
                for (Component c : s.rescan(level)) SettlementService.notify(level, s, c);
                m.setDirty();
            }
            Festival.tick(level, s);
            Social.tickSettlement(level, s);
            int day = (int) SettlementService.day(level);
            if (s.stats.getOrDefault("last_day", -1) != day) {
                s.stats.put("last_day", day);
                int happy = 0;
                for (ResidentRecord r : s.residents) if (r.mood >= 20) happy++;
                if (happy > 0) s.addRenown(happy * 2);
                m.setDirty();
            }
        }
    }
}
