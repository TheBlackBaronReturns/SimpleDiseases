package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.network.NetworkHandler;
import com.theblackbaron.simplediseases.particle.DiseaseParticleEmitter;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.server.level.ServerPlayer;

public final class InjuryManager {
    // Armor-tiered flesh-wound chance. Tiers: 0 = unarmored, 1 = light (1-6), 2 = medium (7-10), 3 = heavy (11+).
    private static final double[] FLESH_WOUND_CHANCE   = {0.10, 0.07, 0.04, 0.02};
    private static final float  MIN_FLESH_WOUND_DAMAGE = 4.0F;
    private static final int    HEAVY_ARMOR_THRESHOLD    = 7;
    private static final double HEAVY_WEAPON_BONUS_BASE  = 0.05;
    private static final double HEAVY_WEAPON_BONUS_EXTRA = 0.03;

    private static final int    WOUND_EFFECT_DURATION_TICKS    = MobEffectInstance.INFINITE_DURATION;

    // HUD splatter cadence by wound severity [mild, moderate, severe]
    private static final int[] SPLATTER_INTERVAL_TICKS = {600, 300, 120};
    private static final int[] SPLATTER_COUNT          = {2, 3, 3};
    private static final int   SPLATTER_ON_WOUND_COUNT = 4;

    // Per-second (per-20-tick) infection seeding chance by flesh-wound severity phase [sev0, sev1, sev2].
    private static final double[] INFECTION_CHANCE_BOOSTED   = {0.000342, 0.000351, 0.000496};
    private static final double[] INFECTION_CHANCE_NORMAL    = {0.001487, 0.001383, 0.001113};
    private static final double[] INFECTION_CHANCE_DEFICIENT = {0.002868, 0.001113, 0.002121};
    private static final double INFECTION_SEED_MIN = 0.0;
    private static final double INFECTION_SEED_MAX = 0.5;

    public void tick(ServerPlayer player, PlayerDiseaseState state) {
        PlayerInjuryState injury = state.injury();
        long gameTime = player.level().getGameTime();

        if (!injury.hasActiveInjury()) {
            clearWoundEffects(player);
            return;
        }

        injury.tick();

        int woundSeverity = injury.fleshWoundSeverity();

        if (woundSeverity >= 0 && gameTime % 20 == 0
                && state.progress(DiseaseRegistry.CELLULITIS_STAPH) <= 0.0
                && !state.inRecovery(DiseaseRegistry.CELLULITIS_STAPH)) {
            double[] rates = infectionRates(player);
            if (player.getRandom().nextDouble() < rates[woundSeverity]) {
                double seed = INFECTION_SEED_MIN + player.getRandom().nextDouble() * (INFECTION_SEED_MAX - INFECTION_SEED_MIN);
                state.addProgress(DiseaseRegistry.CELLULITIS_STAPH, Math.max(0.001, seed));
            }
        }

        if (woundSeverity >= 0 && !state.inRecovery(DiseaseRegistry.CELLULITIS_STAPH)) {
            setWoundEffect(player, DiseaseEffects.FLESH_WOUND.get(), woundSeverity);
        } else {
            player.removeEffect(DiseaseEffects.FLESH_WOUND.get());
        }

        if (woundSeverity >= 0) {
            // World blood trail, visible to nearby players (server sendParticles)
            DiseaseParticleEmitter.emitBleeding(player, injury, woundSeverity, gameTime);
            // Local HUD splatter, cadence scaled by severity
            if (gameTime % SPLATTER_INTERVAL_TICKS[woundSeverity] == 0) {
                NetworkHandler.sendBleedingSplatter(player, SPLATTER_COUNT[woundSeverity]);
            }
        }
    }

    public void onPlayerDamaged(ServerPlayer player, PlayerDiseaseState state, DamageSource source, float finalDamage) {
        if (finalDamage <= 0.0F || player.isCreative() || player.isSpectator()) return;

        tryFleshWound(player, state.injury(), source, finalDamage);
    }

    private static boolean tryFleshWound(ServerPlayer player, PlayerInjuryState injury,
                                         DamageSource source, float finalDamage) {
        if (finalDamage < MIN_FLESH_WOUND_DAMAGE || !isLaceratingDamage(source)) return false;

        RandomSource random = player.getRandom();
        int armor = player.getArmorValue();
        int tier = effectiveArmorTier(armor, finalDamage);

        if (random.nextDouble() < FLESH_WOUND_CHANCE[tier]) {
            applyFleshWound(player, injury, finalDamage);
            return true;
        }

        if (armor > HEAVY_ARMOR_THRESHOLD) {
            double bonusChance = HEAVY_WEAPON_BONUS_BASE;
            if (finalDamage >= 8.0F) bonusChance += HEAVY_WEAPON_BONUS_EXTRA;

            ItemStack weapon = getAttackerWeapon(source);
            if (weapon.getItem() instanceof AxeItem && random.nextDouble() < bonusChance) {
                applyFleshWound(player, injury, finalDamage);
                return true;
            }
            if (isCrossbowProjectile(source) && random.nextDouble() < bonusChance) {
                applyFleshWound(player, injury, finalDamage);
                return true;
            }
        }
        return false;
    }

