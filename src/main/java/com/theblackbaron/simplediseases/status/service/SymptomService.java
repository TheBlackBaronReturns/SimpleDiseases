package com.theblackbaron.simplediseases.status.service;

import com.theblackbaron.simplediseases.status.BloodyCoughingEffect;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.component.SymptomPoolComponent;
import com.theblackbaron.simplediseases.status.def.Severity;
import com.theblackbaron.simplediseases.status.def.SymptomAction;
import com.theblackbaron.simplediseases.status.def.SymptomBand;
import com.theblackbaron.simplediseases.status.def.SymptomConfig;
import com.theblackbaron.simplediseases.status.def.SymptomEntry;
import com.theblackbaron.simplediseases.status.def.SymptomTiming;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Hallmark-first pool sync, episodic rotation, static markers, and tier-worsen severe upgrades.
 */
public final class SymptomService {
    private SymptomService() {}

    private static final int   VOMIT_FOOD_DROP      = 3;
    private static final float VOMIT_SATURATION_SET = 0.0f;

    public static SymptomEntry syncPool(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config,
                                        double progress, Severity severity, MobEffect diseaseEffectForAmp) {
        return syncPool(player, pool, config, progress, severity, diseaseEffectForAmp, Optional.empty());
    }

    public static SymptomEntry syncPool(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config,
                                        double progress, Severity severity, MobEffect diseaseEffectForAmp,
                                        Optional<SourceSymptomSnapshot> source) {
        if (progress <= 0.0) {
            pool.clearAll();
            pool.dirty = true;
            ensureStaticMarkers(player, pool, config);
            return null;
        }

        int targetCount = targetCount(config, progress, severity);
        SymptomEntry fired = null;
        while (pool.count() < targetCount) {
            int bit = pickNextBit(player, pool, config, severity, source);
            if (bit < 0) break;
            setPoolBit(player, pool, config, bit);
            if (config.entryAt(bit).timing() != SymptomTiming.STATIC
                    && !player.hasEffect(DiseaseEffects.TREATMENT_APPLIED.get())) {
                fired = fire(player, config, bit, severity, diseaseEffectForAmp);
            }
        }
        ensureStaticMarkers(player, pool, config);
        return fired;
    }

