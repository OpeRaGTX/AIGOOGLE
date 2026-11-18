package io.github.operagtx.hearthholm.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.operagtx.hearthholm.core.Professions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The settlement's view of a resident. The entity is the source of truth while loaded and
 * refreshes this record; the record keeps the town hall UI working while the entity is unloaded.
 */
public final class ResidentRecord {
    public static final Codec<ResidentRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(r -> r.uuid),
            SettlerProfile.CODEC.fieldOf("profile").forGetter(r -> r.profile),
            Identifier.CODEC.optionalFieldOf("profession", Professions.UNEMPLOYED_ID).forGetter(r -> r.profession),
            Codec.INT.optionalFieldOf("mood", 0).forGetter(r -> r.mood),
            Codec.INT.optionalFieldOf("food", 20).forGetter(r -> r.food),
            ComponentSerialization.CODEC.optionalFieldOf("status", Component.empty()).forGetter(r -> r.status),
            BlockPos.CODEC.optionalFieldOf("bed").forGetter(r -> Optional.ofNullable(r.bed)),
            BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(r -> r.lastPos),
            Codec.INT.optionalFieldOf("level", 1).forGetter(r -> r.level),
            Codec.INT.optionalFieldOf("hp", 20).forGetter(r -> r.hp),
            Codec.INT.optionalFieldOf("maxhp", 20).forGetter(r -> r.maxHp)
    ).apply(i, (uuid, profile, prof, mood, food, status, bed, pos, level, hp, maxHp) -> {
        ResidentRecord r = new ResidentRecord(uuid, profile);
        r.profession = prof;
        r.mood = mood;
        r.food = food;
        r.status = status;
        r.bed = bed.orElse(null);
        r.lastPos = pos;
        r.level = level;
        r.hp = hp;
        r.maxHp = maxHp;
        return r;
    }));

    public final UUID uuid;
    public final SettlerProfile profile;
    public Identifier profession = Professions.UNEMPLOYED_ID;
    public int mood;
    public int food = 20;
    public Component status = Component.empty();
    public @Nullable BlockPos bed;
    public BlockPos lastPos = BlockPos.ZERO;
    public int level = 1;
    public int hp = 20, maxHp = 20;

    public ResidentRecord(UUID uuid, SettlerProfile profile) {
        this.uuid = uuid;
        this.profile = profile;
    }
}
