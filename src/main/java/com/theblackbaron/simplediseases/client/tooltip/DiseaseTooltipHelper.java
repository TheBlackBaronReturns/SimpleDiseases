package com.theblackbaron.simplediseases.client.tooltip;

import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.client.ClientDiseaseState;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import com.theblackbaron.simplediseases.status.component.Components;
import com.theblackbaron.simplediseases.status.component.DiseaseInstance;
import com.theblackbaron.simplediseases.status.component.SymptomPoolComponent;
import com.theblackbaron.simplediseases.status.def.BacterialDiseaseDef;
import com.theblackbaron.simplediseases.status.def.ComplicationDiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.Severity;
import com.theblackbaron.simplediseases.status.def.SymptomConfig;
import com.theblackbaron.simplediseases.status.def.SymptomEntry;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import com.theblackbaron.simplediseases.status.manager.PlayerDiseaseState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Builds "When Applied" tooltip rows for disease tier effects from synced player disease state. */
public final class DiseaseTooltipHelper {
    private DiseaseTooltipHelper() {}

    /**
     * Rows to insert directly under the {@code potion.whenDrank} header: fever or shock, pain, then
     * active pool symptoms.
     */
    public static List<Component> buildWhenAppliedRows(MobEffectInstance instance, @Nullable Player player) {
        List<Component> rows = new ArrayList<>();
        MobEffect effect = instance.getEffect();
        if (!(effect instanceof DiseaseMobEffect dme)) return rows;

        // Fever or septic shock — mutually exclusive top row
        if (dme.getShockOffset() > 0.0) {
            rows.add(Component.translatable("simplediseases.shock"));
        } else if (DiseaseEffects.shouldShowFeverTooltip(player, dme)) {
            rows.add(feverLine(dme.getFeverOffset()));
        }

        if (player == null) return rows;

        PlayerDiseaseState state = ClientDiseaseState.forPlayer(player);
        Optional<ResourceLocation> diseaseId = diseaseIdForEffect(effect);
        if (diseaseId.isEmpty()) return rows;

        ResourceLocation id = diseaseId.get();
        if (DiseaseEffects.shouldShowPainTooltip(id, state)) {
            int amp = painAmplifierFor(id, state);
            if (amp >= 0) {
                rows.add(Component.translatable("simplediseases.pain." + amp));
            }
        }

        DiseaseInstance inst = state.peek(id);
        if (inst == null) return rows;

        SymptomConfig config = symptomsOf(DiseaseRegistry.get(id));
        if (config == null) return rows;

        SymptomPoolComponent pool = inst.get(Components.SYMPTOMS);
        if (pool == null) return rows;

        MobEffect malaise = DiseaseEffects.MALAISE.get();
        for (int bit = 0; bit < config.symptomBits(); bit++) {
            if (!pool.has(bit)) continue;
            SymptomEntry entry = config.entryAt(bit);
            MobEffect symptomEffect = entry.effect().get();
            if (symptomEffect == malaise) continue;
            ResourceLocation effectKey = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(symptomEffect);
            if (effectKey == null) continue;
            rows.add(Component.translatable("simplediseases.symptom." + effectKey.getPath())
                    .withStyle(ChatFormatting.RED));
        }

        return rows;
    }

    private static Component feverLine(double feverOffset) {
        String langKey = feverOffset >= DiseaseMobEffect.FEVER_SEVERE ? "simplediseases.fever.severe" :
                         feverOffset >= DiseaseMobEffect.FEVER_HIGH   ? "simplediseases.fever.high" :
                         feverOffset >= DiseaseMobEffect.FEVER_MILD   ? "simplediseases.fever.mild" :
                                                                        "simplediseases.fever.light";
        return Component.translatable(langKey);
    }

    private static int painAmplifierFor(ResourceLocation diseaseId, PlayerDiseaseState state) {
        DiseaseDef def = DiseaseRegistry.get(diseaseId);
        if (def == null) return -1;
        SymptomConfig symptoms = symptomsOf(def);
        if (symptoms == null || symptoms.persistentEffects().painAmplifier().isEmpty()) return -1;
        if (!state.inRecovery(diseaseId)) return -1;
        return symptoms.persistentEffects().painAmplifier().getAsInt();
    }

    @Nullable
    private static SymptomConfig symptomsOf(@Nullable DiseaseDef def) {
        if (def instanceof ViralDiseaseDef v) return v.symptoms();
        if (def instanceof BacterialDiseaseDef b) return b.symptoms();
        if (def instanceof ComplicationDiseaseDef c) return c.symptoms();
        return null;
    }

    /** Maps a hovered disease tier effect back to its disease registry id. */
    public static Optional<ResourceLocation> diseaseIdForEffect(MobEffect effect) {
        if (effect == DiseaseEffects.MOF.get()) {
            return Optional.of(DiseaseRegistry.MOF_STAPH);
        }
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (key == null || !SimpleDiseases.MOD_ID.equals(key.getNamespace())) {
            return Optional.empty();
        }
        String path = key.getPath();
        for (Severity sev : Severity.values()) {
            String tier = sev.name().toLowerCase(Locale.ROOT);
            for (DiseaseDef def : DiseaseRegistry.all()) {
                String dp = def.id().getPath();
                if (dp.equals("pneumonia") || dp.equals("bronchitis")) {
                    if (path.startsWith(dp + "_") && path.endsWith("_" + tier)) {
                        String middle = path.substring(dp.length() + 1, path.length() - tier.length() - 1);
                        if (!middle.isEmpty()) return Optional.of(def.id());
                    }
                } else if (path.equals(dp + "_" + tier)) {
                    return Optional.of(def.id());
                }
            }
        }
        return Optional.empty();
    }

    /** Inserts {@code rows} under the "When Applied" section of a JEED effect tooltip list. */
    public static void insertWhenAppliedRows(List<Component> list, List<Component> rows) {
        if (rows.isEmpty()) return;
        int insertAt = whenAppliedInsertIndex(list);
        for (Component row : rows) {
            list.add(insertAt++, row);
        }
    }

    private static int whenAppliedInsertIndex(List<Component> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && "potion.whenDrank".equals(tc.getKey())) {
                return i + 1;
            }
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && tc.getKey().startsWith("attribute.modifier.")) {
                return i;
            }
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                String key = tc.getKey();
                if ("jeed.tooltip.harmful".equals(key) || "jeed.tooltip.beneficial".equals(key)
                        || "jeed.tooltip.neutral".equals(key)) {
                    return i + 1;
                }
            }
        }
        return list.size();
    }
}
