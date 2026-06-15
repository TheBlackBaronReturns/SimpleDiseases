package com.theblackbaron.simplediseases.event;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.component.Components;
import com.theblackbaron.simplediseases.status.component.DiseaseInstance;
import com.theblackbaron.simplediseases.status.component.SymptomPoolComponent;
import com.theblackbaron.simplediseases.status.component.TierComponent;
import com.theblackbaron.simplediseases.status.def.ComplicationDiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import com.theblackbaron.simplediseases.status.manager.ContagionManager;
import com.theblackbaron.simplediseases.status.manager.PlayerDiseaseState;
import com.theblackbaron.simplediseases.status.service.SymptomService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CureEvents {
    private static final int TREATMENT_EFFECT_TICKS = 6000;
    private static final double SYMPTOMS_MANAGED_REDUCTION = 0.1;
    private static final double TREATMENT_APPLIED_REDUCTION = 0.5;

    private final ContagionManager contagionManager;

    public CureEvents(ContagionManager contagionManager) {
        this.contagionManager = contagionManager;
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() == Items.HONEY_BOTTLE) {
            event.getToolTip().add(Component.literal("Medicinal Properties").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x663399))));
        }
    }

    @SubscribeEvent
    public void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.wakeImmediately()) return;
        PlayerDiseaseState data = contagionManager.getOrCreate(player);
        boolean any = false;
        for (DiseaseDef def : DiseaseRegistry.viral()) {
            if (treat(player, data, def, 1.0, false, true)) any = true;
        }
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (treat(player, data, def, 1.0, false, true)) any = true;
        }
        if (!any) return;
        player.sendSystemMessage(Component.literal("§7You feel better after resting."));
    }

    @SubscribeEvent
    public void onItemFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var item = event.getItem().getItem();
        if (item == Items.MUSHROOM_STEW || item == Items.BEETROOT_SOUP
                || item == Items.RABBIT_STEW || item == Items.SUSPICIOUS_STEW) {
            applySymptomsManaged(player);
        } else if (item == Items.HONEY_BOTTLE) {
            applyTreatment(player);
        } else {
            return;
        }
    }

    private void applySymptomsManaged(ServerPlayer player) {
        if (player.hasEffect(DiseaseEffects.SYMPTOMS_MANAGED.get())
                || player.hasEffect(DiseaseEffects.TREATMENT_APPLIED.get())) return;
        PlayerDiseaseState data = contagionManager.getOrCreate(player);
        boolean any = false;
        for (DiseaseDef def : DiseaseRegistry.viral()) {
            if (treat(player, data, def, SYMPTOMS_MANAGED_REDUCTION, true, false)) any = true;
        }
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (treat(player, data, def, SYMPTOMS_MANAGED_REDUCTION, true, false)) any = true;
        }
        if (!any) return;
        player.addEffect(new MobEffectInstance(
            DiseaseEffects.SYMPTOMS_MANAGED.get(), TREATMENT_EFFECT_TICKS, 0, false, false, true));
        player.sendSystemMessage(Component.literal("§aThe warm broth eases your symptoms."));
    }

    private void applyTreatment(ServerPlayer player) {
        if (player.hasEffect(DiseaseEffects.TREATMENT_APPLIED.get())) return;
        PlayerDiseaseState data = contagionManager.getOrCreate(player);
        boolean any = false;
        for (DiseaseDef def : DiseaseRegistry.viral()) {
            if (treat(player, data, def, TREATMENT_APPLIED_REDUCTION, true, true)) any = true;
        }
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (treat(player, data, def, TREATMENT_APPLIED_REDUCTION, true, true)) any = true;
        }
        if (!any) return;
        if (player.hasEffect(DiseaseEffects.SYMPTOMS_MANAGED.get())) {
            player.removeEffect(DiseaseEffects.SYMPTOMS_MANAGED.get());
        }
        player.addEffect(new MobEffectInstance(
            DiseaseEffects.TREATMENT_APPLIED.get(), TREATMENT_EFFECT_TICKS, 0, false, false, true));
        player.sendSystemMessage(Component.literal("§aThe honey soothes your throat."));
    }

    // Treatment-driven severity reduction: each rest/medicine has a chance to knock the tier down one
    // step (never below the disease's mildest tier). A successful reduction resets the tug-of-war state,
    // including threshold worsening checks and treatment odds for the lowered tier.
    private static final float  TIER_REDUCE_BASE_CHANCE = 0.35f;
    private static final double TIER_REDUCE_DECAY       = 0.50;

    private static boolean treat(ServerPlayer player, PlayerDiseaseState data, DiseaseDef def,
                                 double reduction, boolean clearSymptoms, boolean reduceSeverity) {
        ResourceLocation id = def.id();
        if (def instanceof ComplicationDiseaseDef && !data.inRecovery(id)) return false;
        if (data.progress(id) < 0.1 && !data.inRecovery(id)) return false;
        data.addProgress(id, -reduction);
        if (reduceSeverity && data.inRecovery(id)) tryReduceTier(player, data, def);
        if (clearSymptoms) clearActiveSymptoms(player, data, def);
        return true;
    }

    private static void tryReduceTier(ServerPlayer player, PlayerDiseaseState data, DiseaseDef def) {
        ResourceLocation id = def.id();
        DiseaseInstance inst = data.peek(id);
        if (inst == null) return;
        TierComponent tier = inst.get(Components.TIER);
        if (tier == null || !tier.rolled()) return;
        SeverityFloor floor = severityFloor(def);
        if (floor == null) return;
        if (tier.severity <= floor.ordinal) return; // already at the mildest tier
        float chance = (float) (TIER_REDUCE_BASE_CHANCE * Math.pow(TIER_REDUCE_DECAY, tier.reductions));
        if (player.getRandom().nextFloat() < chance) {
            tier.severity--;
            tier.reductions = 0;
            tier.worseningChecks = 0;
            tier.worsenings = 0;
            tier.previousWorseningProgress = data.progress(id);
            player.sendSystemMessage(Component.literal("§aYour condition eases."));
        }
    }

    private static void clearActiveSymptoms(ServerPlayer player, PlayerDiseaseState data, DiseaseDef def) {
        ResourceLocation id = def.id();
        DiseaseInstance inst = data.peek(id);
        if (inst == null) return;
        SymptomPoolComponent pool = inst.get(Components.SYMPTOMS);
        if (pool != null && def instanceof ViralDiseaseDef v) {
            SymptomService.clearActive(player, pool, v.symptoms());
        } else if (pool != null && def instanceof ComplicationDiseaseDef c) {
            SymptomService.clearActive(player, pool, c.symptoms());
        }
    }

    private record SeverityFloor(int ordinal) {}

    private static SeverityFloor severityFloor(DiseaseDef def) {
        if (def instanceof ViralDiseaseDef v) return new SeverityFloor(v.lowestSeverity().ordinal());
        if (def instanceof ComplicationDiseaseDef c) return new SeverityFloor(c.lowestSeverity().ordinal());
        return null;
    }
}
