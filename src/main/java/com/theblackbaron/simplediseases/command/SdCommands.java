package com.theblackbaron.simplediseases.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.Severity;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import com.theblackbaron.simplediseases.status.manager.ContagionManager;
import com.theblackbaron.simplediseases.status.manager.FluSeasonManager;
import com.theblackbaron.simplediseases.status.manager.VillagerInfection;
import com.theblackbaron.simplediseases.status.manager.WaterborneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.theblackbaron.simplediseases.status.manager.PlayerDiseaseState;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SdCommands {
    private final ContagionManager contagionManager;
    private final FluSeasonManager fluSeasonManager;
    private final Set<UUID>        debugViralPlayers;
    private final Set<UUID>        debugBacterialPlayers;

    public SdCommands(ContagionManager contagionManager, FluSeasonManager fluSeasonManager,
                      Set<UUID> debugViralPlayers, Set<UUID> debugBacterialPlayers) {
        this.contagionManager      = contagionManager;
        this.fluSeasonManager      = fluSeasonManager;
        this.debugViralPlayers     = debugViralPlayers;
        this.debugBacterialPlayers = debugBacterialPlayers;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("sddebugviral")
                .executes(ctx -> {
                    ServerPlayer player;
                    try { player = ctx.getSource().getPlayerOrException(); }
                    catch (Exception e) { return 0; }
                    UUID id = player.getUUID();
                    if (debugViralPlayers.remove(id)) {
                        player.sendSystemMessage(Component.literal("[SD] Viral debug overlay off."));
                    } else {
                        debugViralPlayers.add(id);
                        player.sendSystemMessage(Component.literal("[SD] Viral debug overlay on. Run again to toggle off."));
                    }
                    return 1;
                })
        );
        dispatcher.register(
            Commands.literal("sddebugbacterial")
                .executes(ctx -> {
                    ServerPlayer player;
                    try { player = ctx.getSource().getPlayerOrException(); }
                    catch (Exception e) { return 0; }
                    UUID id = player.getUUID();
                    if (debugBacterialPlayers.remove(id)) {
                        player.sendSystemMessage(Component.literal("[SD] Bacterial debug overlay off."));
                    } else {
                        debugBacterialPlayers.add(id);
                        player.sendSystemMessage(Component.literal("[SD] Bacterial debug overlay on. Run again to toggle off."));
                    }
                    return 1;
                })
        );
        dispatcher.register(
            Commands.literal("sdfluseason")
                .executes(ctx -> {
                    boolean now = fluSeasonManager.toggleForceFluSeason();
                    ctx.getSource().sendSuccess(
                        () -> Component.literal(now
                            ? "§6[SD] Flu season forced ON.§r"
                            : "§7[SD] Flu season force override OFF.§r"),
                        true
                    );
                    return 1;
                })
        );
        dispatcher.register(
            Commands.literal("sdimmune")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("boost").executes(ctx ->
                    applyImmune(ctx, DiseaseEffects.IMMUNE.get(), "Boosted Immunity")))
                .then(Commands.literal("deficient").executes(ctx ->
                    applyImmune(ctx, DiseaseEffects.IMMUNE_DEFICIENCY.get(), "Immunodeficiency")))
                .then(Commands.literal("clear").executes(this::clearImmune))
        );
        dispatcher.register(
            Commands.literal("sdaccumulate")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("disease", StringArgumentType.word())
                    .suggests((c, b) -> {
                        for (DiseaseDef def : DiseaseRegistry.all()) b.suggest(def.id().getPath());
                        return b.buildFuture();
                    })
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                        .executes(ctx -> accumulate(ctx,
                            StringArgumentType.getString(ctx, "disease"),
                            DoubleArgumentType.getDouble(ctx, "amount")))))
        );
        dispatcher.register(
            Commands.literal("sdinjury")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("flesh_wound")
                    .then(Commands.argument("severity", IntegerArgumentType.integer(0, 2))
                        .executes(ctx -> addFleshWound(ctx, IntegerArgumentType.getInteger(ctx, "severity")))))
        );
        dispatcher.register(
            Commands.literal("sdreservoir")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player;
                    try { player = ctx.getSource().getPlayerOrException(); }
                    catch (Exception e) { return 0; }
                    if (!(player.level() instanceof ServerLevel level)) return 0;
                    BlockPos origin = player.blockPosition();
                    BlockPos target = WaterborneManager.findNearestReservoirWater(
                        level, origin, level.getGameTime(), 48, 256);
                    if (target == null) {
                        player.sendSystemMessage(Component.literal(
                            "§7[SD] No infected reservoir with water found nearby."));
                        return 0;
                    }
                    double dist = Math.sqrt(origin.distSqr(target));
                    player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal(String.format(
                        "§b[SD] Teleported to nearest infected reservoir at §f%d %d %d§b (~%.0fm away).",
                        target.getX(), target.getY(), target.getZ(), dist)));
                    return 1;
                })
        );
        dispatcher.register(
            Commands.literal("sdcheck")
                .executes(ctx -> {
                    ServerPlayer player;
                    try { player = ctx.getSource().getPlayerOrException(); }
                    catch (Exception e) { return 0; }
                    long gameTime = player.level().getGameTime();
                    AABB box = player.getBoundingBox().inflate(16.0);
                    List<Villager> nearby = player.level().getEntitiesOfClass(Villager.class, box, v -> true);
                    if (nearby.isEmpty()) {
                        player.sendSystemMessage(Component.literal("§7[SD] No villagers within 16 blocks."));
                        return 1;
                    }
                    for (Villager villager : nearby) {
                        UUID vid = villager.getUUID();
                        String statusStr = "§7healthy§r";
                        StringBuilder infStr = new StringBuilder();
                        for (DiseaseDef def : DiseaseRegistry.viral()) {
                            ViralDiseaseDef v = (ViralDiseaseDef) def;
                            String path = def.id().getPath();
                            String tag = path.substring(0, Math.min(4, path.length())).toUpperCase();
                            String key = path.substring(0, 1).toUpperCase();
                            // Tier-agnostic: villagers carry a rolled tier variant, not always Moderate.
                            MobEffectInstance eff = v.activeEffect(villager);
                            if (eff != null) {
                                Severity sev = v.activeSeverity(villager);
                                String sevTag = sev != null ? sev.name().substring(0, 3) : "?";
                                statusStr = String.format("§e%s§r§7[§e%s§7]§r §7(%ds left)§r", tag, sevTag, eff.getDuration() / 20);
                            }
                            VillagerInfection inf = contagionManager.getVillagerInfection(def.id(), vid);
                            if (inf != null && inf.immunityUntil > gameTime) {
                                infStr.append(String.format(" §a%sIMM§r§7:§e%ds§r", key, (inf.immunityUntil - gameTime) / 20));
                            } else if (inf != null && inf.exposure > 0) {
                                infStr.append(String.format(" §e%sexp§r§7:§e%d§r", key, inf.exposure));
                            }
                        }
                        double dist = player.distanceTo(villager);
                        player.sendSystemMessage(Component.literal(
                            String.format("§7[SD] Villager §f%.1fm§7: %s%s", dist, statusStr, infStr)
                        ));
                    }
                    return 1;
                })
        );
    }

    /** Applies one immune tier infinite + particle-free (HUD icon kept) for debugging. The two tiers are
     *  mutually exclusive, so the other is cleared first. */
    private int applyImmune(CommandContext<CommandSourceStack> ctx, MobEffect effect, String label) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { return 0; }
        player.removeEffect(DiseaseEffects.IMMUNE.get());
        player.removeEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get());
        // duration -1 = infinite; ambient=false, visible(particles)=false, showIcon=true.
        player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        player.sendSystemMessage(Component.literal("§a[SD] " + label + " applied (infinite, no particles)."));
        return 1;
    }

    /** Debug: add {@code amount} to a disease's accumulation directly, by registry path. Clamps to the
     *  disease's cap. For complications, seeds a source if none so they can latch standalone. */
    private int accumulate(CommandContext<CommandSourceStack> ctx, String diseaseName, double amount) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { return 0; }
        ResourceLocation id = null;
        for (DiseaseDef def : DiseaseRegistry.all()) {
            if (def.id().getPath().equalsIgnoreCase(diseaseName)) { id = def.id(); break; }
        }
        if (id == null) {
            player.sendSystemMessage(Component.literal("§c[SD] Unknown disease: " + diseaseName));
            return 0;
        }
        PlayerDiseaseState state = contagionManager.getOrCreate(player);
        if (id.equals(DiseaseRegistry.PNEUMONIA) && amount > 0) {
            state.debugSetComplicationSource(DiseaseRegistry.PNEUMONIA, DiseaseRegistry.FLU, 24000L);
        } else if (id.equals(DiseaseRegistry.BRONCHITIS) && amount > 0) {
            state.debugSetComplicationSource(DiseaseRegistry.BRONCHITIS, DiseaseRegistry.RSV, 24000L);
        } else if (id.equals(DiseaseRegistry.SEPSIS_STAPH) && amount > 0) {
            state.debugSetComplicationSource(DiseaseRegistry.SEPSIS_STAPH, DiseaseRegistry.CELLULITIS_STAPH, 0L);
        }
        state.addProgress(id, amount);
        player.sendSystemMessage(Component.literal(String.format(
            "§a[SD] %s += %.3f §7(now §e%.3f§7).", id.getPath(), amount, state.progress(id))));
        return 1;
    }

    private int addFleshWound(CommandContext<CommandSourceStack> ctx, int severity) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { return 0; }
        PlayerDiseaseState state = contagionManager.getOrCreate(player);
        state.injury().addFleshWound(severity);
        player.sendSystemMessage(Component.literal(String.format(
            "§a[SD] Added %s flesh wound injury state.", switch (severity) {
                case 2 -> "severe";
                case 1 -> "moderate";
                default -> "mild";
            })));
        return 1;
    }

    private int clearImmune(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try { player = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { return 0; }
        player.removeEffect(DiseaseEffects.IMMUNE.get());
        player.removeEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get());
        player.sendSystemMessage(Component.literal("§7[SD] Immune effects cleared."));
        return 1;
    }
}
