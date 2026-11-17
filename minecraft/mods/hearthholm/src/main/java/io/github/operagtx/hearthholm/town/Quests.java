package io.github.operagtx.hearthholm.town;

import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.network.Views;
import io.github.operagtx.hearthholm.registry.ModItems;
import io.github.operagtx.hearthholm.settlement.*;
import io.github.operagtx.hearthholm.settlement.Civic.Quest;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Bulletin board quests: three small requests at a time, renewed daily. "Bring" quests are handed in at the board,
 * "kill" quests count mobs of a type that die within a radius of a work site. Rewards: hearth marks, renown or a gift.
 */
public final class Quests {
    public static final int MAX = 3;

    private record Want(Item item, int min, int max) {}

    private static final List<Want> WANTS = List.of(new Want(Items.APPLE, 6, 12), new Want(Items.BREAD, 6, 12),
            new Want(Items.OAK_LOG, 16, 32), new Want(Items.COBBLESTONE, 32, 64), new Want(Items.WHEAT, 12, 24),
            new Want(Items.COAL, 8, 16), new Want(Items.IRON_INGOT, 4, 8), new Want(Items.CARROT, 10, 20));
    private static final List<EntityType<?>> PESTS = List.of(EntityTypes.SPIDER, EntityTypes.ZOMBIE, EntityTypes.SKELETON);
    private static final List<Item> GIFTS = List.of(Items.GOLDEN_APPLE, Items.EMERALD, Items.DIAMOND, Items.COOKED_BEEF, Items.ARROW, Items.IRON_INGOT);

    private Quests() {}

