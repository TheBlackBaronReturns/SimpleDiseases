package com.theblackbaron.simplediseases.status.service;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.component.SymptomPoolComponent;
import com.theblackbaron.simplediseases.status.def.Severity;
import com.theblackbaron.simplediseases.status.def.SymptomAction;
import com.theblackbaron.simplediseases.status.def.SymptomConfig;
import com.theblackbaron.simplediseases.status.def.SymptomEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;

/**
 * Shared symptom-episode machinery, category-agnostic. Operates on a {@link SymptomPoolComponent}
 * (live state) plus a {@link SymptomConfig} (the Moderate-baseline pool + pacing), scaled by the
 * illness's rolled {@link Severity}: higher tiers get longer episodes and shorter intervals. Logic
 * is the hardened cold/flu behavior — a monotonic high-water-mark pool during accumulation, recurring
 * random episodes during recovery, and full suppression under TREATMENT_APPLIED.
 */
public final class SymptomService {
    private SymptomService() {}

    // Magnitudes for the DRAIN_FOOD action (flu vomiting): drop hunger, reset saturation.
    private static final int   VOMIT_FOOD_DROP      = 3;
    private static final float VOMIT_SATURATION_SET = 0.0f;

    /**
     * Accumulation phase: grow the pool as progress reaches new thresholds (monotonic — never
     * removed if progress dips), resetting at 0. Fires the newly added symptom once, unless
     * TREATMENT_APPLIED suppresses the visual.
     *
     * <p>Selection is gated by the illness's rolled {@code severity}: only symptoms whose
     * {@link SymptomEntry#minSeverity()} is at or below the rolled tier are eligible to be drawn, and
     * the count is capped at the eligible total. So a mild flu can never pull vomiting (min SEVERE),
     * while a severe one merely has the chance to — selection stays random. The tier is rolled at the
     * first threshold (see ViralCategory), so it's available here before any symptom is chosen.
     */
    public static SymptomEntry syncPool(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config, double progress, Severity severity) {
        int addedBit = -1;
        if (progress <= 0.0) {
            pool.clearAll();
        } else {
            int bits = config.symptomBits();
            // A treatment reduction may have dropped the tier below a pooled symptom's gate — drop it.
            pruneIneligible(player, pool, config, severity);
            int targetCount = 0;
            for (double threshold : config.thresholds()) {
                if (progress >= threshold) targetCount++;
            }
            // Cap the target at how many symptoms this tier is even allowed to manifest.
            int eligibleCount = 0;
            for (int b = 0; b < bits; b++) {
                if (eligible(config, b, severity)) eligibleCount++;
            }
            if (targetCount > eligibleCount) targetCount = eligibleCount;

            int symptomCount = pool.count();
            while (symptomCount < targetCount) {
                int unusedEligible = 0;
                for (int b = 0; b < bits; b++) {
                    if (!pool.has(b) && eligible(config, b, severity)) unusedEligible++;
                }
                int pick = player.getRandom().nextInt(unusedEligible);
                for (int b = 0; b < bits; b++) {
                    if (!pool.has(b) && eligible(config, b, severity)) {
                        if (pick == 0) { pool.set(b); addedBit = b; break; }
                        pick--;
                    }
                }
                symptomCount++;
            }
        }
        if (addedBit >= 0 && !player.hasEffect(DiseaseEffects.TREATMENT_APPLIED.get())) {
            return fire(player, config, addedBit, severity);
        }
        return null;
    }

    /** Whether symptom {@code bit} is eligible at the rolled {@code severity} (min-severity gate). */
    private static boolean eligible(SymptomConfig config, int bit, Severity severity) {
        return severity.ordinal() >= config.pool().get(bit).minSeverity().ordinal();
    }

    /** Clears any pooled symptom no longer eligible at the current tier (bit + active effect). Lets a
     *  treatment-driven tier reduction retroactively remove a symptom that's now above the gate. */
    private static void pruneIneligible(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config, Severity severity) {
        int bits = config.symptomBits();
        for (int b = 0; b < bits; b++) {
            if (pool.has(b) && !eligible(config, b, severity)) {
                pool.clear(b);
                MobEffect effect = config.pool().get(b).effect().get();
                if (player.hasEffect(effect)) player.removeEffect(effect);
            }
        }
    }

