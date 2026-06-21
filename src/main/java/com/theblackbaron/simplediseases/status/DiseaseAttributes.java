package com.theblackbaron.simplediseases.status;

import com.theblackbaron.simplediseases.SimpleDiseases;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class DiseaseAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, SimpleDiseases.MOD_ID);

    /**
     * Maximum saturation the player can accumulate. Default 5.0 (vanilla natural max).
     * Reserved for symptom/disease hooks; capped by a tick handler in SymptomEvents.
     */
    public static final RegistryObject<Attribute> MAX_SATURATION = ATTRIBUTES.register(
            "disease_max_saturation",
            () -> new RangedAttribute("attribute.simplediseases.disease_max_saturation", 5.0, 0.0, 20.0)
                    .setSyncable(true));

    /**
     * Multiplied against the knockback strength the player deals on hit.
     * Default 1.0; pain applies MULTIPLY_TOTAL toward 0 via SymptomEvents.
     */
    public static final RegistryObject<Attribute> KNOCKBACK_FACTOR = ATTRIBUTES.register(
            "disease_knockback_factor",
            () -> new RangedAttribute("attribute.simplediseases.disease_knockback_factor", 1.0, 0.0, 2.0)
                    .setSyncable(true));

    /**
     * Multiplied against the player's block-break speed (ForgeMod.BLOCK_BREAK_SPEED was added after
     * 1.20.1; this custom attribute fills the same role). Default 1.0; pain applies
     * MULTIPLY_TOTAL toward 0. Applied in SymptomEvents via PlayerEvent.BreakSpeed.
     */
    public static final RegistryObject<Attribute> BLOCK_BREAK_SPEED = ATTRIBUTES.register(
            "disease_block_break_speed",
            () -> new RangedAttribute("attribute.simplediseases.disease_block_break_speed", 1.0, 0.0, 2.0)
                    .setSyncable(true));

    /**
     * Multiplied against jump velocity on {@code LivingJumpEvent}. Default 1.0; malaise applies
     * MULTIPLY_TOTAL toward 0. 1.20.1 has no player {@code JUMP_STRENGTH} attribute.
     */
    public static final RegistryObject<Attribute> JUMP_FACTOR = ATTRIBUTES.register(
            "disease_jump_factor",
            () -> new RangedAttribute("attribute.simplediseases.disease_jump_factor", 1.0, 0.0, 2.0)
                    .setSyncable(true));

    private DiseaseAttributes() {}

    public static void onAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, MAX_SATURATION.get());
        event.add(EntityType.PLAYER, KNOCKBACK_FACTOR.get());
        event.add(EntityType.PLAYER, BLOCK_BREAK_SPEED.get());
        event.add(EntityType.PLAYER, JUMP_FACTOR.get());
    }
}
