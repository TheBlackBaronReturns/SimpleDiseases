package com.theblackbaron.simplediseases;

import com.theblackbaron.simplediseases.command.SdCommands;
import com.theblackbaron.simplediseases.event.CureEvents;
import com.theblackbaron.simplediseases.event.DiseaseEvents;
import com.theblackbaron.simplediseases.event.SymptomEvents;
import com.theblackbaron.simplediseases.particle.DiseaseParticles;
import com.theblackbaron.simplediseases.sound.DiseaseSounds;
import com.theblackbaron.simplediseases.status.DiseaseAttributes;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SimpleDiseases.MOD_ID)
public class SimpleDiseases {

    public static final String MOD_ID = "simplediseases";

    public SimpleDiseases() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Register custom attributes BEFORE DiseaseEffects (which references them in lambdas).
        DiseaseAttributes.ATTRIBUTES.register(modBus);
        modBus.addListener(DiseaseAttributes::onAttributeModification);
        DiseaseEffects.EFFECTS.register(modBus);
        DiseaseParticles.PARTICLES.register(modBus);
        DiseaseSounds.SOUNDS.register(modBus);

        // Build the built-in disease definitions (cold + flu as viral rows). Effects are referenced
        // lazily, so this is safe before registries populate.
        DiseaseRegistry.bootstrap();

        DiseaseEvents events = new DiseaseEvents();
        MinecraftForge.EVENT_BUS.register(events);
        MinecraftForge.EVENT_BUS.register(new SdCommands(events.getContagionManager(), events.getFluSeasonManager(),
                events.getDebugViralPlayers(), events.getDebugBacterialPlayers()));
        MinecraftForge.EVENT_BUS.register(new CureEvents(events.getContagionManager()));
        MinecraftForge.EVENT_BUS.register(new SymptomEvents());
    }
}
