package com.theblackbaron.simplediseases.sound;

import com.theblackbaron.simplediseases.SimpleDiseases;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Custom symptom sound events. Each is backed by an {@code .ogg} under
 * {@code assets/simplediseases/sounds/} (see {@code assets/simplediseases/sounds.json}). Until the
 * audio files are added the events are silent (Forge logs a harmless "missing sound" warning).
 */
public class DiseaseSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SimpleDiseases.MOD_ID);

    public static final RegistryObject<SoundEvent> COUGH               = register("cough");
    public static final RegistryObject<SoundEvent> SNEEZE              = register("sneeze");
    public static final RegistryObject<SoundEvent> VOMIT               = register("vomit");
    public static final RegistryObject<SoundEvent> DIARRHEA            = register("diarrhea");
    public static final RegistryObject<SoundEvent> SHORTNESS_OF_BREATH = register("shortness_of_breath");
    public static final RegistryObject<SoundEvent> STOMACH_CRAMPS      = register("stomach_cramps");
    public static final RegistryObject<SoundEvent> HEARTBEAT       = register("heartbeat");
    public static final RegistryObject<SoundEvent> RAPID_BREATHING = register("rapid_breathing");
    public static final RegistryObject<SoundEvent> WHEEZING_SOUND  = register("wheezing");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SimpleDiseases.MOD_ID, name)));
    }
}
