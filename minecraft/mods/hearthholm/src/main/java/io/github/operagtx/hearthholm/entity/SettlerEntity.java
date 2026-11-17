package io.github.operagtx.hearthholm.entity;

import com.mojang.serialization.Codec;
import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.core.Trait;
import io.github.operagtx.hearthholm.entity.ai.SettlerBrain;
import io.github.operagtx.hearthholm.entity.ai.goals.*;
import io.github.operagtx.hearthholm.settlement.*;
import io.github.operagtx.hearthholm.social.Social;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * A hired settler. Holds its needs and inventory; all decisions are made by {@link SettlerBrain}
 * (activities: leave, arrive, sleep, eat, work, idle) running inside one vanilla goal, while
 * vanilla goals handle swimming, doors, fleeing and combat.
 */
public class SettlerEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_PROFESSION = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_NAME = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Component> DATA_STATUS = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Component> DATA_BUBBLE = SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.COMPONENT);
    private static final Codec<Map<String, Integer>> AFFINITY_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);
    private static final Codec<Map<String, Integer>> XP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);
    public static final int[] LEVEL_XP = {0, 60, 180, 400, 800};
    public static final float MAX_FOOD = 20f;

    private SettlerProfile profile = SettlerProfile.DEFAULT;
    private @Nullable UUID settlementId;
    private final SimpleContainer inventory = new SimpleContainer(18);
    private float food = MAX_FOOD;
    private float mood = 10f;
    private int unhappyTicks;
    private boolean warnedUnhappy;
    private boolean sleptLastNight = true;
    private boolean leaving;
    private boolean arrived = true;
    private long lastHurt = -100000;
    /** Timed mood entries ("Saw the festival +12"), saved. */
    private final List<Thought> thoughts = new ArrayList<>();
    private boolean moodDirty;
    /** Affinity towards other settlers (uuid string to -100..100), saved. */
    private final Map<String, Integer> affinity = new HashMap<>();
    private int bubbleTicks, sayCooldown = 200;
    private long greetedUntil, lastChatDay = -1, festivalSeen = -1;
    private final Map<String, Integer> lastBubble = new HashMap<>();
    private @Nullable UUID chatPartner;
    private boolean chatInitiator;
    private long chatDeadline;
    private final Map<String, Integer> xp = new HashMap<>();
    private List<MoodEntry> moodEntries = List.of();
    private final SettlerBrain brain;

    public SettlerEntity(EntityType<? extends SettlerEntity> type, Level level) {
        super(type, level);
        this.brain = new SettlerBrain(this);
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
        }
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 1.5)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(2, new SettlerFleeGoal(this));
        goalSelector.addGoal(2, new SettlerMeleeGoal(this));
        goalSelector.addGoal(5, new SettlerBrainGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new SettlerDefendGoal(this));
        targetSelector.addGoal(2, new SettlerTargetGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LOOK, 0);
        builder.define(DATA_NAME, 0);
        builder.define(DATA_PROFESSION, Professions.UNEMPLOYED_ID.toString());
        builder.define(DATA_STATUS, Component.empty());
        builder.define(DATA_BUBBLE, Component.empty());
    }

    // ---- identity -------------------------------------------------------------------------

    public void setup(SettlerProfile profile, UUID settlementId) {
        this.profile = profile;
        this.settlementId = settlementId;
        this.arrived = false;
        entityData.set(DATA_LOOK, profile.appearance());
        entityData.set(DATA_NAME, profile.nameIndex() | (profile.female() ? 1 << 16 : 0));
        float hp = 20 + profile.stat(io.github.operagtx.hearthholm.core.Stat.ENDURANCE) / 2f;
        for (Trait t : profile.traitList()) hp += t.maxHealthBonus();
        Objects.requireNonNull(getAttribute(Attributes.MAX_HEALTH)).setBaseValue(hp);
        Objects.requireNonNull(getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(1 + profile.stat(io.github.operagtx.hearthholm.core.Stat.STRENGTH) * 0.25);
        setHealth(getMaxHealth());
        refreshName();
    }

    public SettlerProfile profile() { return profile; }
    public int lookBits() { return entityData.get(DATA_LOOK); }
    public @Nullable UUID settlementId() { return settlementId; }
    public SimpleContainer inventory() { return inventory; }
    public SettlerBrain brain() { return brain; }
    public float food() { return food; }
    public float mood() { return mood; }
    public List<MoodEntry> moodEntries() { return moodEntries; }
    public boolean isLeaving() { return leaving; }
    public boolean hasArrived() { return arrived; }
    public void markArrived() { arrived = true; }
    public boolean sleptLastNight() { return sleptLastNight; }
    public boolean hadTastyMeal() { return hasThought("tasty_meal"); }
    public void setSleptLastNight(boolean v) { sleptLastNight = v; }

    // ---- thoughts, bubbles, relationships ---------------------------------------------------

    public List<Thought> thoughts() { return thoughts; }

    public boolean hasThought(String id) {
        long now = level().getGameTime();
        for (Thought t : thoughts) if (t.id().equals(id) && t.expiry() > now) return true;
        return false;
    }

    /** Adds or refreshes a thought; it fades after {@code duration} ticks. */
    public void addThought(String id, int value, long duration) {
        putThought(id, value, duration);
        moodDirty = true;
    }

    private void putThought(String id, int value, long duration) {
        thoughts.removeIf(t -> t.id().equals(id));
        thoughts.add(new Thought(id, value, level().getGameTime() + duration));
    }

    public Component bubble() { return entityData.get(DATA_BUBBLE); }
    public boolean hasBubble() { return !bubble().getString().isEmpty(); }

    public void showBubble(Component line, int ticks) {
        entityData.set(DATA_BUBBLE, line);
        bubbleTicks = ticks;
    }

    public int sayCooldown() { return sayCooldown; }
    public void setSayCooldown(int v) { sayCooldown = v; }
    public long greetedUntil() { return greetedUntil; }
    public void setGreetedUntil(long v) { greetedUntil = v; }
    public long festivalSeen() { return festivalSeen; }
    public void setFestivalSeen(long v) { festivalSeen = v; }
    public int lastBubbleIndex(String situation) { return lastBubble.getOrDefault(situation, -1); }
    public void rememberBubble(String situation, int idx) { lastBubble.put(situation, idx); }

    /** Says a line right now (ignores the cooldown, not the nearby-bubble limit unless {@code cap} is 99). */
    public boolean say(String situation) {
        return Social.say(this, situation, 4);
    }

    public Map<String, Integer> affinityMap() { return affinity; }

    public int affinity(UUID other) { return affinity.getOrDefault(other.toString(), 0); }

    public void addAffinity(UUID other, int delta) {
        affinity.merge(other.toString(), delta, (a, b) -> Mth.clamp(a + b, -100, 100));
        affinity.computeIfPresent(other.toString(), (k, v) -> Mth.clamp(v, -100, 100));
    }

    /** Closest friend (affinity &gt; 0) or rival (affinity &lt; 0); null when there is none (or they left). */
    public @Nullable Map.Entry<UUID, Integer> bestRelation(boolean friend) {
        Settlement s = settlement();
        Map.Entry<UUID, Integer> best = null;
        for (Map.Entry<String, Integer> e : affinity.entrySet()) {
            int v = e.getValue();
            if (friend ? v <= 0 : v >= 0) continue;
            UUID id;
            try { id = UUID.fromString(e.getKey()); } catch (IllegalArgumentException ex) { continue; }
            if (s != null && s.resident(id) == null) continue;
            if (best == null || (friend ? v > best.getValue() : v < best.getValue())) best = Map.entry(id, v);
        }
        return best;
    }

    public boolean canChat(long day) {
        return hasArrived() && !isLeaving() && !isSleeping() && chatPartner == null && lastChatDay != day
                && getTarget() == null && !isCombatant() && brain.currentActivity().matches("WorkActivity|IdleActivity|none");
    }

    public void beginChat(UUID partner, boolean initiator, long deadline, long day) {
        chatPartner = partner;
        chatInitiator = initiator;
        chatDeadline = deadline;
        lastChatDay = day;
        brain.pause();
    }

    public void endChat() { chatPartner = null; }
    public @Nullable UUID chatPartnerId() { return chatPartner; }
    public boolean isChatInitiator() { return chatInitiator; }
    public long chatDeadline() { return chatDeadline; }

    public @Nullable SettlerEntity partner() {
        if (chatPartner == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(chatPartner) instanceof SettlerEntity p && p.isAlive() && getUUID().equals(p.chatPartner) ? p : null;
    }

    public Identifier professionId() {
        Identifier id = Identifier.tryParse(entityData.get(DATA_PROFESSION));
        return id == null ? Professions.UNEMPLOYED_ID : id;
    }

    public Profession profession() {
        return Professions.get(professionId());
    }

    public void setProfession(Profession p) {
        if (p.id().equals(professionId())) return;
        brain.onProfessionChanged();
        entityData.set(DATA_PROFESSION, p.id().toString());
        refreshName();
        syncRecord();
    }

    public boolean isCombatant() {
        return profession().isCombat();
    }

    /** Combatants only fight inside the settlement or close to themselves. */
    public boolean canDefend(LivingEntity target) {
        Settlement s = settlement();
        if (s != null && s.contains(level().dimension(), target.blockPosition())) return true;
        if (brain.job().defends(this, target)) return true;
        return distanceToSqr(target) < 14 * 14;
    }

    public Component status() { return entityData.get(DATA_STATUS); }

    public void setStatus(Component status) {
        if (!status.equals(entityData.get(DATA_STATUS))) entityData.set(DATA_STATUS, status);
    }

    public void setStatus(String key, Object... args) {
        setStatus(Component.translatable("status.hearthholm." + key, args));
    }

    @Override
    public Component getName() {
        if (level().isClientSide()) {
            int n = entityData.get(DATA_NAME);
            return Component.translatable("settler.hearthholm.name." + ((n >> 16 & 1) == 1 ? "f" : "m") + "." + (n & 0xFFFF));
        }
        return profile.name();
    }

    public void refreshName() {
        Profession p = profession();
        setCustomName(Component.empty().append(profile.fullName())
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(p.label().copy().withColor(p.color())));
        setCustomNameVisible(false);
    }

    // ---- settlement -----------------------------------------------------------------------

    public @Nullable Settlement settlement() {
        if (settlementId == null || !(level() instanceof ServerLevel sl)) return null;
        return SettlementManager.get(sl.getServer()).byId(settlementId);
    }

    public @Nullable ResidentRecord record() {
        Settlement s = settlement();
        return s == null ? null : s.resident(getUUID());
    }

    public @Nullable BlockPos bed() {
        ResidentRecord r = record();
        return r == null ? null : r.bed;
    }

    public void syncRecord() {
        ResidentRecord r = record();
        if (r == null) return;
        r.profession = professionId();
        r.mood = Math.round(mood);
        r.food = Math.round(food);
        r.status = status();
        r.lastPos = blockPosition();
        r.level = level(professionId());
    }

    // ---- skills ---------------------------------------------------------------------------

    public int xp(Identifier profession) {
        return xp.getOrDefault(profession.toString(), 0);
    }

    public int level(Identifier profession) {
        int v = xp(profession), lvl = 1;
        for (int i = 1; i < LEVEL_XP.length; i++) if (v >= LEVEL_XP[i]) lvl = i + 1;
        return lvl;
    }

    public void gainXp(int amount) {
        Identifier p = professionId();
        int before = level(p);
        xp.merge(p.toString(), amount, Integer::sum);
        int after = level(p);
        if (after > before && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 2, getZ(), 12, 0.4, 0.4, 0.4, 0.1);
            sl.playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6f, 1.3f);
            addThought("proud", 6, 24000);
            Social.say(this, "levelup", 4);
            SettlementService.notifyOwner(this, Component.translatable("message.hearthholm.level_up", getName(), profession().label(), after));
        }
    }

    /** Overall work speed multiplier from aptitude, level, mood, traits and hunger. */
    public float workSpeed() {
        Profession p = profession();
        float apt = 0.6f + p.aptitude(profile) * 0.08f;
        float lvl = 1f + 0.1f * (level(p.id()) - 1);
        float moodF = 1f + Mth.clamp(mood, -100, 100) / 200f;
        float trait = 1f;
        for (Trait t : profile.traitList()) trait *= t.workSpeed();
        float hunger = food <= 0 ? 0.6f : 1f;
        return Math.max(0.2f, apt * lvl * moodF * trait * hunger);
    }

    public float moveFactor() {
        float f = 1f;
        for (Trait t : profile.traitList()) f *= t.moveSpeed();
        return f;
    }

    public int luck() {
        int l = level(professionId()) / 2;
        for (Trait t : profile.traitList()) l += t.luck();
        return l;
    }

    public boolean hasTrait(String path) {
        return profile.hasTrait(Hearthholm.id(path));
    }

    public int breakTicks(BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(level(), pos);
        ItemStack tool = getMainHandItem();
        float speed = tool.isEmpty() ? 1f : Math.max(1f, tool.getDestroySpeed(state));
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) speed /= 4f;
        return Mth.clamp(Mth.ceil(hardness * 30f / (speed * workSpeed())), 4, 600);
    }

    // ---- inventory ------------------------------------------------------------------------

    public void store(List<ItemStack> stacks) {
        for (ItemStack s : stacks) store(s);
    }

    public void store(ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack rest = inventory.addItem(stack.copy());
        if (!rest.isEmpty() && level() instanceof ServerLevel sl) spawnAtLocation(sl, rest);
    }

    public int count(Predicate<ItemStack> filter) {
        int n = 0;
        for (ItemStack s : inventory.getItems()) if (!s.isEmpty() && filter.test(s)) n += s.getCount();
        return n;
    }

    public boolean has(Item item) {
        return count(s -> s.is(item)) > 0;
    }

    public boolean takeItem(Item item, int amount) {
        if (count(s -> s.is(item)) < amount) return false;
        inventory.removeItemType(item, amount);
        return true;
    }

    public ItemStack takeFirst(Predicate<ItemStack> filter, int max) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && filter.test(s)) {
                ItemStack out = s.split(max);
                inventory.setChanged();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    public int freeSlots() {
        int n = 0;
        for (ItemStack s : inventory.getItems()) if (s.isEmpty()) n++;
        return n;
    }

    /** Puts a fitting tool from a container into the main hand (old one goes into the container). */
    public boolean equipToolFrom(net.minecraft.world.Container c) {
        Profession p = profession();
        if (p.isTool(getMainHandItem())) return true;
        ItemStack tool = Storage.take(List.of(c), p::isTool, 1);
        if (tool.isEmpty()) return false;
        ItemStack old = getMainHandItem();
        setItemSlot(EquipmentSlot.MAINHAND, tool);
        if (!old.isEmpty()) {
            ItemStack rest = Storage.insert(c, old);
            if (!rest.isEmpty()) store(rest);
        }
        return true;
    }

    public void swingArm(InteractionHand hand) {
        swing(hand, SwingAnimation.DEFAULT);
    }

    // ---- needs ----------------------------------------------------------------------------

    public long dayTime() {
        return Math.floorMod(level().getOverworldClockTime(), 24000L);
    }

    public boolean isSleepTime() {
        long t = dayTime();
        if (profession().nightShift()) return t >= 3000 && t < 9000;
        long start = hasTrait("night_owl") ? 14500 : 12600;
        return t >= start && t < 23400;
    }

    public boolean isWorkTime() {
        if (isSleepTime()) return false;
        long t = dayTime();
        return profession().nightShift() || t < 11800 || t >= 23400;
    }

    public boolean isLunchTime() {
        long t = dayTime();
        return t >= 5800 && t < 6800;
    }

    public void eat(ItemStack stack) {
        int n = Storage.nutrition(stack);
        if (n <= 0) return;
        food = Math.min(MAX_FOOD, food + n);
        if (io.github.operagtx.hearthholm.crafting.Workshop.MEALS.contains(stack.getItem())) {
            addThought("tasty_meal", 8, 24000);
            Social.say(this, "tasty", 4);
        }
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.6f, 1f);
            sl.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, stack.getItem()),
                    getX(), getEyeY() - 0.2, getZ(), 6, 0.15, 0.1, 0.15, 0.05);
        }
        swingArm(InteractionHand.MAIN_HAND);
    }

    private void tickNeeds() {
        float rate = 1f / 1800f;
        for (Trait t : profile.traitList()) rate *= t.hungerRate();
        if (isSleeping()) rate *= 0.5f;
        food = Math.max(0f, food - rate);
        if (tickCount % 600 == 0) updateMood(true);
        else if (moodDirty && tickCount % 20 == 0) updateMood(false);
        if (bubbleTicks > 0 && --bubbleTicks == 0) entityData.set(DATA_BUBBLE, Component.empty());
        if (tickCount % 40 == 0) Social.ambient(this);
        if ((tickCount + getId()) % 200 == 0) Social.relations(this);
    }

    private void updateMood(boolean regular) {
        moodDirty = false;
        long now = level().getGameTime();
        thoughts.removeIf(t -> t.expiry() <= now);
        List<MoodEntry> entries = new ArrayList<>();
        if (food >= 14) entries.add(MoodEntry.of("fed", 10));
        else if (food <= 0) entries.add(MoodEntry.of("starving", -40));
        else if (food < 6) entries.add(MoodEntry.of("hungry", -20));
        BlockPos bed = bed();
        Settlement s = settlement();
        if (bed != null) {
            entries.add(MoodEntry.of("own_bed", 10));
            BuildingRecord home = s == null ? null : s.buildingWithBed(bed);
            if (home != null && home.comfort() > 0) entries.add(MoodEntry.of("cozy_home", home.comfort() * 2));
            if (home != null && home.comfort() >= 8) putThought("pretty_home", 6, 24000);
        } else {
            entries.add(MoodEntry.of("no_bed", -25));
        }
        entries.add(sleptLastNight ? MoodEntry.of("rested", 5) : MoodEntry.of("tired", -15));
        Profession p = profession();
        if (p == Professions.UNEMPLOYED) entries.add(MoodEntry.of("idle", -5));
        else if (p.aptitude(profile) >= 7) entries.add(MoodEntry.of("loves_job", 10));
        else if (p.aptitude(profile) <= 3) entries.add(MoodEntry.of("hates_job", -8));
        if (level().getGameTime() - lastHurt < 2400) entries.add(MoodEntry.of("hurt", -10));
        if (level().isRainingAt(blockPosition())) putThought("wet", -3, 1800);
        if (profile.legendary()) entries.add(MoodEntry.thought("legend", 5));
        if (s != null && s.trophyInHall) entries.add(MoodEntry.thought("trophy", 5));
        for (Thought t : thoughts) entries.add(MoodEntry.thought(t.id(), t.value()));
        if (s != null && s.residents.stream().anyMatch(r -> Professions.get(r.profession).isCombat()))
            entries.add(MoodEntry.of("protected", 5));
        for (Trait t : profile.traitList()) if (t.mood() != 0) entries.add(new MoodEntry(t.label(), t.mood()));
        moodEntries = List.copyOf(entries);

        int target = 0;
        for (MoodEntry e : entries) target += e.value();
        target = Mth.clamp(target, -100, 100);
        if (!regular) return;
        mood += Mth.clamp(target - mood, -8f, 8f);

        if (mood <= -40) {
            unhappyTicks += 600;
            if (!warnedUnhappy && unhappyTicks >= 24000) {
                warnedUnhappy = true;
                SettlementService.notifyOwner(this, Component.translatable("message.hearthholm.unhappy", getName()).withStyle(ChatFormatting.GOLD));
            }
            if (unhappyTicks >= 72000) leave(Component.translatable("message.hearthholm.left_unhappy", getName()));
        } else {
            unhappyTicks = Math.max(0, unhappyTicks - 1200);
            if (unhappyTicks == 0) warnedUnhappy = false;
        }
    }

    /** Leaves the settlement: walks away and disappears. */
    public void leave(Component message) {
        if (leaving) return;
        leaving = true;
        brain.onProfessionChanged();
        Settlement s = settlement();
        if (s != null) {
            SettlementService.notifyOwner(this, message.copy().withStyle(ChatFormatting.RED));
            s.removeResident(getUUID());
            SettlementService.markDirty(this);
        }
        setStatus("leaving");
    }

    // ---- movement helpers -----------------------------------------------------------------

    public boolean canReach(BlockPos pos) {
        return getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= 4.6 * 4.6
                && Math.abs(pos.getY() + 0.5 - getY()) <= 3.2;
    }

    /** Short "unstuck" hop to a free standing spot near the target. */
    public boolean hopNear(BlockPos target) {
        Level level = level();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(target.offset(-2, -2, -2), target.offset(2, 2, 2))) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) continue;
            if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) continue;
            if (!level.getFluidState(p).isEmpty()) continue;
            BlockPos below = p.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) continue;
            double d = p.distSqr(target);
            if (d < bestDist) { bestDist = d; best = p.immutable(); }
        }
        if (best == null) return false;
        if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 6, 0.2, 0.3, 0.2, 0.02);
        getNavigation().stop();
        teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
        return true;
    }

    // ---- vanilla hooks --------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        tickNeeds();
        if (tickCount % 100 == 0) {
            if (settlementId != null && settlement() == null && !leaving) leave(Component.translatable("message.hearthholm.left_unhappy", getName()));
            syncRecord();
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            lastHurt = level.getGameTime();
            if (source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Player)) {
                SettlementService.callGuards(this, attacker);
            }
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl && !isRemoved()) {
            Settlement s = settlement();
            if (s != null) {
                io.github.operagtx.hearthholm.threat.Threats.onSettlerDied(sl, s);
                SettlementService.notifyOwner(this, Component.translatable("message.hearthholm.died", getName(),
                        source.getLocalizedDeathMessage(this)).withStyle(ChatFormatting.RED));
                s.removeResident(getUUID());
                SettlementService.markDirty(this);
            }
            brain.onProfessionChanged();
            for (ItemStack st : inventory.removeAllItems()) spawnAtLocation(sl, st);
        }
        super.die(source);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            SettlementService.interactSettler(sp, this, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("Profile", SettlerProfile.CODEC, profile);
        out.storeNullable("Settlement", UUIDUtil.CODEC, settlementId);
        out.putString("Profession", professionId().toString());
        out.putFloat("Food", food);
        out.putFloat("Mood", mood);
        out.putInt("Unhappy", unhappyTicks);
        out.putBoolean("Slept", sleptLastNight);
        out.putBoolean("Leaving", leaving);
        out.putBoolean("Arrived", arrived);
        out.store("Thoughts", Thought.CODEC.listOf(), List.copyOf(thoughts));
        out.store("Affinity", AFFINITY_CODEC, Map.copyOf(affinity));
        out.putLong("LastChatDay", lastChatDay);
        out.store("Xp", XP_CODEC, Map.copyOf(xp));
        inventory.storeAsItemList(out.list("Inventory", ItemStack.CODEC));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        profile = in.read("Profile", SettlerProfile.CODEC).orElse(SettlerProfile.DEFAULT);
        settlementId = in.read("Settlement", UUIDUtil.CODEC).orElse(null);
        entityData.set(DATA_PROFESSION, in.getStringOr("Profession", Professions.UNEMPLOYED_ID.toString()));
        entityData.set(DATA_LOOK, profile.appearance());
        entityData.set(DATA_NAME, profile.nameIndex() | (profile.female() ? 1 << 16 : 0));
        food = in.getFloatOr("Food", MAX_FOOD);
        mood = in.getFloatOr("Mood", 10f);
        unhappyTicks = in.getIntOr("Unhappy", 0);
        sleptLastNight = in.getBooleanOr("Slept", true);
        leaving = in.getBooleanOr("Leaving", false);
        arrived = in.getBooleanOr("Arrived", true);
        thoughts.clear();
        in.read("Thoughts", Thought.CODEC.listOf()).ifPresent(thoughts::addAll);
        long legacyTasty = in.getLongOr("TastyUntil", -1L);
        if (legacyTasty > 0 && thoughts.stream().noneMatch(t -> t.id().equals("tasty_meal"))) thoughts.add(new Thought("tasty_meal", 8, legacyTasty));
        affinity.clear();
        in.read("Affinity", AFFINITY_CODEC).ifPresent(affinity::putAll);
        lastChatDay = in.getLongOr("LastChatDay", -1L);
        xp.clear();
        in.read("Xp", XP_CODEC).ifPresent(xp::putAll);
        inventory.fromItemList(in.listOrEmpty("Inventory", ItemStack.CODEC));
        refreshName();
    }
}
