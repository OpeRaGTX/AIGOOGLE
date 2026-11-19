package io.github.operagtx.hearthholm.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Wave E state of a settlement: decor and market positions, raid fire markers, the trader caravan and the
 * bulletin board quests. Every field has a default so old saves load unchanged.
 */
public final class Civic {
    /** A bulletin board request. {@code kind} is "bring" (hand in {@code count} of item {@code target}) or "kill"
     *  ({@code count} mobs of entity type {@code target} within {@code radius} of {@code site}). */
    public record Quest(int id, String kind, Identifier target, int count, int progress, BlockPos site, int radius,
                        String reward, int amount, Identifier gift, boolean done, boolean claimed) {
        public static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Quest::id),
                Codec.STRING.fieldOf("kind").forGetter(Quest::kind),
                Identifier.CODEC.fieldOf("target").forGetter(Quest::target),
                Codec.INT.fieldOf("count").forGetter(Quest::count),
                Codec.INT.optionalFieldOf("progress", 0).forGetter(Quest::progress),
                BlockPos.CODEC.optionalFieldOf("site", BlockPos.ZERO).forGetter(Quest::site),
                Codec.INT.optionalFieldOf("radius", 24).forGetter(Quest::radius),
                Codec.STRING.optionalFieldOf("reward", "marks").forGetter(Quest::reward),
                Codec.INT.optionalFieldOf("amount", 1).forGetter(Quest::amount),
                Identifier.CODEC.optionalFieldOf("gift", Identifier.withDefaultNamespace("air")).forGetter(Quest::gift),
                Codec.BOOL.optionalFieldOf("done", false).forGetter(Quest::done),
                Codec.BOOL.optionalFieldOf("claimed", false).forGetter(Quest::claimed)
        ).apply(i, Quest::new));

        public Quest withProgress(int p) { return new Quest(id, kind, target, count, p, site, radius, reward, amount, gift, p >= count, claimed); }
        public Quest asDone() { return new Quest(id, kind, target, count, count, site, radius, reward, amount, gift, true, claimed); }
        public Quest asClaimed() { return new Quest(id, kind, target, count, progress, site, radius, reward, amount, gift, true, true); }
    }

    public final List<Quest> quests = new ArrayList<>();
    public int nextQuestId = 1;
    public long lastQuestDay = -1;
    /** Decor blocks (lamp posts, benches...), markets and bulletin boards registered when placed; pruned on rescan. */
    public final List<BlockPos> decor = new ArrayList<>();
    public final List<BlockPos> markets = new ArrayList<>();
    public final List<BlockPos> boards = new ArrayList<>();
    /** Raid fires still to be put out (persisted so a restart cannot leave them burning). */
    public final List<BlockPos> fires = new ArrayList<>();
    /** Trader caravan: day of the last arrival, game time it leaves, the trader entity, the stock seed, spent offers. */
    public long lastCaravan = -1, caravanEnd = -1;
    public @Nullable UUID trader;
    public @Nullable UUID llama;
    public int caravanSeed;
    public final List<Integer> soldOut = new ArrayList<>();
    /** Blueprints bought from a trader (file names), usable whatever the settlement tier. */
    public final List<String> unlocked = new ArrayList<>();

    /** Wave F: the guide book was already handed to the founder. */
    public boolean guideGiven;

    /** Wave K1: co-owners the founder added in the town hall; they manage everything except removing co-owners and breaking the hall. */
    public record CoOwner(UUID id, String name) {
        public static final Codec<CoOwner> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(CoOwner::id),
                Codec.STRING.optionalFieldOf("name", "").forGetter(CoOwner::name)
        ).apply(i, CoOwner::new));
    }

    public final List<CoOwner> coOwners = new ArrayList<>();

    public boolean caravanHere(long gameTime) { return caravanEnd > gameTime; }

    public static final Codec<Civic> CODEC = RecordCodecBuilder.create(i -> i.group(
            Quest.CODEC.listOf().optionalFieldOf("quests", List.of()).forGetter(c -> c.quests),
            Codec.INT.optionalFieldOf("next_quest", 1).forGetter(c -> c.nextQuestId),
            Codec.LONG.optionalFieldOf("quest_day", -1L).forGetter(c -> c.lastQuestDay),
            BlockPos.CODEC.listOf().optionalFieldOf("decor", List.of()).forGetter(c -> c.decor),
            BlockPos.CODEC.listOf().optionalFieldOf("markets", List.of()).forGetter(c -> c.markets),
            BlockPos.CODEC.listOf().optionalFieldOf("boards", List.of()).forGetter(c -> c.boards),
            BlockPos.CODEC.listOf().optionalFieldOf("fires", List.of()).forGetter(c -> c.fires),
            Codec.LONG.optionalFieldOf("last_caravan", -1L).forGetter(c -> c.lastCaravan),
            Codec.LONG.optionalFieldOf("caravan_end", -1L).forGetter(c -> c.caravanEnd),
            UUIDUtil.CODEC.optionalFieldOf("trader").forGetter(c -> Optional.ofNullable(c.trader)),
            UUIDUtil.CODEC.optionalFieldOf("llama").forGetter(c -> Optional.ofNullable(c.llama)),
            Codec.INT.optionalFieldOf("caravan_seed", 0).forGetter(c -> c.caravanSeed),
            Codec.INT.listOf().optionalFieldOf("sold_out", List.of()).forGetter(c -> c.soldOut),
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(c -> c.unlocked),
            Codec.BOOL.optionalFieldOf("guide_given", false).forGetter(c -> c.guideGiven),
            CoOwner.CODEC.listOf().optionalFieldOf("co_owners", List.of()).forGetter(c -> c.coOwners)
    ).apply(i, (q, nq, qd, d, m, b, f, lc, ce, tr, ll, seed, so, un, gg, co) -> {
        Civic c = new Civic();
        c.quests.addAll(q);
        c.nextQuestId = nq;
        c.lastQuestDay = qd;
        c.decor.addAll(d);
        c.markets.addAll(m);
        c.boards.addAll(b);
        c.fires.addAll(f);
        c.lastCaravan = lc;
        c.caravanEnd = ce;
        c.trader = tr.orElse(null);
        c.llama = ll.orElse(null);
        c.caravanSeed = seed;
        c.soldOut.addAll(so);
        c.unlocked.addAll(un);
        c.guideGiven = gg;
        c.coOwners.addAll(co);
        return c;
    }));
}