    /**
     * Runs for the whole time the disease is latched: picks a random pool symptom each episode for a
     * tier-scaled duration, then schedules the next after a tier-scaled interval. The first episode
     * is scheduled one interval out from latch. TREATMENT_APPLIED cancels the active episode and holds
     * the timer so nothing fires the instant it expires.
     */
    public static SymptomEntry tickEpisodes(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config, long gameTime, Severity severity) {
        // A treatment reduction during recovery may have dropped the tier below a pooled symptom's
        // gate — drop it so a now-too-severe symptom stops being picked for episodes.
        pruneIneligible(player, pool, config, severity);
        int symptomCount = pool.count();
        if (symptomCount == 0) return null;

        if (player.hasEffect(DiseaseEffects.TREATMENT_APPLIED.get())) {
            clearActive(player, pool, config);
            pool.nextEpisodeAt = gameTime + severity.scaleInterval(config.minIntervalTicks());
            return null;
        }

        long nextAt = pool.nextEpisodeAt;
        if (nextAt == 0L) {
            pool.nextEpisodeAt = gameTime + scaledInterval(player.getRandom(), config, severity);
            return null;
        }
        if (gameTime < nextAt) return null;

        SymptomEntry fired = null;
        int pick = player.getRandom().nextInt(symptomCount);
        int bits = config.symptomBits();
        for (int b = 0; b < bits; b++) {
            if (pool.has(b)) {
                if (pick == 0) { fired = fire(player, config, b, severity); break; }
                pick--;
            }
        }
        pool.nextEpisodeAt = gameTime + scaledInterval(player.getRandom(), config, severity);
        return fired;
    }

    /** Remove any pool-symptom effects currently on the player. Called on cure and under treatment. */
    public static void clearActive(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config) {
        int bits = config.symptomBits();
        for (int b = 0; b < bits; b++) {
            if (pool.has(b)) {
                SymptomEntry entry = config.pool().get(b);
                MobEffect effect = entry.effect().get();
                if (player.hasEffect(effect)) {
                    player.removeEffect(effect);
                    clearSymptomSideEffect(player, entry.action());
                }
            }
        }
    }

    private static void clearSymptomSideEffect(ServerPlayer player, SymptomAction action) {
        switch (action) {
            case DAMAGE -> {
                if (player.hasEffect(DiseaseEffects.COUGH_FIT.get())) player.removeEffect(DiseaseEffects.COUGH_FIT.get());
            }
            case NAUSEA -> {
                if (player.hasEffect(MobEffects.CONFUSION)) player.removeEffect(MobEffects.CONFUSION);
            }
            case BREATHLESS -> {
                if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
            default -> {}
        }
    }

    private static SymptomEntry fire(ServerPlayer player, SymptomConfig config, int bit, Severity severity) {
        SymptomEntry entry = config.pool().get(bit);
        // The symptom's marker effect (the HUD icon) always lasts a full tier-scaled episode, like the
        // other symptoms. Its impactful side effect (nausea, the breathless slow) runs for a shorter
        // fixed burst when the entry sets durationTicks; otherwise it spans the whole episode.
        int episodeDuration = randomTicks(player.getRandom(),
                severity.scaleDuration(config.minDurationTicks()),
                severity.scaleDuration(config.maxDurationTicks()));
        int amp = entry.severityAmp() ? Math.max(0, severity.ordinal() - Severity.MILD.ordinal()) : entry.amplifier();
        player.addEffect(new MobEffectInstance(entry.effect().get(), episodeDuration, amp, false, false, true));
        int impactDuration = entry.durationTicks().orElse(episodeDuration);
        applyAction(player, entry.action(), impactDuration);
        entry.sound().ifPresent(sound -> playOnsetSound(player, sound.get(), severity));
        return entry;
    }

    /** Plays a symptom's onset sound for everyone nearby (so others hear you cough/sneeze). Higher
     *  severity is louder and a touch lower-pitched, with small random variation between fires. */
    private static void playOnsetSound(ServerPlayer player, SoundEvent sound, Severity severity) {
        float volume = (float) Math.min(1.0, 0.55 + 0.25 * severity.debuffMult);
        float pitch  = 1.05f - (float) (0.07 * (severity.debuffMult - 1.0))
                + (player.getRandom().nextFloat() - 0.5f) * 0.15f;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static int scaledInterval(RandomSource rng, SymptomConfig config, Severity severity) {
        return randomTicks(rng,
                severity.scaleInterval(config.minIntervalTicks()),
                severity.scaleInterval(config.maxIntervalTicks()));
    }

    private static void applyAction(ServerPlayer player, SymptomAction action, int duration) {
        switch (action) {
            case DRAIN_FOOD -> {
                FoodData food = player.getFoodData();
                food.setFoodLevel(Math.max(0, food.getFoodLevel() - VOMIT_FOOD_DROP));
                food.setSaturation(VOMIT_SATURATION_SET);
            }
            // Vanilla Nausea I — icon/particles hidden (the headache icon is the visible symptom
            // marker), but the screen-warp still plays while CONFUSION is present.
            case NAUSEA -> player.addEffect(
                    new MobEffectInstance(MobEffects.CONFUSION, duration, 0, false, false, false));
            // Drastic Slowness (IV ≈ −60%) — icon/particles hidden (the shortness-of-breath icon is
            // the visible marker); only the brief movement crash is felt.
            case BREATHLESS -> player.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 3, false, false, false));
            // Bad Cough: the hidden COUGH_FIT effect hurts the player once a second for the window.
            case DAMAGE -> player.addEffect(
                    new MobEffectInstance(DiseaseEffects.COUGH_FIT.get(), duration, 0, false, false, false));
            default -> {}
        }
    }

    private static int randomTicks(RandomSource rng, int minTicks, int maxTicks) {
        return minTicks + rng.nextInt(maxTicks - minTicks + 1);
    }
}
