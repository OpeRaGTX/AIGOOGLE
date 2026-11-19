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
        // wave K1: every mod recipe is in the recipe book from the first login
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> unlockRecipes(handler.getPlayer()));
        // wave K1: only the founder breaks the town hall
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            if (!(state.getBlock() instanceof io.github.operagtx.hearthholm.block.TownHallDeskBlock) || !(level instanceof ServerLevel sl)
                    || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) return true;
            Settlement st = SettlementService.deskSettlement(sl, pos);
            if (st == null || SettlementService.isFounder(sp, st) || sp.isCreative()) return true;
            SettlementService.say(sp, "hall_founder_only", st.ownerName);
            return false;
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            // wave G: a party member whose chunk unloads leaves its last position on the roster
            if (entity instanceof SettlerEntity se && se.partyOwner() != null && se.getRemovalReason() != null && se.getRemovalReason().shouldSave())
                io.github.operagtx.hearthholm.party.Party.remember(se);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Zombie || entity instanceof AbstractSkeleton || entity instanceof Spider || entity instanceof AbstractIllager) {
                Mob mob = (Mob) entity;
                ((MobAccessor) mob).hearthholm$targetSelector().addGoal(3, new NearestAttackableTargetGoal<>(mob, SettlerEntity.class, true));
            }
        });
    }

    /** Wave K1: awards all recipes of this mod (not only the ones found through advancements). */
    public static int unlockRecipes(net.minecraft.server.level.ServerPlayer player) {
        java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> mine = new java.util.ArrayList<>();
        for (net.minecraft.world.item.crafting.RecipeHolder<?> h : player.level().getServer().getRecipeManager().getRecipes())
            if (h.id().identifier().getNamespace().equals(io.github.operagtx.hearthholm.Hearthholm.MOD_ID)) mine.add(h);
        return player.awardRecipes(mine);
    }

    private static void tick(MinecraftServer server) {
        io.github.operagtx.hearthholm.party.Party.tick(server);
        if (server.getTickCount() % 20 != 0) return;
        io.github.operagtx.hearthholm.threat.Threats.serverTick(server);
        SettlementManager m = SettlementManager.get(server);
        for (Settlement s : m.all()) {
            ServerLevel level = server.getLevel(s.dimension);
            if (level == null || !level.isLoaded(s.center)) continue;
            if (level.getGameTime() - s.lastScan >= 600) {
                for (Component c : s.rescan(level)) SettlementService.notify(level, s, c);
                m.setDirty();
            }
            int tierNow = s.tier().index() + 1, tierSeen = s.stats.getOrDefault("tier_seen", 0);
            if (tierSeen != tierNow) {
                s.stats.put("tier_seen", tierNow);
                m.setDirty();
                if (tierSeen > 0 && tierNow > tierSeen) {
                    io.github.operagtx.hearthholm.town.Fx.levelUp(level, s.center, true);
                    SettlementService.notify(level, s, net.minecraft.network.chat.Component.translatable("message.hearthholm.tier_up", s.tier().label()).withStyle(net.minecraft.ChatFormatting.GOLD));
                }
            }
            Festival.tick(level, s);
            io.github.operagtx.hearthholm.town.Caravan.tick(level, s);
            io.github.operagtx.hearthholm.threat.Threats.tick(level, s);
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