    private static void applyFleshWound(ServerPlayer player, PlayerInjuryState injury, float finalDamage) {
        int severity = fleshWoundSeverity(finalDamage);
        injury.addFleshWound(severity);
        NetworkHandler.sendBleedingSplatter(player, SPLATTER_ON_WOUND_COUNT);
    }

    private static int effectiveArmorTier(int armor, float finalDamage) {
        int tier = armorTier(armor);
        if (finalDamage >= 12.0F) tier = Math.max(0, tier - 2);
        else if (finalDamage >= 8.0F) tier = Math.max(0, tier - 1);
        return tier;
    }

    private static ItemStack getAttackerWeapon(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof AbstractArrow || direct instanceof ThrownTrident) return ItemStack.EMPTY;
        Entity attacker = resolveAttacker(source);
        if (attacker instanceof Player player) return player.getMainHandItem();
        if (attacker instanceof LivingEntity living) return living.getMainHandItem();
        return ItemStack.EMPTY;
    }

    /** Direct melee from a mob (teeth/claws/fists), not a thrown or projected hit. */
    private static boolean isDirectMobMelee(DamageSource source) {
        if (source == null) return false;
        return source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypes.STING);
    }

    /** Prefer the causing entity; fall back to the direct entity for single-entity mob sources. */
    private static Entity resolveAttacker(DamageSource source) {
        if (source == null) return null;
        Entity attacker = source.getEntity();
        if (attacker != null) return attacker;
        Entity direct = source.getDirectEntity();
        return direct instanceof LivingEntity ? direct : null;
    }

    private static boolean isCrossbowProjectile(DamageSource source) {
        Entity direct = source.getDirectEntity();
        return direct instanceof AbstractArrow arrow && arrow.shotFromCrossbow();
    }

    private static double[] infectionRates(ServerPlayer player) {
        if (player.hasEffect(DiseaseEffects.IMMUNE.get()))            return INFECTION_CHANCE_BOOSTED;
        if (player.hasEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get())) return INFECTION_CHANCE_DEFICIENT;
        return INFECTION_CHANCE_NORMAL;
    }

    private static void setWoundEffect(ServerPlayer player, MobEffect effect, int amp) {
        MobEffectInstance cur = player.getEffect(effect);
        if (cur != null && cur.getAmplifier() == amp) return;
        if (cur != null) player.removeEffect(effect);
        player.addEffect(new MobEffectInstance(effect, WOUND_EFFECT_DURATION_TICKS, amp, false, false, true));
    }

    private static void clearWoundEffects(ServerPlayer player) {
        player.removeEffect(DiseaseEffects.FLESH_WOUND.get());
    }

    private static int armorTier(int armor) {
        if (armor <= 0)  return 0;
        if (armor <= 6)  return 1;
        if (armor <= 10) return 2;
        return 3;
    }

    private static boolean isLaceratingDamage(DamageSource source) {
        if (source == null) return false;
        Entity direct = source.getDirectEntity();
        if (direct instanceof AbstractArrow || direct instanceof ThrownTrident) return true;
        if (isDirectMobMelee(source)) {
            Entity attacker = resolveAttacker(source);
            if (attacker instanceof IronGolem) return false;
            if (attacker instanceof LivingEntity living && !(attacker instanceof Player)) {
                ItemStack hand = living.getMainHandItem();
                return hand.isEmpty() || !isBluntWeapon(hand);
            }
            return false;
        }
        Entity attacker = resolveAttacker(source);
        if (attacker instanceof Player player) return isSharp(player.getMainHandItem());
        return false;
    }

    private static boolean isBluntWeapon(ItemStack stack) {
        if (stack.isEmpty()) return true;
        Item item = stack.getItem();
        if (item instanceof AxeItem) return false;
        if (item instanceof SwordItem || item instanceof HoeItem
                || item instanceof ShearsItem || item instanceof TridentItem) return false;
        String id = item.getDescriptionId();
        return !id.contains("knife") && !id.contains("dagger") && !id.contains("blade") && !id.contains("spear");
    }

    private static boolean isSharp(ItemStack stack) {
        return !isBluntWeapon(stack) && !stack.isEmpty();
    }

    private static int fleshWoundSeverity(float finalDamage) {
        if (finalDamage >= 12.0F) return 2;
        if (finalDamage >= 8.0F) return 1;
        return 0;
    }
}
