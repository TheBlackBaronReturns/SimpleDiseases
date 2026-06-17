package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.particle.DiseaseParticleEmitter;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
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

    private static final double[] FLESH_WOUND_CHANCE   = {0.10, 0.07, 0.04, 0.02};
    private static final float  MIN_FLESH_WOUND_DAMAGE = 4.0F;
    private static final int    HEAVY_ARMOR_THRESHOLD    = 7;
    private static final double HEAVY_WEAPON_BONUS_BASE  = 0.05;
    private static final double HEAVY_WEAPON_BONUS_EXTRA = 0.03;

    private static final double INTERNAL_BLEEDING_DAMAGE_SCALE = 7.5 / 20.0;
    private static final double INTERNAL_BLEEDING_CHANCE       = 0.15;
    private static final float  MIN_INTERNAL_BLEEDING_DAMAGE   = 5.0F;

    private static final double BLOOD_LOSS_WOUND_THRESHOLD     = 3.5;
    private static final float  BLOOD_LOSS_HP_FLOOR            = 6.0F;
    private static final int    WOUND_EFFECT_DURATION_TICKS    = MobEffectInstance.INFINITE_DURATION;

    private static final int PAIN_EPISODE_MIN_INTERVAL = 45 * 20;
    private static final int PAIN_EPISODE_MAX_INTERVAL = 90 * 20;
    private static final int PAIN_DURATION_MIN         = 20 * 20;
    private static final int PAIN_DURATION_MAX         = 40 * 20;

    private static final double LIGHT_BLEEDING_AMOUNT = 1.0;
    private static final int    CACTUS_BLEED_COOLDOWN = 20;

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

        double bleeding = injury.bleeding();
        if (bleeding >= 0.5) {
            MobEffectInstance bleedingEffect = player.getEffect(DiseaseEffects.BLEEDING.get());
            int amp = bleedingEffect != null ? bleedingEffect.getAmplifier() : 0;
            setWoundEffect(player, DiseaseEffects.BLEEDING.get(), Mth.clamp((int) (bleeding - 0.5), 0, 3));
            if (player.hasEffect(DiseaseEffects.BLEEDING.get())) {
                DiseaseParticleEmitter.emitBleeding(player, injury, amp, gameTime);
            }
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
            tickFleshWoundPain(player, injury, state, gameTime);
        } else {
            player.removeEffect(DiseaseEffects.FLESH_WOUND.get());
        }

        if (injury.totalWoundLoad() >= BLOOD_LOSS_WOUND_THRESHOLD && player.getHealth() <= BLOOD_LOSS_HP_FLOOR) {
            setWoundEffect(player, DiseaseEffects.BLOOD_LOSS.get(), 0);
        } else {
            player.removeEffect(DiseaseEffects.BLOOD_LOSS.get());
        }
    }

    public void onPlayerDamaged(ServerPlayer player, PlayerDiseaseState state, DamageSource source, float finalDamage) {
        if (finalDamage <= 0.0F || player.isCreative() || player.isSpectator()) return;

        PlayerInjuryState injury = state.injury();

        if (isCactusDamage(source)) {
            long gameTime = player.level().getGameTime();
            if (gameTime - injury.lastCactusBleedTick() >= CACTUS_BLEED_COOLDOWN) {
                injury.setLastCactusBleedTick(gameTime);
                injury.addBleeding(LIGHT_BLEEDING_AMOUNT);
            }
        }

        tryMobBiteBleeding(injury, source, player.getRandom());

        if (isBleedingDamage(source)) {
            if (finalDamage >= minBleedingDamage(source)) {
                int tier = effectiveArmorTier(player.getArmorValue(), finalDamage);
                if (player.getRandom().nextDouble() < BLEEDING_CHANCE[tier]) {
                    injury.addBleeding(BLEEDING_AMOUNT[tier]);
                }
            }
        }

        tryFleshWound(player, injury, state, source, finalDamage);

        if (isInternalBleedingDamage(source)
                && finalDamage >= MIN_INTERNAL_BLEEDING_DAMAGE
                && player.getRandom().nextDouble() < INTERNAL_BLEEDING_CHANCE) {
            double armorFactor = Math.max(0.85, 1.0 - player.getArmorValue() * 0.0075);
            injury.addInternalBleeding(finalDamage * INTERNAL_BLEEDING_DAMAGE_SCALE * armorFactor);
        }
    }

    public void onGlassBrokenBareHand(ServerPlayer player, PlayerDiseaseState state) {
        if (player.isCreative() || player.isSpectator()) return;
        if (!player.getMainHandItem().isEmpty()) return;
        state.injury().addBleeding(LIGHT_BLEEDING_AMOUNT);
    }

    private void tickFleshWoundPain(ServerPlayer player, PlayerInjuryState injury,
                                    PlayerDiseaseState state, long gameTime) {
        if (state.inRecovery(DiseaseRegistry.CELLULITIS_STAPH)) return;
        if (player.hasEffect(DiseaseEffects.TREATMENT_APPLIED.get())) return;

        RandomSource random = player.getRandom();
        if (injury.nextPainEpisodeAt() == 0L) {
            injury.setNextPainEpisodeAt(gameTime + randomBetween(random, PAIN_EPISODE_MIN_INTERVAL, PAIN_EPISODE_MAX_INTERVAL));
            return;
        }

        if (gameTime >= injury.nextPainEpisodeAt()) {
            int duration = randomBetween(random, PAIN_DURATION_MIN, PAIN_DURATION_MAX);
            player.addEffect(new MobEffectInstance(DiseaseEffects.SHARP_PAIN.get(), duration, 0, false, false, true));
            injury.setNextPainEpisodeAt(gameTime + randomBetween(random, PAIN_EPISODE_MIN_INTERVAL, PAIN_EPISODE_MAX_INTERVAL));
        }
    }

    private static boolean tryFleshWound(ServerPlayer player, PlayerInjuryState injury,
                                         PlayerDiseaseState state, DamageSource source, float finalDamage) {
        if (finalDamage < MIN_FLESH_WOUND_DAMAGE || !isLaceratingDamage(source)) return false;

        RandomSource random = player.getRandom();
        int armor = player.getArmorValue();
        int tier = effectiveArmorTier(armor, finalDamage);
        long gameTime = player.level().getGameTime();

        if (random.nextDouble() < FLESH_WOUND_CHANCE[tier]) {
            applyFleshWound(player, injury, state, finalDamage, gameTime);
            return true;
        }

        if (armor > HEAVY_ARMOR_THRESHOLD) {
            double bonusChance = HEAVY_WEAPON_BONUS_BASE;
            if (finalDamage >= 8.0F) bonusChance += HEAVY_WEAPON_BONUS_EXTRA;

            ItemStack weapon = getAttackerWeapon(source);
            if (weapon.getItem() instanceof AxeItem && random.nextDouble() < bonusChance) {
                applyFleshWound(player, injury, state, finalDamage, gameTime);
                return true;
            }
            if (isCrossbowProjectile(source) && random.nextDouble() < bonusChance) {
                applyFleshWound(player, injury, state, finalDamage, gameTime);
                return true;
            }
        }
        return false;
    }

    private static void applyFleshWound(ServerPlayer player, PlayerInjuryState injury,
                                        PlayerDiseaseState state, float finalDamage, long gameTime) {
        int severity = fleshWoundSeverity(finalDamage);
        injury.addFleshWound(severity);
        injury.addBleeding(fleshWoundBleedingBonus(severity));
        fireFleshWoundPainEpisode(player, injury, state, gameTime);
    }

    private static void fireFleshWoundPainEpisode(ServerPlayer player, PlayerInjuryState injury,
                                                  PlayerDiseaseState state, long gameTime) {
        if (state.inRecovery(DiseaseRegistry.CELLULITIS_STAPH)) return;
        if (player.hasEffect(DiseaseEffects.TREATMENT_APPLIED.get())) return;

        RandomSource random = player.getRandom();
        int duration = randomBetween(random, PAIN_DURATION_MIN, PAIN_DURATION_MAX);
        player.addEffect(new MobEffectInstance(DiseaseEffects.SHARP_PAIN.get(), duration, 0, false, false, true));
        injury.setNextPainEpisodeAt(gameTime + randomBetween(random, PAIN_EPISODE_MIN_INTERVAL, PAIN_EPISODE_MAX_INTERVAL));
    }

    private static int effectiveArmorTier(int armor, float finalDamage) {
        int tier = bleedingArmorTier(armor);
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

    private static void tryMobBiteBleeding(PlayerInjuryState injury, DamageSource source, RandomSource random) {
        if (!isMobBiteDamage(source)) return;
        if (random.nextDouble() < 0.5) {
            injury.addBleeding(LIGHT_BLEEDING_AMOUNT);
        }
    }

    /** Direct melee from a mob (teeth/claws/fists), not a thrown or projected hit. */
    private static boolean isDirectMobMelee(DamageSource source) {
        if (source == null) return false;
        return source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypes.STING);
    }

    private static boolean isMobBiteDamage(DamageSource source) {
        if (!isDirectMobMelee(source)) return false;
        Entity attacker = resolveAttacker(source);
        if (attacker instanceof Llama) return false;
        return attacker instanceof Zombie || attacker instanceof Spider || attacker instanceof Animal;
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

    private static boolean isCactusDamage(DamageSource source) {
        return source != null && "cactus".equals(source.getMsgId());
    }

    private static int randomBetween(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
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
        player.removeEffect(DiseaseEffects.BLEEDING.get());
        player.removeEffect(DiseaseEffects.INTERNAL_BLEEDING.get());
        player.removeEffect(DiseaseEffects.FLESH_WOUND.get());
        player.removeEffect(DiseaseEffects.BLOOD_LOSS.get());
    }

    private static int bleedingArmorTier(int armor) {
        if (armor <= 0)  return 0;
        if (armor <= 6)  return 1;
        if (armor <= 10) return 2;
        return 3;
    }

    /** Blunt hits need ≥5 HP; lacerating sources (arrows, sharp weapons, mob claws/teeth) need ≥4 HP. */
    private static float minBleedingDamage(DamageSource source) {
        return isLaceratingDamage(source) ? MIN_FLESH_WOUND_DAMAGE : MIN_BLEEDING_DAMAGE;
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
        Entity attacker = resolveAttacker(source);
        if (attacker instanceof IronGolem) return false;
        if (attacker instanceof Player p && isBluntWeapon(p.getMainHandItem())) return false;
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
        Entity attacker = resolveAttacker(source);
        if (attacker instanceof IronGolem) return isDirectMobMelee(source);
        if (attacker instanceof Player p) return isBluntWeapon(p.getMainHandItem());
        return false;
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

    private static double fleshWoundBleedingBonus(int severity) {
        return switch (severity) {
            case 2 -> 1.5;
            case 1 -> 1.0;
            default -> 0.5;
        };
    }
}
