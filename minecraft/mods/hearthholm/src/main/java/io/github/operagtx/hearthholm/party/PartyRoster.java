package io.github.operagtx.hearthholm.party;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.operagtx.hearthholm.Hearthholm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Wave G: last known place of every party member (saved), so members whose chunk is unloaded can still be found,
 * force-loaded and brought to the leader. Updated while a member is loaded and when its chunk unloads.
 */
public final class PartyRoster extends SavedData {
    public static final class Entry {
        public final UUID member, owner;
        public ResourceKey<Level> dim;
        public BlockPos pos;
        public transient long retryAt;
        public transient int fails;

        public Entry(UUID member, UUID owner, ResourceKey<Level> dim, BlockPos pos) {
            this.member = member; this.owner = owner; this.dim = dim; this.pos = pos;
        }

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("member").forGetter(e -> e.member),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(e -> e.owner),
                Level.RESOURCE_KEY_CODEC.fieldOf("dim").forGetter(e -> e.dim),
                BlockPos.CODEC.fieldOf("pos").forGetter(e -> e.pos)
        ).apply(i, Entry::new));
    }

    private static final Codec<PartyRoster> CODEC = Entry.CODEC.listOf().xmap(PartyRoster::new, r -> List.copyOf(r.entries.values()));
    private static final SavedDataType<PartyRoster> TYPE = new SavedDataType<>(
            Hearthholm.id("party_roster"), PartyRoster::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public PartyRoster() {}

    private PartyRoster(List<Entry> list) {
        for (Entry e : list) entries.put(e.member, e);
    }

    public static PartyRoster get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<Entry> all() { return entries.values(); }

    public Entry get(UUID member) { return entries.get(member); }

    /** Records where a member is; marks the data dirty only when it changed noticeably. */
    public void put(UUID member, UUID owner, ResourceKey<Level> dim, BlockPos pos) {
        Entry e = entries.get(member);
        if (e == null) {
            entries.put(member, new Entry(member, owner, dim, pos.immutable()));
            setDirty();
        } else if (!e.owner.equals(owner) || !e.dim.equals(dim) || e.pos.distSqr(pos) > 16) {
            Entry n = new Entry(member, owner, dim, pos.immutable());
            n.retryAt = e.retryAt;
            n.fails = e.fails;
            entries.put(member, n);
            setDirty();
        }
    }

    public void remove(UUID member) {
        if (entries.remove(member) != null) setDirty();
    }
}