    public static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.level() instanceof ServerLevel sl) onKill(sl, entity);
        });
    }

    public static void registerBoard(ServerLevel level, BlockPos pos) {
        Settlement s = SettlementService.manager(level).at(level.dimension(), pos);
        if (s == null || s.civic.boards.contains(pos)) return;
        s.civic.boards.add(pos.immutable());
        SettlementService.manager(level).setDirty();
    }

    // ---- generation --------------------------------------------------------------------------------------

    /** Tops the board up to {@link #MAX} open quests, once per day (or {@code force}). */
    public static void refresh(ServerLevel level, Settlement s, boolean force) {
        Civic c = s.civic;
        long day = SettlementService.day(level);
        if (!force && c.lastQuestDay == day && c.quests.size() >= 1) return;
        c.lastQuestDay = day;
        c.quests.removeIf(Quest::claimed);
        while (c.quests.size() < MAX) c.quests.add(generate(level, s, level.getRandom()));
        SettlementService.manager(level).setDirty();
    }

    private static Quest generate(ServerLevel level, Settlement s, RandomSource r) {
        Civic c = s.civic;
        int id = c.nextQuestId++;
        String reward = switch (r.nextInt(3)) { case 0 -> "marks"; case 1 -> "renown"; default -> "gift"; };
        Item giftItem = GIFTS.get(r.nextInt(GIFTS.size()));
        int giftCount = giftItem == Items.GOLDEN_APPLE || giftItem == Items.DIAMOND ? 1 : giftItem == Items.EMERALD ? 2 + r.nextInt(3) : 8 + r.nextInt(9);
        Identifier giftId = BuiltInRegistries.ITEM.getKey(giftItem);
        boolean kill = r.nextInt(3) == 0;
        if (kill) {
            EntityType<?> type = PESTS.get(r.nextInt(PESTS.size()));
            BlockPos site = s.center;
            if (!s.sites.isEmpty()) site = s.sites.get(r.nextInt(s.sites.size())).pos();
            int count = 3 + r.nextInt(3);
            int amount = switch (reward) { case "marks" -> io.github.operagtx.hearthholm.core.Balance.QUEST_KILL_MARKS_BASE + count; case "renown" -> io.github.operagtx.hearthholm.core.Balance.QUEST_KILL_RENOWN_BASE + io.github.operagtx.hearthholm.core.Balance.QUEST_KILL_RENOWN_PER_MOB * count; default -> giftCount; };
            return new Quest(id, "kill", BuiltInRegistries.ENTITY_TYPE.getKey(type), count, 0, site, 28, reward, amount, giftId, false, false);
        }
        Want w = WANTS.get(r.nextInt(WANTS.size()));
        int count = w.min() + r.nextInt(w.max() - w.min() + 1);
        int amount = switch (reward) { case "marks" -> io.github.operagtx.hearthholm.core.Balance.QUEST_BRING_MARKS_BASE + count / io.github.operagtx.hearthholm.core.Balance.QUEST_BRING_MARKS_DIV; case "renown" -> io.github.operagtx.hearthholm.core.Balance.QUEST_BRING_RENOWN_BASE + count / io.github.operagtx.hearthholm.core.Balance.QUEST_BRING_RENOWN_DIV; default -> giftCount; };
        return new Quest(id, "bring", BuiltInRegistries.ITEM.getKey(w.item()), count, 0, BlockPos.ZERO, 0, reward, amount, giftId, false, false);
    }

    // ---- kills -------------------------------------------------------------------------------------------

    /** A mob died: advance every open kill quest (of settlements in this dimension) whose site it died near. Returns quests advanced. */
    public static int onKill(ServerLevel level, LivingEntity dead) {
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        int n = 0;
        for (Settlement s : SettlementService.manager(level).all()) {
            if (!s.dimension.equals(level.dimension())) continue;
            List<Quest> list = s.civic.quests;
            for (int i = 0; i < list.size(); i++) {
                Quest q = list.get(i);
                if (q.done() || !q.kind().equals("kill") || !q.target().equals(type)) continue;
                if (dead.blockPosition().distSqr(q.site()) > (double) q.radius() * q.radius()) continue;
                Quest next = q.withProgress(q.progress() + 1);
                list.set(i, next);
                n++;
                if (next.done()) {
                    SettlementService.notify(level, s, Component.translatable("message.hearthholm.quest_done", describe(level, s, next)).withStyle(ChatFormatting.GREEN));
                    Fx.quest(level, q.site());
                }
                SettlementService.manager(level).setDirty();
            }
        }
        return n;
    }

    // ---- views and actions -------------------------------------------------------------------------------

    private static Component siteName(Settlement s, BlockPos site) {
        for (Settlement.SiteRecord r : s.sites) if (r.pos().equals(site)) return WorkSiteTypes.get(r.type()).label();
        return Component.translatable("quest.hearthholm.town");
    }

    public static Component describe(ServerLevel level, Settlement s, Quest q) {
        Item item = BuiltInRegistries.ITEM.getValue(q.target());
        if (q.kind().equals("bring")) return Component.translatable("quest.hearthholm.bring", q.count(), item.getName(new ItemStack(item)));
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(q.target());
        return Component.translatable("quest.hearthholm.kill", q.count(), type.getDescription(), siteName(s, q.site()));
    }

    private static Component rewardText(Quest q) {
        return switch (q.reward()) {
            case "marks" -> Component.translatable("quest.hearthholm.reward.marks", q.amount());
            case "renown" -> Component.translatable("quest.hearthholm.reward.renown", q.amount());
            default -> Component.translatable("quest.hearthholm.reward.gift", q.amount(), BuiltInRegistries.ITEM.getValue(q.gift()).getName(new ItemStack(BuiltInRegistries.ITEM.getValue(q.gift()))));
        };
    }

    public static Views.Board view(ServerLevel level, Settlement s, ServerPlayer player, BlockPos pos) {
        List<Views.QuestLine> lines = new ArrayList<>();
        for (Quest q : s.civic.quests) {
            ItemStack icon = q.kind().equals("bring") ? new ItemStack(BuiltInRegistries.ITEM.getValue(q.target())) : new ItemStack(Items.IRON_SWORD);
            int progress = q.kind().equals("bring") ? Math.min(q.count(), count(player, BuiltInRegistries.ITEM.getValue(q.target()))) : q.progress();
            lines.add(new Views.QuestLine(q.id(), icon, describe(level, s, q), progress, q.count(), q.done(), q.claimed(),
                    q.kind().equals("bring") && !q.done() && progress >= q.count(), rewardText(q)));
        }
        return new Views.Board(pos, Component.translatable("gui.hearthholm.board_title", s.displayName()), lines);
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack st : p.getInventory().getNonEquipmentItems()) if (st.is(item)) n += st.getCount();
        return n;
    }

    private static void take(ServerPlayer p, Item item, int n) {
        for (ItemStack st : p.getInventory().getNonEquipmentItems()) {
            if (n <= 0) break;
            if (st.is(item)) {
                int k = Math.min(n, st.getCount());
                st.shrink(k);
                n -= k;
            }
        }
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        Settlement s = SettlementService.manager(level).at(level.dimension(), pos);
        if (s == null) { SettlementService.say(player, "outside"); return; }
        if (!SettlementService.canManage(player, s)) { SettlementService.say(player, "not_owner", s.ownerName); return; }
        refresh(level, s, false);
        ServerPlayNetworking.send(player, new Payloads.OpenBoard(view(level, s, player, pos)));
    }

    public static void handle(ServerPlayer player, Payloads.BoardAction a) {
        ServerLevel level = player.level();
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(a.pos())) > 100) return;
        Settlement s = SettlementService.manager(level).at(level.dimension(), a.pos());
        if (s == null || !SettlementService.canManage(player, s)) return;
        if (a.action() == Payloads.BoardAction.DELIVER) deliver(player, level, s, a.quest());
        else if (a.action() == Payloads.BoardAction.CLAIM) claim(player, level, s, a.quest());
        ServerPlayNetworking.send(player, new Payloads.OpenBoard(view(level, s, player, a.pos())));
    }

    private static int indexOf(Settlement s, int id) {
        for (int i = 0; i < s.civic.quests.size(); i++) if (s.civic.quests.get(i).id() == id) return i;
        return -1;
    }

    /** Hands in the items of a "bring" quest from the player's pockets. */
    public static boolean deliver(ServerPlayer player, ServerLevel level, Settlement s, int id) {
        int i = indexOf(s, id);
        if (i < 0) return false;
        Quest q = s.civic.quests.get(i);
        Item item = BuiltInRegistries.ITEM.getValue(q.target());
        if (q.done() || !q.kind().equals("bring") || count(player, item) < q.count()) return false;
        take(player, item, q.count());
        s.civic.quests.set(i, q.asDone());
        Fx.quest(level, player.blockPosition());
        SettlementService.manager(level).setDirty();
        return true;
    }

    /** Pays the reward of a finished quest: marks into the warehouse, renown to the settlement, a gift to the player. */
    public static boolean claim(ServerPlayer player, ServerLevel level, Settlement s, int id) {
        int i = indexOf(s, id);
        if (i < 0) return false;
        Quest q = s.civic.quests.get(i);
        if (!q.done() || q.claimed()) return false;
        switch (q.reward()) {
            case "marks" -> {
                List<Container> storage = Storage.resolve(level, s.storagePositions());
                ItemStack rest = Storage.insert(storage, new ItemStack(ModItems.HEARTH_MARK, q.amount()));
                if (!rest.isEmpty() && !player.getInventory().add(rest)) player.spawnAtLocation(level, rest);
            }
            case "renown" -> s.addRenown(q.amount());
            default -> {
                ItemStack gift = new ItemStack(BuiltInRegistries.ITEM.getValue(q.gift()), q.amount());
                if (!player.getInventory().add(gift)) player.spawnAtLocation(level, gift);
            }
        }
        s.civic.quests.set(i, q.asClaimed());
        s.count("quests_done", 1);
        player.sendSystemMessage(Component.translatable("message.hearthholm.quest_reward", rewardText(q)).withStyle(ChatFormatting.GOLD));
        Fx.quest(level, player.blockPosition());
        SettlementService.manager(level).setDirty();
        return true;
    }
}