    public static SymptomEntry tickEpisodes(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config,
                                            long gameTime, Severity severity, MobEffect diseaseEffectForAmp) {
        int episodicCount = episodicCount(pool, config);
        if (episodicCount == 0) {
            ensureStaticMarkers(player, pool, config);
            return null;
        }

        if (player.hasEffect(DiseaseEffects.TREATMENT_APPLIED.get())
                || player.hasEffect(DiseaseEffects.SYMPTOMS_MANAGED.get())) {
            clearEpisodic(player, pool, config);
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
        int pick = player.getRandom().nextInt(episodicCount);
        int bits = config.symptomBits();
        for (int b = 0; b < bits; b++) {
            if (!pool.has(b) || config.entryAt(b).timing() == SymptomTiming.STATIC) continue;
            if (pick == 0) {
                fired = fire(player, config, b, severity, diseaseEffectForAmp);
                break;
            }
            pick--;
        }
        pool.nextEpisodeAt = gameTime + scaledInterval(player.getRandom(), config, severity);
        return fired;
    }

    /** Tier worsen: swap a random common slot for a newly eligible severe symptom, or fill an open slot. */
    public static void tryUpgradeIncidental(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config,
                                            Severity oldTier, Severity newTier, MobEffect diseaseEffectForAmp) {
        if (newTier.ordinal() <= oldTier.ordinal()) return;
        List<Integer> unlocks = new ArrayList<>();
        int bits = config.symptomBits();
        for (int b = 0; b < bits; b++) {
            if (pool.has(b)) continue;
            SymptomEntry entry = config.entryAt(b);
            if (entry.band() != SymptomBand.ADVANCED) continue;
            if (entry.inheritOnly()) continue;
            if (!entry.band().eligibleAt(newTier)) continue;
            if (entry.band().eligibleAt(oldTier)) continue;
            unlocks.add(b);
        }
        if (unlocks.isEmpty()) return;

        RandomSource rng = player.getRandom();
        int severeBit = unlocks.get(rng.nextInt(unlocks.size()));

        List<Integer> commonBits = new ArrayList<>();
        for (int b = 0; b < bits; b++) {
            if (pool.has(b) && config.isCommon(b)) commonBits.add(b);
        }

        if (!commonBits.isEmpty()) {
            int commonBit = commonBits.get(rng.nextInt(commonBits.size()));
            clearBit(player, pool, config, commonBit);
        } else if (pool.count() >= config.thresholds().size()) {
            return;
        }

        setPoolBit(player, pool, config, severeBit);
        SymptomEntry entry = config.entryAt(severeBit);
        if (entry.timing() != SymptomTiming.STATIC) {
            fire(player, config, severeBit, newTier, diseaseEffectForAmp);
        }
        ensureStaticMarkers(player, pool, config);
    }

    public static void ensureStaticMarkers(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config) {
        if (!pool.dirty) return;
        pool.dirty = false;
        int bits = config.symptomBits();
        for (int b = 0; b < bits; b++) {
            SymptomEntry entry = config.entryAt(b);
            if (entry.timing() != SymptomTiming.STATIC) continue;
            MobEffect effect = entry.effect().get();
            if (pool.has(b)) {
                if (!player.hasEffect(effect)) {
                    player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION,
                            entry.amplifier(), false, false, true));
                }
            } else if (player.hasEffect(effect)) {
                player.removeEffect(effect);
            }
        }
    }

    public static void clearActive(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config) {
        clearEpisodic(player, pool, config);
        clearStatic(player, pool, config);
    }

    public static void clearEpisodic(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config) {
        int bits = config.symptomBits();
        for (int b = 0; b < bits; b++) {
            if (!pool.has(b)) continue;
            SymptomEntry entry = config.entryAt(b);
            if (entry.timing() == SymptomTiming.STATIC) continue;
            MobEffect effect = entry.effect().get();
            if (player.hasEffect(effect)) {
                player.removeEffect(effect);
                clearSymptomSideEffect(player, entry.action());
            }
        }
        BloodyCoughingEffect.clearDamageWindow(player);
    }

    public static void clearStatic(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config) {
        int bits = config.symptomBits();
        for (int b = 0; b < bits; b++) {
            SymptomEntry entry = config.entryAt(b);
            if (entry.timing() != SymptomTiming.STATIC) continue;
            MobEffect effect = entry.effect().get();
            if (player.hasEffect(effect)) player.removeEffect(effect);
        }
    }

    // --- internals ---------------------------------------------------------------------------

    private static int targetCount(SymptomConfig config, double progress, Severity severity) {
        int target = 0;
        for (double threshold : config.thresholds()) {
            if (progress >= threshold) target++;
        }
        target = Math.min(target, config.symptomBits());
        int eligible = 0;
        for (int b = 0; b < config.symptomBits(); b++) {
            if (eligible(config, b, severity)) eligible++;
        }
        return Math.min(target, eligible);
    }

    private static int pickNextBit(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config,
                                   Severity severity, Optional<SourceSymptomSnapshot> source) {
        for (int b = 0; b < config.hallmarkCount(); b++) {
            if (!pool.has(b) && eligible(config, b, severity)) return b;
        }
        if (source.isPresent()) {
            SourceSymptomSnapshot snap = source.get();
            for (int bit : config.inheritableFromSource(snap.config(), snap.sourceMask(), severity, pool.mask)) {
                return bit;
            }
        }
        List<Integer> candidates = new ArrayList<>();
        int bits = config.symptomBits();
        for (int b = config.hallmarkCount(); b < bits; b++) {
            if (pool.has(b)) continue;
            SymptomEntry entry = config.entryAt(b);
            if (entry.inheritOnly()) continue;
            if (!eligible(config, b, severity)) continue;
            candidates.add(b);
        }
        if (candidates.isEmpty()) return -1;
        return candidates.get(player.getRandom().nextInt(candidates.size()));
    }

    private static boolean eligible(SymptomConfig config, int bit, Severity severity) {
        return config.entryAt(bit).band().eligibleAt(severity);
    }

    private static void setPoolBit(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config, int bit) {
        SymptomEntry entry = config.entryAt(bit);
        if (config.isCoughVariant(entry.effect().get())) {
            clearOtherCoughVariants(player, pool, config, entry.effect().get());
        }
        config.exclusivePeer(entry.effect().get()).ifPresent(peer -> {
            int peerBit = config.indexOfEffect(peer);
            if (peerBit >= 0) clearBit(player, pool, config, peerBit);
        });
        pool.set(bit);
        pool.dirty = true;
    }

    private static void clearBit(ServerPlayer player, SymptomPoolComponent pool, SymptomConfig config, int bit) {
        if (!pool.has(bit)) return;
        SymptomEntry entry = config.entryAt(bit);
        pool.clear(bit);
        pool.dirty = true;
        MobEffect effect = entry.effect().get();
        if (player.hasEffect(effect)) {
            player.removeEffect(effect);
            clearSymptomSideEffect(player, entry.action());
        }
    }

    private static void clearOtherCoughVariants(ServerPlayer player, SymptomPoolComponent pool,
                                                   SymptomConfig config, MobEffect kept) {
        for (Supplier<MobEffect> variant : config.coughVariantGroup()) {
            MobEffect effect = variant.get();
            if (effect == kept) continue;
            int bit = config.indexOfEffect(effect);
            if (bit >= 0) clearBit(player, pool, config, bit);
        }
    }

    private static int episodicCount(SymptomPoolComponent pool, SymptomConfig config) {
        int count = 0;
        for (int b = 0; b < config.symptomBits(); b++) {
            if (pool.has(b) && config.entryAt(b).timing() != SymptomTiming.STATIC) count++;
        }
        return count;
    }

    private static SymptomEntry fire(ServerPlayer player, SymptomConfig config, int bit, Severity severity,
                                     MobEffect diseaseEffectForAmp) {
        SymptomEntry entry = config.entryAt(bit);
        int episodeDuration = randomTicks(player.getRandom(),
                severity.scaleDuration(config.minDurationTicks()),
                severity.scaleDuration(config.maxDurationTicks()));
        int amp = entry.amplifier();
        player.addEffect(new MobEffectInstance(entry.effect().get(), episodeDuration, amp, false, false, true));
        int impactDuration = entry.durationTicks().orElse(episodeDuration);
        applyAction(player, entry, impactDuration);
        entry.sound().ifPresent(sound -> playOnsetSound(player, sound.get(), severity));
        return entry;
    }

    private static void applyAction(ServerPlayer player, SymptomEntry entry, int duration) {
        long gameTime = player.level().getGameTime();
        switch (entry.action()) {
            case DRAIN_FOOD -> {
                FoodData food = player.getFoodData();
                food.setFoodLevel(Math.max(0, food.getFoodLevel() - VOMIT_FOOD_DROP));
                food.setSaturation(VOMIT_SATURATION_SET);
            }
            case NAUSEA -> player.addEffect(
                    new MobEffectInstance(MobEffects.CONFUSION, duration, 0, false, false, false));
            case BREATHLESS -> player.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 3, false, false, false));
            case HYPOTENSION -> { /* pulses via HypotensionEffect.applyEffectTick */ }
            case DAMAGE -> {
                if (entry.effect().get() == DiseaseEffects.BLOODY_COUGHING.get()) {
                    BloodyCoughingEffect.beginDamageWindow(player, gameTime + duration);
                }
            }
            default -> {}
        }
    }

    private static void clearSymptomSideEffect(ServerPlayer player, SymptomAction action) {
        switch (action) {
            case DAMAGE -> BloodyCoughingEffect.clearDamageWindow(player);
            case NAUSEA -> {
                if (player.hasEffect(MobEffects.CONFUSION)) player.removeEffect(MobEffects.CONFUSION);
            }
            case BREATHLESS -> {
                if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
            case HYPOTENSION -> {
                if (player.hasEffect(MobEffects.BLINDNESS)) player.removeEffect(MobEffects.BLINDNESS);
                if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
            default -> {}
        }
    }

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

    private static int randomTicks(RandomSource rng, int minTicks, int maxTicks) {
        return minTicks + rng.nextInt(maxTicks - minTicks + 1);
    }
}
