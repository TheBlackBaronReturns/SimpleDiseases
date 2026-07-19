package com.theblackbaron.simplediseases.compat;

import com.momosoftworks.coldsweat.api.event.core.registry.TempModifierRegisterEvent;
import com.momosoftworks.coldsweat.api.registry.TempModifierRegistry;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.placement.Matcher;
import com.momosoftworks.coldsweat.api.util.placement.Placement;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public class ColdSweatCompat {

    public static final boolean LOADED = ModList.get().isLoaded("cold_sweat");

    public static final double SUPPRESSED_RECOVERY_MIN = 0.25;

    private static final ResourceLocation FILLED_WATERSKIN_ID = new ResourceLocation("cold_sweat", "filled_waterskin");
    private static final ResourceLocation[] GOAT_FUR_ARMOR_IDS = {
        new ResourceLocation("cold_sweat", "goat_fur_helmet"),
        new ResourceLocation("cold_sweat", "goat_fur_chestplate"),
        new ResourceLocation("cold_sweat", "goat_fur_leggings"),
        new ResourceLocation("cold_sweat", "goat_fur_boots"),
    };
    private static final String WATERSKIN_TEMP_KEY      = "Temperature";
    private static final String WATERSKIN_TEMP_KEY_ALT  = "temperature";
    private static final String WATERSKIN_TEMP_KEY_ALT2 = "temp";
    // World-temp bonus a carried hot waterskin adds to drying / recovery warmth calcs.
    private static final double WATERSKIN_DRY_HEAT = 1.0;
    // Minimum objective WORLD warmth for recovery (MC 0–2 scale; plains ~0.8).
    // Viral / respiratory: higher floor — colds and flu need shelter/warmth.
    // With FEVER_LIGHT (+0.05) threshold = 0.80 — passable in plains; mild+ needs extra warmth.
    private static final double MIN_WORLD_TEMP_TO_RECOVER        = 0.75;
    // Bacterial (cellulitis cap-recovery): lower floor — localized wound infections can heal in cold.
    private static final double MIN_WORLD_TEMP_TO_RECOVER_BACTERIAL = 0.60;
    // Hot armor insulation (CS units) → MC WORLD-scale recovery bonus.
    private static final double INSULATION_TO_WORLD = 0.01;
    // Fallback without CS: warmth bonus per leather or goat-fur armor piece.
    private static final double ARMOR_PIECE_WARMTH = 0.08;
    // Accumulation gates (gate-only — rates are unaffected):
    private static final double DAMP_COLD_THRESHOLD        = 1.0;
    private static final double BODY_TEMP_ACCUMULATE_BELOW = 0.0;
    private static final double WINDCHILL_CHILL            = 10.0;

    private static Item filledWaterskinItem;
    private static Set<Item> goatFurItems = Set.of();
    private static boolean itemsResolved;

    private static void resolveItems() {
        if (itemsResolved) return;
        Item filled = ForgeRegistries.ITEMS.getValue(FILLED_WATERSKIN_ID);
        filledWaterskinItem = (filled == null || filled == Items.AIR) ? null : filled;
        Set<Item> set = new HashSet<>();
        for (ResourceLocation id : GOAT_FUR_ARMOR_IDS) {
            Item it = ForgeRegistries.ITEMS.getValue(id);
            if (it != null && it != Items.AIR) set.add(it);
        }
        goatFurItems = set;
        itemsResolved = true;
    }

    public static boolean hasHotWaterskin(ServerPlayer player) {
        if (!LOADED) return false;
        resolveItems();
        Item filled = filledWaterskinItem;
        if (filled == null) return false;
        for (ItemStack stack : player.getInventory().items) {
            if (isHotWaterskin(stack, filled)) return true;
        }
        return isHotWaterskin(player.getOffhandItem(), filled);
    }

    private static boolean isHotWaterskin(ItemStack stack, Item filledWaterskin) {
        if (!stack.is(filledWaterskin)) return false;
        return getWaterskinTemp(stack) > 0.0;
    }

    private static double getWaterskinTemp(ItemStack stack) {
        if (!stack.hasTag()) return 0.0;
        var tag = stack.getTag();
        if (tag.contains(WATERSKIN_TEMP_KEY)) return tag.getDouble(WATERSKIN_TEMP_KEY);
        if (tag.contains(WATERSKIN_TEMP_KEY_ALT)) return tag.getDouble(WATERSKIN_TEMP_KEY_ALT);
        return tag.getDouble(WATERSKIN_TEMP_KEY_ALT2);
    }

    public static double getHotWaterskinTemp(ServerPlayer player) {
        if (!LOADED) return 0.0;
        resolveItems();
        Item filled = filledWaterskinItem;
        if (filled == null) return 0.0;
        double max = 0.0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(filled)) {
                double temp = getWaterskinTemp(stack);
                if (temp > max) max = temp;
            }
        }
        double offhand = getWaterskinTemp(player.getOffhandItem());
        return Math.max(max, player.getOffhandItem().is(filled) ? offhand : 0.0);
    }

    public static int goatFurArmorPieces(ServerPlayer player) {
        if (!LOADED) return 0;
        resolveItems();
        if (goatFurItems.isEmpty()) return 0;
        int count = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (goatFurItems.contains(stack.getItem())) count++;
        }
        return count;
    }

    public static double getWorldTemp(ServerPlayer player) {
        if (LOADED) {
            return WorldHelper.getRoughTemperatureAt(player.level(), player.blockPosition(), 1);
        }
        double temp = player.level().getBiome(player.blockPosition()).value().getBaseTemperature();
        if (player.level().isRainingAt(player.blockPosition())) temp -= 0.2;
        return temp;
    }

    public static double getDryRate(ServerPlayer player) {
        double temp;
        if (LOADED) {
            temp = Temperature.get(player, Temperature.Trait.WORLD);
        } else {
            int blockLight = player.level().getBrightness(LightLayer.BLOCK, player.blockPosition());
            temp = getWorldTemp(player) + blockLight / 15.0;
        }
        if (hasHotWaterskin(player)) temp += WATERSKIN_DRY_HEAT;
        return Math.max(0.00015, temp * 0.0015);
    }

    public static double getColdRate(double worldTemp) {
        return Math.max(0.0, 1.0 - worldTemp) * 0.000300;
    }

    /**
     * Objective environmental warmth for recovery — ambient WORLD (minus disease perception
     * modifiers) plus insulation and hot-waterskin bonuses. Does not read BODY temperature.
     */
    public static double getObjectiveRecoveryWarmth(ServerPlayer player) {
        if (LOADED) {
            double world = Temperature.get(player, Temperature.Trait.WORLD);
            world -= DiseaseWorldTempHelper.perceptionOffset(player);
            world += getInsulationWarmthBonus(player);
            world += getHotWaterskinRecoveryBonus(player);
            return world;
        }
        int blockLight = player.level().getBrightness(LightLayer.BLOCK, player.blockPosition());
        double warmth = getWorldTemp(player) + blockLight / 15.0;
        warmth += getInsulationWarmthBonus(player);
        warmth += getHotWaterskinRecoveryBonus(player);
        return warmth;
    }

    private static double getInsulationWarmthBonus(ServerPlayer player) {
        if (LOADED) {
            return DiseaseWorldTempHelper.readHotInsulation(player) * INSULATION_TO_WORLD;
        }
        return (leatherArmorPieces(player) + goatFurArmorPieces(player)) * ARMOR_PIECE_WARMTH;
    }

    private static int leatherArmorPieces(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE)
                    || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS)) count++;
        }
        return count;
    }

    private static double getHotWaterskinRecoveryBonus(ServerPlayer player) {
        if (!hasHotWaterskin(player)) return 0.0;
        if (LOADED) {
            return Math.min(WATERSKIN_DRY_HEAT, getHotWaterskinTemp(player) * 0.01);
        }
        return WATERSKIN_DRY_HEAT;
    }

    /**
     * Passive recovery rate multiplier for diseases of the given pathogen type.
     * Returns {@code 0.0} while environmental damp/wind is adding viral progress this tick.
     */
    public static double getRecoveryMultiplier(ServerPlayer player, String pathogenType,
                                               boolean environmentalAccumulating) {
        if (environmentalAccumulating) return 0.0;
        double minWarmth = minRecoveryWarmth(pathogenType);
        double threshold = minWarmth + feverOffset(player);
        double warmth = getObjectiveRecoveryWarmth(player);
        if (warmth >= threshold) return 1.0;
        if (warmth <= minWarmth) return SUPPRESSED_RECOVERY_MIN;
        double span = threshold - minWarmth;
        if (span <= 0.0) return SUPPRESSED_RECOVERY_MIN;
        double t = (warmth - minWarmth) / span;
        return SUPPRESSED_RECOVERY_MIN + (1.0 - SUPPRESSED_RECOVERY_MIN) * t;
    }

    private static double minRecoveryWarmth(String pathogenType) {
        return DiseaseRegistry.GROUP_BACTERIAL.equals(pathogenType)
                ? MIN_WORLD_TEMP_TO_RECOVER_BACTERIAL : MIN_WORLD_TEMP_TO_RECOVER;
    }

    /**
     * Whether the player is warm enough for diseases of the given pathogen type to passively recover.
     * Viral uses {@link #MIN_WORLD_TEMP_TO_RECOVER}; bacterial uses {@link #MIN_WORLD_TEMP_TO_RECOVER_BACTERIAL}.
     * Both apply full fever offset on top of their respective base.
     */
    public static boolean isWarmEnoughForRecovery(ServerPlayer player, String pathogenType) {
        return getRecoveryMultiplier(player, pathogenType, false) >= 1.0;
    }

    /**
     * Whether the player is warm enough for a respiratory illness to recover. Gates on objective
     * WORLD warmth plus insulation/waterskin; fever raises the threshold without requiring elevated BODY.
     */
    public static boolean isWarmEnoughToRecover(ServerPlayer player) {
        return isWarmEnoughForRecovery(player, DiseaseRegistry.GROUP_VIRAL);
    }

    private static final ResourceLocation FEVER_WORLD_MODIFIER_ID =
            new ResourceLocation(SimpleDiseases.MOD_ID, "fever_world");
    private static final ResourceLocation SEPTIC_SHOCK_MODIFIER_ID =
            new ResourceLocation(SimpleDiseases.MOD_ID, "septic_shock");
    private static final Placement DISEASE_WORLD_PLACEMENT =
            Placement.LAST.noDuplicates(Matcher.SAME_CLASS);

    /**
     * Ensures fever (+) and septic-shock (−) WORLD perception modifiers are present while active,
     * and removed when cured. No-op without Cold Sweat.
     */
    public static void syncDiseaseWorldModifiers(ServerPlayer player) {
        if (!LOADED) return;
        boolean hasFever = FeverWorldTempModifier.maxFeverOffset(player) > 0.0;
        boolean inShock = DiseaseEffects.hasSepticShock(player);
        boolean hasFeverMod = Temperature.hasModifier(player, Temperature.Trait.WORLD, FeverWorldTempModifier.class);
        boolean hasShockMod = Temperature.hasModifier(player, Temperature.Trait.WORLD, SepticShockTempModifier.class);

        if (hasFever && !hasFeverMod) {
            Temperature.addModifier(player, createFeverWorldModifier(), Temperature.Trait.WORLD, DISEASE_WORLD_PLACEMENT);
        } else if (!hasFever && hasFeverMod) {
            Temperature.removeModifiers(player, Temperature.Trait.WORLD, FeverWorldTempModifier.class);
        }

        if (inShock && !hasShockMod) {
            Temperature.addModifier(player, createSepticShockModifier(), Temperature.Trait.WORLD, DISEASE_WORLD_PLACEMENT);
        } else if (!inShock && hasShockMod) {
            Temperature.removeModifiers(player, Temperature.Trait.WORLD, SepticShockTempModifier.class);
        }
    }

    private static FeverWorldTempModifier createFeverWorldModifier() {
        return TempModifierRegistry.getValue(FEVER_WORLD_MODIFIER_ID)
                .map(m -> (FeverWorldTempModifier) m)
                .orElseGet(FeverWorldTempModifier::new);
    }

    private static SepticShockTempModifier createSepticShockModifier() {
        return TempModifierRegistry.getValue(SEPTIC_SHOCK_MODIFIER_ID)
                .map(m -> (SepticShockTempModifier) m)
                .orElseGet(SepticShockTempModifier::new);
    }

    /** Runs before CS {@code tickTemperature} so disease WORLD modifiers are present for that tick. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingTickSyncDiseaseWorld(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        syncDiseaseWorldModifiers(player);
    }

    @SubscribeEvent
    public static void onTempModifierRegister(TempModifierRegisterEvent event) {
        event.register(FEVER_WORLD_MODIFIER_ID, FeverWorldTempModifier::new);
        event.register(SEPTIC_SHOCK_MODIFIER_ID, SepticShockTempModifier::new);
    }

    /**
     * Whether the player is warm enough for a bacterial infection to recover. Uses the lower
     * {@link #MIN_WORLD_TEMP_TO_RECOVER_BACTERIAL} floor with full fever offset.
     */
    public static boolean isWarmEnoughForBacterialRecovery(ServerPlayer player) {
        return isWarmEnoughForRecovery(player, DiseaseRegistry.GROUP_BACTERIAL);
    }

    private static double feverOffset(ServerPlayer player) {
        double max = 0.0;
        for (MobEffectInstance inst : player.getActiveEffects()) {
            if (inst.getEffect() instanceof DiseaseMobEffect dme) {
                double offset = dme.getFeverOffset();
                if (offset > max) max = offset;
            }
        }
        return max;
    }

    public static boolean isColdEnoughForDamp(ServerPlayer player) {
        if (LOADED) {
            return Temperature.get(player, Temperature.Trait.WORLD) < DAMP_COLD_THRESHOLD;
        }
        double ambient = getWorldTemp(player) + player.level().getBrightness(LightLayer.BLOCK, player.blockPosition()) / 15.0;
        if (hasHotWaterskin(player)) ambient += WATERSKIN_DRY_HEAT;
        return ambient < DAMP_COLD_THRESHOLD;
    }

    public static boolean isColdEnoughForWindchill(ServerPlayer player) {
        if (!LOADED) return true;
        return Temperature.get(player, Temperature.Trait.BODY) - WINDCHILL_CHILL < BODY_TEMP_ACCUMULATE_BELOW;
    }
}
