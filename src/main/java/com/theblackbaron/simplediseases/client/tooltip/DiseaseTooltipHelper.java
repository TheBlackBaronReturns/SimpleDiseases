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
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Builds custom disease tooltip rows from synced player disease state. */
public final class DiseaseTooltipHelper {
    private static final Component SYMPTOMS_HEADER =
            Component.translatable("simplediseases.tooltip.symptoms").withStyle(ChatFormatting.GRAY);

    private DiseaseTooltipHelper() {}

    /** Post-processes a JEED effect tooltip list for disease tier effects. */
    public static void applyDiseaseTooltip(List<Component> list, MobEffectInstance instance, @Nullable Player player) {
        MobEffect effect = instance.getEffect();
        if (!(effect instanceof DiseaseMobEffect) || !isDiseaseTierEffect(effect)) return;

        stripJeedMetadataLines(list);
        removeHiddenAttributeBlock(list);
        insertConditionRow(list, effect);
        applySymptomsSection(list, buildSymptomRows(instance, player));
        stripTrailingBlankLines(list);
    }

    public static boolean isDiseaseTierEffect(MobEffect effect) {
        return diseaseIdForEffect(effect).isPresent();
    }

    /** Symptom rows under the Symptoms header: fever or shock, pain, then active pool symptoms. */
    public static List<Component> buildSymptomRows(MobEffectInstance instance, @Nullable Player player) {
        List<Component> rows = new ArrayList<>();
        MobEffect effect = instance.getEffect();
        if (!(effect instanceof DiseaseMobEffect dme)) return rows;

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

    private static void insertConditionRow(List<Component> list, MobEffect effect) {
        if (list.isEmpty()) return;
        Optional<ResourceLocation> diseaseId = diseaseIdForEffect(effect);
        if (diseaseId.isEmpty()) return;
        DiseaseDef def = DiseaseRegistry.get(diseaseId.get());
        if (def == null) return;
        list.add(1, Component.translatable(def.organGroup().langKey()).withStyle(ChatFormatting.GRAY));
    }

    /** Removes JEED effect-color and category rows (harmful / beneficial / neutral). */
    private static void stripJeedMetadataLines(List<Component> list) {
        list.removeIf(line -> {
            if (!(line.getContents() instanceof TranslatableContents tc)) return false;
            return switch (tc.getKey()) {
                case "jeed.tooltip.color_complete",
                     "jeed.tooltip.harmful",
                     "jeed.tooltip.beneficial",
                     "jeed.tooltip.neutral" -> true;
                default -> false;
            };
        });
    }

    /**
     * Removes JEED's hidden max-health attribute block: the blank spacer, {@code potion.whenDrank},
     * and all attribute modifier lines. Tier diseases only register fever/shock max-health modifiers.
     */
    private static void removeHiddenAttributeBlock(List<Component> list) {
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i).getContents() instanceof TranslatableContents tc)) continue;
            if (!"potion.whenDrank".equals(tc.getKey())) continue;

            if (i > 0 && isBlankTooltipLine(list.get(i - 1))) {
                list.remove(i - 1);
                i--;
            }

            list.remove(i);

            while (i < list.size() && isAttributeModifierLine(list.get(i))) {
                list.remove(i);
            }
            return;
        }

        list.removeIf(DiseaseTooltipHelper::isAttributeModifierLine);
    }

    private static void stripTrailingBlankLines(List<Component> list) {
        while (!list.isEmpty() && isBlankTooltipLine(list.get(list.size() - 1))) {
            list.remove(list.size() - 1);
        }
    }

    private static boolean isBlankTooltipLine(Component line) {
        return line.getString().isBlank();
    }

    private static void applySymptomsSection(List<Component> list, List<Component> rows) {
        if (rows.isEmpty()) return;

        int insertAt = indexOfWhenAppliedHeader(list);
        if (insertAt >= 0) {
            list.remove(insertAt);
        } else {
            insertAt = symptomsInsertIndex(list);
        }

        if (insertAt > 0 && isBlankTooltipLine(list.get(insertAt - 1))) {
            list.add(insertAt, SYMPTOMS_HEADER);
            insertRows(list, insertAt + 1, rows);
        } else {
            list.add(insertAt, Component.empty());
            list.add(insertAt + 1, SYMPTOMS_HEADER);
            insertRows(list, insertAt + 2, rows);
        }
    }

    private static void insertRows(List<Component> list, int index, List<Component> rows) {
        for (int i = 0; i < rows.size(); i++) {
            list.add(index + i, rows.get(i));
        }
    }

    private static int indexOfWhenAppliedHeader(List<Component> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getContents() instanceof TranslatableContents tc
                    && "potion.whenDrank".equals(tc.getKey())) {
                return i;
            }
        }
        return -1;
    }

    private static int symptomsInsertIndex(List<Component> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getContents() instanceof TranslatableContents tc
                    && tc.getKey().startsWith("attribute.modifier.")) {
                return i;
            }
        }
        return list.size();
    }

    private static boolean isAttributeModifierLine(Component line) {
        return line.getContents() instanceof TranslatableContents tc
                && tc.getKey().startsWith("attribute.modifier.");
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
        if (symptoms == null || symptoms.persistentEffects().painProfile().isEmpty()) return -1;
        if (!state.inRecovery(diseaseId)) return -1;
        return symptoms.persistentEffects().painProfile().get().amplifierFor(state.tierOf(diseaseId));
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
}
