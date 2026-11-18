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
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.item.Items;
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
    public static final int[] LEVEL_XP = io.github.operagtx.hearthholm.core.Balance.LEVEL_XP;   // wave I1: one curve for all professions, tunable
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
    /** The player whose war-horn party this settler belongs to (saved); null at home. */
    private @Nullable UUID partyOwner;
    private long nextTaunt;
    private boolean retreating;
    /** Wave I2: duty shift of a fighter (0 always, 1 day, 2 night), saved; 0 follows the shift of the post. */
    public static final int SHIFT_ALWAYS = 0, SHIFT_DAY = 1, SHIFT_NIGHT = 2;
    private int shift;
    private int postShift;
    private long postShiftAt = -1000;

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
                .add(Attributes.ATTACK_SPEED, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(1, new SettlerRetreatGoal(this));
        goalSelector.addGoal(2, new SettlerFleeGoal(this));
        goalSelector.addGoal(2, new SettlerRangedGoal(this));
        goalSelector.addGoal(2, new SettlerMeleeGoal(this));
        goalSelector.addGoal(5, new SettlerBrainGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(0, new PartyTargetGoal(this));
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
        refreshCombatStats();
        setHealth(getMaxHealth());
        refreshName();
    }

    public SettlerProfile profile() { return profile; }
    public int lookBits() { return entityData.get(DATA_LOOK); }
    public @Nullable UUID settlementId() { return settlementId; }
    public SimpleContainer inventory() { return inventory; }
    public SettlerBrain brain() { return brain; }
    public float food() { return food; }
    public void setFood(float v) { food = Mth.clamp(v, 0f, MAX_FOOD); }
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
        refreshCombatStats();
        refreshName();
        syncRecord();
    }

    public boolean isCombatant() {
        return profession().isCombat();
    }

    // ---- wave D: party, combat strength, healing, shield, taunt ----------------------------------

    public @Nullable UUID partyOwner() { return partyOwner; }
    public boolean inParty() { return partyOwner != null; }

    public void joinParty(UUID owner) {
        partyOwner = owner;
        io.github.operagtx.hearthholm.party.Party.markOwner(owner);
        io.github.operagtx.hearthholm.party.Party.remember(this);
        brain.pause();
        getNavigation().stop();
        if (isSleeping()) stopSleeping();
        setTarget(null);
        setStatus("party_with");
        syncRecord();
    }

    public void leaveParty() {
        if (partyOwner == null) return;
        partyOwner = null;
        brain.pause();
        getNavigation().stop();
        setTarget(null);
    }

    public boolean isRetreating() { return retreating; }
    public void setRetreating(boolean v) { retreating = v; }

    public boolean isKnight() { return professionId().equals(Professions.KNIGHT_ID); }

    /** A settler fighting with a bow and with arrows left shoots instead of swinging. */
    public boolean isRanged() {
        return professionId().equals(Professions.ARCHER.id()) && getMainHandItem().is(Items.BOW) && has(Items.ARROW);
    }

    /** An archer with a bow in hand and no arrows left. */
    public boolean needsArrows() {
        return professionId().equals(Professions.ARCHER.id()) && getMainHandItem().is(Items.BOW) && !has(Items.ARROW);
    }

    /**
     * Wave I2: combat settlers move at the speed of a player: walking 4.3 b/s, chasing a target (or retreating) 5.6 b/s,
     * whatever speed factor the task asked for (traits do not change it). Mobs accelerate with speed squared, hence the square roots.
     */
    @Override
    public void setSpeed(float v) {
        if (v > 0.0001f && isCombatant()) {
            LivingEntity t = getTarget();
            boolean sprint = retreating || (t != null && t.isAlive());
            float walk = (float) Math.sqrt((double) io.github.operagtx.hearthholm.core.Balance.COMBAT_WALK_BPS_X10 / io.github.operagtx.hearthholm.core.Balance.COMBAT_SPRINT_BPS_X10);
            v = (float) getAttributeValue(Attributes.MOVEMENT_SPEED) * (sprint ? 1f : walk);
        }
        super.setSpeed(v);
    }

    /** Test hooks: a goal with the highest priority (the game test drives a settler along a line with it). */
    public void addTestGoal(net.minecraft.world.entity.ai.goal.Goal g) { goalSelector.addGoal(0, g); }
    public void removeTestGoal(net.minecraft.world.entity.ai.goal.Goal g) { goalSelector.removeGoal(g); }

    // ---- wave I2: shifts ---------------------------------------------------------------------

    public int shift() { return shift; }

    public void setShift(int v) {
        shift = Mth.clamp(v, SHIFT_ALWAYS, SHIFT_NIGHT);
        syncRecord();
    }

    /** The shift in force: the own setting, else the shift of the nearest post of the profession (the post screen), else always. */
    public int effectiveShift() {
        if (!isCombatant()) return SHIFT_ALWAYS;
        if (shift != SHIFT_ALWAYS) return shift;
        long now = level().getGameTime();
        if (now - postShiftAt > 100 || now < postShiftAt) {
            postShiftAt = now;
            postShift = SHIFT_ALWAYS;
            Settlement st = settlement();
            Identifier site = profession().siteType();
            if (st != null && site != null) {
                BlockPos from = bed() != null ? bed() : blockPosition();
                double best = Double.MAX_VALUE;
                for (Settlement.SiteRecord r : st.sitesOf(site)) {
                    double d = r.pos().distSqr(from);
                    if (d < best && level().isLoaded(r.pos()) && level().getBlockEntity(r.pos()) instanceof io.github.operagtx.hearthholm.worksite.WorkSiteBlockEntity w) {
                        best = d;
                        postShift = w.option("shift");
                    }
                }
            }
        }
        return postShift;
    }

    private boolean nightWindow() {
        int m = effectiveShift();
        return m == SHIFT_NIGHT || (m == SHIFT_ALWAYS && profession().nightShift());
    }

    /** Whether this settler is on duty (working hours of the shift, awake and not leaving). */
    public boolean isOnDuty() {
        return isWorkTime() && !isLeaving();
    }

    /** True when the working period ends within {@code ticks}: the time to carry loot home. */
    public boolean shiftEndsWithin(int ticks) {
        return isWorkTime() && !workAt(Math.floorMod(dayTime() + ticks, 24000L));
    }

    /**
     * Health, damage and natural armour of combat settlers grow with level (a guard or warrior starts sturdier
     * than a civilian and gains with every level), knights get a large bonus on top. Gear adds its own attributes.
     */
    public void refreshCombatStats() {
        int lvl = level(professionId());
        float hp = 20 + profile.stat(io.github.operagtx.hearthholm.core.Stat.ENDURANCE) / 2f;
        for (Trait t : profile.traitList()) hp += t.maxHealthBonus();
        double atk = 1 + profile.stat(io.github.operagtx.hearthholm.core.Stat.STRENGTH) * 0.25;
        double armor = 0, toughness = 0;
        if (isCombatant()) {
            hp += 20 + 4 * (lvl - 1);
            // wave I2: a bare hand hits for 1 like a player's; the held weapon adds its own vanilla damage through its attribute modifiers
            atk = 1 + profile.stat(io.github.operagtx.hearthholm.core.Stat.STRENGTH) * io.github.operagtx.hearthholm.core.Balance.COMBAT_DMG_PER_STRENGTH_PCT / 100.0
                    + io.github.operagtx.hearthholm.core.Balance.COMBAT_DMG_PER_LEVEL_PCT / 100.0 * (lvl - 1);
            armor = 2 + 0.5 * (lvl - 1);
            toughness = 2;
            if (isKnight()) { hp += 24; atk += io.github.operagtx.hearthholm.core.Balance.KNIGHT_DMG_BONUS_PCT / 100.0; armor += 3; toughness += 2; }
        }
        var maxHp = Objects.requireNonNull(getAttribute(Attributes.MAX_HEALTH));
        float old = getMaxHealth();
        if (Math.abs(maxHp.getBaseValue() - hp) > 0.01) {
            maxHp.setBaseValue(hp);
            setHealth(Math.min(hp, Math.max(1f, getHealth() * (hp / Math.max(1f, old)))));
        }
        Objects.requireNonNull(getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(atk);
        // wave I2: movement attribute = chasing speed of a sprinting player; walking is derived in setSpeed
        Objects.requireNonNull(getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(isCombatant()
                ? Math.sqrt(io.github.operagtx.hearthholm.core.Balance.COMBAT_SPRINT_BPS_X10 / 10.0 / io.github.operagtx.hearthholm.core.Balance.COMBAT_BPS_PER_ATTR_SQ) : 0.5);
        Objects.requireNonNull(getAttribute(Attributes.ARMOR)).setBaseValue(armor);
        Objects.requireNonNull(getAttribute(Attributes.ARMOR_TOUGHNESS)).setBaseValue(toughness);
    }

    /** Test hook and promotion: sets the experience of a profession. */
    public void setXp(Identifier profession, int value) {
        xp.put(profession.toString(), value);
        refreshCombatStats();
    }

    /** Near a weapon rack of a valid barracks of this settlement. */
    public boolean inBarracks() {
        Settlement st = settlement();
        if (st == null) return false;
        for (BuildingRecord b : st.buildings) {
            if (!b.valid() || !b.is(io.github.operagtx.hearthholm.crafting.Workshop.BARRACKS.role())) continue;
            for (BlockPos p : b.stations()) if (p.distSqr(blockPosition()) <= 64 && Math.abs(p.getY() - getBlockY()) <= 3) return true;
        }
        return false;
    }

    /** The nearest weapon rack of a valid barracks, or null. */
    public @Nullable BlockPos barracksSpot() {
        Settlement st = settlement();
        if (st == null) return null;
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BuildingRecord b : st.buildings) {
            if (!b.valid() || !b.is(io.github.operagtx.hearthholm.crafting.Workshop.BARRACKS.role())) continue;
            for (BlockPos p : b.stations()) {
                double d = p.distSqr(blockPosition());
                if (d < bd) { bd = d; best = p; }
            }
        }
        return best;
    }

    /** Where a badly hurt fighter withdraws to: the nearest barracks rack, else the nearest healer. */
    public @Nullable BlockPos retreatSpot() {
        BlockPos best = barracksSpot();
        if (best != null) return best;
        double bd = Double.MAX_VALUE;
        for (SettlerEntity o : level().getEntitiesOfClass(SettlerEntity.class, getBoundingBox().inflate(64, 16, 64),
                x -> x.isAlive() && x.professionId().equals(Professions.HEALER.id()) && Objects.equals(x.settlementId, settlementId))) {
            double d = o.distanceToSqr(this);
            if (d < bd) { bd = d; best = o.blockPosition(); }
        }
        return best;
    }

    public boolean wantsRetreat() {
        return isCombatant() && !inParty() && getHealth() < getMaxHealth() * 0.3f && retreatSpot() != null;
    }

    /** Once a second: slow natural healing (fast in a barracks), salves, the knight's taunt, "comrades" mood. */
    private void tickMilitary(ServerLevel sl) {
        long now = sl.getGameTime();
        boolean barracks = isCombatant() && inBarracks();
        if (getHealth() < getMaxHealth() && food > 4 && now - lastHurt > 100) heal(barracks ? 1.25f : 0.25f);
        if (barracks && !isSleeping()) {
            addThought("comrades", io.github.operagtx.hearthholm.core.Balance.MOOD_COMRADES, 6000);
            if (getHealth() < getMaxHealth() && getRandom().nextInt(8) == 0)
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.6, getZ(), 1, 0.2, 0.2, 0.2, 0.0);
        }
        if (getHealth() < getMaxHealth() * 0.5f && has(io.github.operagtx.hearthholm.registry.ModItems.HEALING_SALVE)
                && !io.github.operagtx.hearthholm.entity.ai.jobs.HealerJob.healerNear(this, 24)) {
            takeItem(io.github.operagtx.hearthholm.registry.ModItems.HEALING_SALVE, 1);
            heal(io.github.operagtx.hearthholm.item.HealingSalveItem.HEAL);
            sl.sendParticles(ParticleTypes.HEART, getX(), getY() + 1.9, getZ(), 4, 0.3, 0.2, 0.3, 0.02);
            sl.playSound(null, blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 0.7f, 1.2f);
            Settlement st = settlement();
            if (st != null) st.stats.merge("salves_used", 1, Integer::sum);
        }
        if (isKnight() && now >= nextTaunt && getHealth() > getMaxHealth() * 0.35f) {
            int n = 0;
            for (Mob m : sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(10, 4, 10),
                    x -> x instanceof net.minecraft.world.entity.monster.Enemy && x.isAlive() && x.getTarget() != null && x.getTarget() != this)) {
                m.setTarget(this);
                n++;
            }
            if (n > 0) {
                nextTaunt = now + 160;
                sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.2, getZ(), 6, 0.4, 0.3, 0.4, 0.0);
                sl.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 0.5f, 1.7f);
                Social.say(this, "taunt", 99);
                Settlement st = settlement();
                if (st != null) st.stats.merge("taunts", 1, Integer::sum);
            }
        }
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
        r.hp = Math.round(getHealth());
        r.maxHp = Math.round(getMaxHealth());
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

    private int xpFraction, workTicks;

    /** Action XP (a block mined, a crop picked): only a small share counts, the main source is {@link #workTick()}. */
    public void gainXp(int amount) {
        xpFraction += amount * io.github.operagtx.hearthholm.core.Balance.XP_ACTION_PERCENT;
        int whole = xpFraction / 100;
        xpFraction %= 100;
        if (whole > 0) addXp(whole);
    }

    /** Called every tick the settler is busy with a job task: one XP point per {@code XP_WORK_TICKS} ticks. */
    public void workTick() {
        if (++workTicks >= io.github.operagtx.hearthholm.core.Balance.XP_WORK_TICKS) {
            workTicks = 0;
            addXp(1);
        }
    }

    private void addXp(int amount) {
        Identifier p = professionId();
        int before = level(p);
        xp.merge(p.toString(), amount, Integer::sum);
        int after = level(p);
        if (after > before) refreshCombatStats();
        if (after > before && level() instanceof ServerLevel sl) {
            io.github.operagtx.hearthholm.town.Fx.levelUp(sl, blockPosition(), false);
            addThought("proud", io.github.operagtx.hearthholm.core.Balance.MOOD_PROUD, 24000);
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

    /**
     * Wave I1: the vanilla time a player without enchantments needs with the held tool (hardness x 30 / speed, x 100 / speed when the
     * tool is wrong for a block that needs one), stretched by {@code BREAK_PACE_PERCENT} and shortened a little by level, traits and hunger.
     */
    public int breakTicks(BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(level(), pos);
        ItemStack tool = getMainHandItem();
        float speed = tool.isEmpty() ? 1f : Math.max(1f, tool.getDestroySpeed(state));
        boolean harvest = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        float vanilla = hardness * (harvest ? 30f : 100f) / speed;
        float skill = 1f + io.github.operagtx.hearthholm.core.Balance.BREAK_SKILL_PERCENT_PER_LEVEL / 100f * (level(professionId()) - 1);
        for (Trait t : profile.traitList()) skill *= t.workSpeed();
        if (food <= 0) skill *= 0.6f;
        float ticks = vanilla * io.github.operagtx.hearthholm.core.Balance.BREAK_PACE_PERCENT / 100f / Math.max(0.3f, skill);
        return Mth.clamp(Mth.ceil(ticks), 4, 1200);
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
        int best = -1;
        long bestScore = -1;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack st = c.getItem(i);
            if (!p.isTool(st)) continue;
            long score = (long) st.getMaxDamage() * 10000L + (st.getMaxDamage() - st.getDamageValue());   // best material first, then least worn
            if (score > bestScore) { bestScore = score; best = i; }
        }
        if (best < 0) return false;
        ItemStack tool = c.removeItem(best, 1);
        c.setChanged();
        ItemStack old = getMainHandItem();
        setItemSlot(EquipmentSlot.MAINHAND, tool);
        if (!old.isEmpty()) {
            ItemStack rest = Storage.insert(c, old);
            if (!rest.isEmpty()) store(rest);
        }
        return true;
    }

    /** Moves a fitting tool from the pockets into the main hand; false when there is none. */
    public boolean equipToolFromPockets() {
        Profession p = profession();
        if (p.isTool(getMainHandItem())) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack st = inventory.getItem(i);
            if (!p.isTool(st)) continue;
            ItemStack old = getMainHandItem();
            setItemSlot(EquipmentSlot.MAINHAND, st.copy());
            inventory.setItem(i, old);
            return true;
        }
        return false;
    }

    public void swingArm(InteractionHand hand) {
        swing(hand, SwingAnimation.DEFAULT);
    }

    // ---- needs ----------------------------------------------------------------------------

    public long dayTime() {
        return Math.floorMod(level().getOverworldClockTime(), 24000L);
    }

    public boolean isSleepTime() {
        return sleepAt(dayTime());
    }

    private boolean sleepAt(long t) {
        if (nightWindow()) return t >= 3000 && t < 9000;
        long start = hasTrait("night_owl") ? 14500 : 12600;
        return t >= start && t < 23400;
    }

    private boolean workAt(long t) {
        if (sleepAt(t)) return false;
        return switch (effectiveShift()) {
            case SHIFT_NIGHT -> t >= 11800 || t < 3000;
            case SHIFT_DAY -> t < 11800 || t >= 23400;
            default -> profession().nightShift() || t < 11800 || t >= 23400;
        };
    }

    public boolean isWorkTime() {
        return workAt(dayTime());
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
            addThought("tasty_meal", io.github.operagtx.hearthholm.core.Balance.MOOD_TASTY_MEAL, 24000);
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
        if (tickCount % 20 == 0) tickMilitary(level);
        if (tickCount % 100 == 0) {
            io.github.operagtx.hearthholm.town.Decor.tick(this);
            if (settlementId != null && settlement() == null && !leaving) leave(Component.translatable("message.hearthholm.left_unhappy", getName()));
            syncRecord();
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof SettlerEntity) return false;   // no friendly fire (arrows, stray blows): it would start a brawl
        if (isKnight() && !source.is(DamageTypeTags.BYPASSES_SHIELD) && source.getSourcePosition() != null) {
            Vec3 look = getViewVector(1f).multiply(1, 0, 1).normalize();
            Vec3 to = source.getSourcePosition().subtract(position()).multiply(1, 0, 1).normalize();
            if (to.dot(look) > 0.2) {
                amount *= 0.35f;
                level.playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.NEUTRAL, 0.8f, 1f);
                level.sendParticles(ParticleTypes.CRIT, getX() + to.x * 0.6, getY() + 1.2, getZ() + to.z * 0.6, 4, 0.15, 0.2, 0.15, 0.1);
            }
        }
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
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && isCombatant() && target instanceof LivingEntity le) {
            gainXp(le.isDeadOrDying() ? 6 : 1);
            ItemStack weapon = getMainHandItem();
            if (weapon.isDamageableItem() && !weapon.is(Items.BOW)) weapon.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);   // wave I2: weapons wear out
        }
        return hit;
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
        out.storeNullable("PartyOwner", UUIDUtil.CODEC, partyOwner);
        out.putLong("LastChatDay", lastChatDay);
        out.store("Xp", XP_CODEC, Map.copyOf(xp));
        out.putInt("Shift", shift);
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
        partyOwner = in.read("PartyOwner", UUIDUtil.CODEC).orElse(null);
        if (partyOwner != null) io.github.operagtx.hearthholm.party.Party.markOwner(partyOwner);
        lastChatDay = in.getLongOr("LastChatDay", -1L);
        xp.clear();
        in.read("Xp", XP_CODEC).ifPresent(xp::putAll);
        shift = in.getIntOr("Shift", SHIFT_ALWAYS);
        inventory.fromItemList(in.listOrEmpty("Inventory", ItemStack.CODEC));
        refreshCombatStats();
        refreshName();
    }
}
