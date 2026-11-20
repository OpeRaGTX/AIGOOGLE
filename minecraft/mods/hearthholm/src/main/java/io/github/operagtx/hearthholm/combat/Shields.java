package io.github.operagtx.hearthholm.combat;

import io.github.operagtx.hearthholm.core.Balance;
import io.github.operagtx.hearthholm.entity.SettlerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Wave L1: blocking with a shield in the off hand, for every combat settler (before wave L1 only knights blocked, and without an item). A blow from the
 * front (within about 78 degrees of where the settler looks) that does not bypass shields costs {@code SHIELD_BLOCK_PERCENT} of its damage and wears the
 * shield. Pure of the entity's other behaviour: {@code SettlerEntity.hurtServer} calls {@link #reduce} once.
 */
public final class Shields {
    private Shields() {}

    /** Whether this settler fights with a shield (a combat profession with a shield item in the off hand). */
    public static boolean holdsShield(SettlerEntity s) {
        return s.isCombatant() && s.getOffhandItem().is(Items.SHIELD);
    }

    /** Whether a blow from {@code source} would be caught: shield held, the blow does not bypass shields and comes from the front. */
    public static boolean blocks(SettlerEntity s, DamageSource source) {
        if (!holdsShield(s) || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.getSourcePosition() == null) return false;
        Vec3 look = s.getViewVector(1f).multiply(1, 0, 1).normalize();
        Vec3 to = source.getSourcePosition().subtract(s.position()).multiply(1, 0, 1).normalize();
        return to.dot(look) > 0.2;
    }

    /** The damage left after the shield (unchanged when it does not block); plays the sound, shows sparks and wears the shield. */
    public static float reduce(SettlerEntity s, ServerLevel level, DamageSource source, float amount) {
        if (!blocks(s, source)) return amount;
        Vec3 to = source.getSourcePosition().subtract(s.position()).multiply(1, 0, 1).normalize();
        level.playSound(null, s.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.NEUTRAL, 0.8f, 1f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, s.getX() + to.x * 0.6, s.getY() + 1.2, s.getZ() + to.z * 0.6, 4, 0.15, 0.2, 0.15, 0.1);
        ItemStack shield = s.getOffhandItem();
        if (shield.isDamageableItem()) shield.hurtAndBreak(Math.max(1, Math.round(amount)), s, EquipmentSlot.OFFHAND);
        return amount * Balance.SHIELD_BLOCK_PERCENT / 100f;
    }
}
