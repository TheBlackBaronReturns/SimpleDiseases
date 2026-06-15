package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
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
    // Armor-tiered bleeding chance (mirrors M&H BleedingConditionsProcedure).
    // Tiers: 0 = unarmored, 1 = light (1-6), 2 = medium (7-10), 3 = heavy (11+).
    private static final float    MIN_BLEEDING_DAMAGE  = 5.0F;
    private static final double[] BLEEDING_CHANCE      = {0.10, 0.08, 0.05, 0.01};
    private static final double[] BLEEDING_AMOUNT      = {2.5,  1.5,  1.0,  0.5 };

    private static final double FLESH_WOUND_CHANCE     = 0.10;
    private static final float  MIN_FLESH_WOUND_DAMAGE = 4.0F;
    private static final int    MAX_FLESH_WOUND_ARMOR  = 7;

    private static final double INTERNAL_BLEEDING_DAMAGE_SCALE = 7.5 / 20.0;
    private static final double INTERNAL_BLEEDING_CHANCE       = 0.15;
    private static final float  MIN_INTERNAL_BLEEDING_DAMAGE   = 5.0F;

    private static final double BLOOD_LOSS_WOUND_THRESHOLD     = 3.5;
    private static final float  BLOOD_LOSS_HP_FLOOR            = 6.0F;
    private static final int    INJURY_EFFECT_DURATION_TICKS   = 20 * 40;
    private static final int    WOUND_EFFECT_DURATION_TICKS    = MobEffectInstance.INFINITE_DURATION;

    // Per-second (per-20-tick) infection seeding chance by flesh-wound severity phase [sev0, sev1, sev2].
    // Each phase lasts 150 s; values are solved so cumulative P across the full wound matches targets.
    // Boosted  → ~5 / ~10 / ~17%  (kept from original tuning)
    // Normal   → ~20 / ~35 / ~45%
    // Deficient→ ~35 / ~45 / ~60%
    private static final double[] INFECTION_CHANCE_BOOSTED   = {0.000342, 0.000351, 0.000496};
    private static final double[] INFECTION_CHANCE_NORMAL    = {0.001487, 0.001383, 0.001113};
    private static final double[] INFECTION_CHANCE_DEFICIENT = {0.002868, 0.001113, 0.002121};
    // Seed uniformly in [0, 0.5]: with accumulationRate 1/4800, this gives a 2–4 min wound-open window
    // to latch (seed=0 → 4 min, seed=0.5 → 2 min). Sev0 wounds (2.5 min) can outrun slower seeds.
    private static final double INFECTION_SEED_MIN = 0.0;
    private static final double INFECTION_SEED_MAX = 0.5;

    public void tick(ServerPlayer player, PlayerDiseaseState state) {
        PlayerInjuryState injury = state.injury();
        if (!injury.hasActiveInjury()) {
            clearWoundEffects(player);
            return;
        }

        injury.tick();

        double bleeding = injury.bleeding();
        if (bleeding > 0.5) {
            setWoundEffect(player, DiseaseEffects.BLEEDING.get(), Mth.clamp((int) (bleeding - 0.5), 0, 3));
        } else {
            player.removeEffect(DiseaseEffects.BLEEDING.get());
        }

        double internalBleeding = injury.internalBleeding();
        if (internalBleeding > 0.5) {
            setWoundEffect(player, DiseaseEffects.INTERNAL_BLEEDING.get(), Mth.clamp((int) (internalBleeding - 0.5), 0, 3));
        } else {
            player.removeEffect(DiseaseEffects.INTERNAL_BLEEDING.get());
        }

        int woundSeverity = injury.fleshWoundSeverity();

        long gameTime = player.level().getGameTime();
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

        if (injury.totalWoundLoad() >= BLOOD_LOSS_WOUND_THRESHOLD && player.getHealth() <= BLOOD_LOSS_HP_FLOOR) {
            player.addEffect(new MobEffectInstance(DiseaseEffects.BLOOD_LOSS.get(), INJURY_EFFECT_DURATION_TICKS, 0, false, false, true));
        }
    }

    private static double[] infectionRates(ServerPlayer player) {
        if (player.hasEffect(DiseaseEffects.IMMUNE.get()))            return INFECTION_CHANCE_BOOSTED;
        if (player.hasEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get())) return INFECTION_CHANCE_DEFICIENT;
        return INFECTION_CHANCE_NORMAL;
    }

    // Removes and re-adds the effect when the amplifier has changed, keeping it INFINITE_DURATION.
    // Plain addEffect() won't downgrade an existing higher-amplifier infinite effect, so the HUD
    // would otherwise stay stuck at the old (higher) tier.
    private static void setWoundEffect(ServerPlayer player, MobEffect effect, int amp) {
        MobEffectInstance cur = player.getEffect(effect);
        if (cur != null && cur.getAmplifier() == amp) return;
        if (cur != null) player.removeEffect(effect);
        player.addEffect(new MobEffectInstance(effect, WOUND_EFFECT_DURATION_TICKS, amp, false, false, true));
    }

    private static void clearWoundEffects(ServerPlayer player) {
        player.removeEffect(DiseaseEffects.BLEEDING.get());
        player.removeEffect(DiseaseEffects.INTERNAL_BLEEDING.get());
        player.removeEffect(DiseaseEffects.FLESH_WOUND.get());
    }

    public void onPlayerDamaged(ServerPlayer player, PlayerDiseaseState state, DamageSource source, float finalDamage) {
        if (finalDamage <= 0.0F || player.isCreative() || player.isSpectator()) return;

        PlayerInjuryState injury = state.injury();

        if (isBleedingDamage(source)) {
            if (finalDamage >= MIN_BLEEDING_DAMAGE) {
                int tier = bleedingArmorTier(player.getArmorValue());
                if (player.getRandom().nextDouble() < BLEEDING_CHANCE[tier]) {
                    injury.addBleeding(BLEEDING_AMOUNT[tier]);
                }
            }
            if (finalDamage >= MIN_FLESH_WOUND_DAMAGE
                    && player.getArmorValue() <= MAX_FLESH_WOUND_ARMOR
                    && isLaceratingDamage(source)
                    && player.getRandom().nextDouble() < FLESH_WOUND_CHANCE) {
                int severity = fleshWoundSeverity(finalDamage);
                injury.addFleshWound(severity);
                injury.addBleeding(fleshWoundBleedingBonus(severity));
            }
        }

        if (isInternalBleedingDamage(source)
                && finalDamage >= MIN_INTERNAL_BLEEDING_DAMAGE
                && player.getRandom().nextDouble() < INTERNAL_BLEEDING_CHANCE) {
            double armorFactor = Math.max(0.85, 1.0 - player.getArmorValue() * 0.0075);
            injury.addInternalBleeding(finalDamage * INTERNAL_BLEEDING_DAMAGE_SCALE * armorFactor);
        }
    }

    private static int bleedingArmorTier(int armor) {
        if (armor <= 0)  return 0;
        if (armor <= 6)  return 1;
        if (armor <= 10) return 2;
        return 3;
    }

    private static boolean isBleedingDamage(DamageSource source) {
        if (source == null) return false;
        String msg = source.getMsgId();
        if (source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.BYPASSES_ARMOR)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || msg.contains("starve")
                || msg.contains("magic")
                || msg.contains("wither")) return false;
        // Blunt player attacks route to internal bleeding only
        Entity attacker = source.getEntity();
        if (attacker instanceof Player p && !isSharp(p.getMainHandItem())) return false;
        return true;
    }

    private static boolean isInternalBleedingDamage(DamageSource source) {
        if (source == null) return false;
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_EXPLOSION)) return true;
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.BYPASSES_ARMOR)) return false;
        String msg = source.getMsgId();
        if (msg.contains("starve") || msg.contains("magic") || msg.contains("wither")) return false;
        Entity direct = source.getDirectEntity();
        if (direct instanceof AbstractArrow || direct instanceof ThrownTrident) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof Player p) return !isSharp(p.getMainHandItem());
        return false; // mob melee → external bleeding only
    }

    private static boolean isLaceratingDamage(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof AbstractArrow || direct instanceof ThrownTrident) return true;
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player) return isSharp(player.getMainHandItem());
        return false;
    }

    private static boolean isSharp(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof HoeItem
                || item instanceof ShearsItem || item instanceof TridentItem) return true;
        String id = item.getDescriptionId();
        return id.contains("knife") || id.contains("dagger") || id.contains("blade") || id.contains("spear");
    }

    private static int fleshWoundSeverity(float finalDamage) {
        if (finalDamage >= 12.0F) return 2;
        if (finalDamage >= 8.0F) return 1;
        return 0;
    }

    private static double fleshWoundBleedingBonus(int severity) {
        return switch (severity) {
            case 2 -> 1.5;
            case 1 -> 1.0;
            default -> 0.5;
        };
    }
}
