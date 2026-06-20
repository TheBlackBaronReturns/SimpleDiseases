package com.theblackbaron.simplediseases.event;

import com.theblackbaron.simplediseases.status.DiseaseAttributes;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Symptom-driven interactions keyed on a symptom MobEffect's presence (not tied to a specific
 * disease, so it works for any illness whose pool includes the symptom).
 */
public class SymptomEvents {

    /** Half a heart of magic damage per hunger point restored by the meal. */
    private static final float SORE_THROAT_DAMAGE_PER_NUTRITION = 0.5f;

    /**
     * Sore Throat punishes swallowing: eating still works, but larger meals deal more throat pain
     * once the bite is finished (after hunger is applied).
     */
    @SubscribeEvent
    public void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasEffect(DiseaseEffects.SORE_THROAT.get())) return;
        if (!(entity instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        ItemStack stack = event.getItem();
        if (!stack.getItem().isEdible()) return;
        FoodProperties food = stack.getItem().getFoodProperties(stack, player);
        if (food == null) return;

        int nutrition = food.getNutrition();
        if (nutrition <= 0) return;

        float damage = nutrition * SORE_THROAT_DAMAGE_PER_NUTRITION;
        if (damage > 0.0f) {
            player.hurt(player.damageSources().magic(), damage);
        }
        player.displayClientMessage(
                Component.translatable("message.simplediseases.sore_throat_eat"), true);
    }

    /**
     * Stomach Cramps stops hunger-based health regeneration for the symptom's whole duration. Natural
     * regen heals in small (<=1.0) increments and the player carries no Regeneration effect; block only
     * that, so healing potions, golden-apple Regeneration, and other deliberate heals still work.
     */
    @SubscribeEvent
    public void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasEffect(DiseaseEffects.STOMACH_CRAMPS.get())) return;
        if (event.getAmount() <= 1.0F && !entity.hasEffect(MobEffects.REGENERATION)) {
            event.setCanceled(true);
        }
    }

    /**
     * Tachypnea on land: sprint drains air through Forge's breathe hook (drowning at -20 like vanilla).
     * Suppresses automatic land air refill so sprint drain is not undone every tick.
     */
    @SubscribeEvent
    public void onLivingBreathe(LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!DiseaseEffects.hasTachypnea(player)) return;

        if (player.isEyeInFluid(FluidTags.WATER)) {
            event.setConsumeAirAmount(event.getConsumeAirAmount() + 1);
            return;
        }

        if (player.isSprinting()) {
            event.setCanBreathe(false);
            event.setConsumeAirAmount(6);
            return;
        }

        event.setCanRefillAir(true);
        event.setRefillAirAmount(4);
    }

    /**
     * Pain tier 3 (amplifier >= 2) blocks sleep.
     */
    @SubscribeEvent
    public void onSleepAttempt(PlayerSleepInBedEvent event) {
        MobEffectInstance pain = event.getEntity().getEffect(DiseaseEffects.PAIN.get());
        if (pain == null || pain.getAmplifier() < 2) return;
        event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.simplediseases.pain_no_sleep"), true);
        }
    }

    /**
     * Caps the player's saturation at the DISEASE_MAX_SATURATION attribute value (applied by norovirus).
     * Default is 5.0 (no active disease); norovirus reduces it via ADDITION modifiers so the player
     * cannot retain as much nutrition.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (event.player.level().isClientSide()) return;
        AttributeInstance attr = event.player.getAttribute(DiseaseAttributes.MAX_SATURATION.get());
        if (attr == null) return;
        float cap = (float) attr.getValue();
        if (cap >= 5.0f) return; // default, no active norovirus cap
        FoodData food = event.player.getFoodData();
        if (food.getSaturationLevel() > cap) food.setSaturation(cap);
    }

    /**
     * Applies DISEASE_BLOCK_BREAK_SPEED: scales block dig speed by the attribute value.
     * ForgeMod.BLOCK_BREAK_SPEED was added after 1.20.1, so this custom attribute + event handler
     * fills the same role.
     */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        AttributeInstance attr = event.getEntity().getAttribute(DiseaseAttributes.BLOCK_BREAK_SPEED.get());
        if (attr == null) return;
        double factor = attr.getValue();
        if (factor >= 1.0) return;
        event.setNewSpeed(event.getNewSpeed() * (float) factor);
    }

    /**
     * Scales knockback dealt by a player whose DISEASE_KNOCKBACK_FACTOR attribute is below 1.0
     * (applied by cellulitis). Reads the attacker from the target's lastHurtByMob, which is set
     * in LivingEntity.hurt() before knockback fires in the same call stack.
     */
    @SubscribeEvent
    public void onKnockback(LivingKnockBackEvent event) {
        LivingEntity attacker = event.getEntity().getLastHurtByMob();
        if (attacker == null) return;
        AttributeInstance attr = attacker.getAttribute(DiseaseAttributes.KNOCKBACK_FACTOR.get());
        if (attr == null) return;
        double factor = attr.getValue();
        if (factor >= 1.0) return;
        event.setStrength(event.getStrength() * (float) factor);
    }

    /**
     * Applies DISEASE_JUMP_FACTOR: scales jump velocity by the attribute value. 1.20.1 has no player
     * jump-strength attribute, so malaise debuffs jump via this custom factor on LivingJumpEvent.
     */
    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        AttributeInstance attr = entity.getAttribute(DiseaseAttributes.JUMP_FACTOR.get());
        if (attr == null) return;
        double factor = attr.getValue();
        if (factor >= 1.0) return;
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, factor, 1.0));
    }
}
