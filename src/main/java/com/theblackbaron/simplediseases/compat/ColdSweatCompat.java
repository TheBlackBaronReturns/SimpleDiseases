package com.theblackbaron.simplediseases.compat;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public class ColdSweatCompat {

    public static final boolean LOADED = ModList.get().isLoaded("cold_sweat");

    // Cold Sweat item ids/NBT (confirmed against ColdSweat-2.4.jar). Resolved to Item references
    // once and cached (see resolveItems) so the per-tick hot path does NO registry lookups — these
    // run every tick for every player, so this matters at high player counts.
    private static final ResourceLocation FILLED_WATERSKIN_ID = new ResourceLocation("cold_sweat", "filled_waterskin");
    private static final ResourceLocation[] GOAT_FUR_ARMOR_IDS = {
        new ResourceLocation("cold_sweat", "goat_fur_helmet"),
        new ResourceLocation("cold_sweat", "goat_fur_chestplate"),
        new ResourceLocation("cold_sweat", "goat_fur_leggings"),
        new ResourceLocation("cold_sweat", "goat_fur_boots"),
    };
    private static final String WATERSKIN_TEMP_KEY     = "temperature"; // double; >0 = hot
    private static final String WATERSKIN_TEMP_KEY_ALT = "temp";        // defensive fallback
    // World-temp bonus a carried hot waterskin adds to the drying calc (≈ a small heat source).
    private static final double WATERSKIN_DRY_HEAT     = 1.0;
    // Minimum Cold Sweat BODY temperature (−100…+100 scale, 0 = comfortable) for a respiratory illness
    // to recover. At/above this the player counts as "warm enough" — so a fire/insulation that warms the
    // body lets a cold recover even in a frozen biome. Lower this (toward negative) to allow recovery
    // while still mildly cold; raise it to require the player be actively warm.
    private static final double MIN_BODY_TEMP_TO_RECOVER = 0.0;
    // Accumulation gates (gate-only — the accumulation RATE is computed separately and is unaffected):
    //  • Damp path: wet clothing provides NO insulation, so the gate ignores body temp/insulation entirely
    //    and looks only at the ambient the player is exposed to — biome/season temp plus real heat sources
    //    (nearby fire/torch/lava via block light, and a carried hot waterskin). Below DAMP_COLD_THRESHOLD
    //    (matches getColdRate's nonzero band) the damp player is cold enough to accumulate.
    //  • Windchill path: clothes are DRY here, so insulation still protects — gate on the player's CS BODY
    //    temperature (which includes insulation) minus a windchill chill, below the comfort point.
    private static final double DAMP_COLD_THRESHOLD        = 1.0; // ambient (worldTemp scale) below this = cold
    private static final double BODY_TEMP_ACCUMULATE_BELOW = 0.0; // CS BODY scale, 0 = comfortable
    private static final double WINDCHILL_CHILL            = 10.0; // body-temp chill while in windchill

    // Lazily resolved (after registries load) then cached; null/empty if absent or ids missing.
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

    /** True if the player is carrying a Cold Sweat Filled Waterskin holding a hot (positive) temperature. */
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
        if (!stack.is(filledWaterskin) || !stack.hasTag()) return false;
        var tag = stack.getTag();
        double temp = tag.contains(WATERSKIN_TEMP_KEY) ? tag.getDouble(WATERSKIN_TEMP_KEY)
                    : tag.getDouble(WATERSKIN_TEMP_KEY_ALT);
        return temp > 0.0;
    }

    /** Number of worn Cold Sweat goat-fur armor pieces (0–4). 0 without Cold Sweat. */
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

    /**
     * Ambient world temperature in CS MC units — drives cold rate, drying, and recovery.
     * Calibrated for the 0–2 biome scale (plains ~0.8, cold biome ~0.2).
     */
    public static double getWorldTemp(ServerPlayer player) {
        if (LOADED) {
            return WorldHelper.getRoughTemperatureAt(player.level(), player.blockPosition(), 1);
        }
        double temp = player.level().getBiome(player.blockPosition()).value().getBaseTemperature();
        if (player.level().isRainingAt(player.blockPosition())) temp -= 0.2;
        return temp;
    }

    /**
     * Drying rate. Driven by the surrounding warmth (same world-temp scale): temp = 1.0 → 0.0015/tick
     * (~11 min); 5.0 (campfire) → 0.0075 (~2.2 min); 10.0 (lava) → 0.015 (~1.1 min). Floors at
     * 0.00015/tick so cold conditions still dry, slowly.
     *
     * <p>With Cold Sweat the warmth read is the full WORLD temperature ({@code Trait.WORLD}) — which folds
     * in real heat sources (campfire/lava/furnace via {@code BlockTemp}) and excludes the player's
     * insulation, so a fire dries you faster but wet armor does NOT (consistent with
     * {@link #isColdEnoughForDamp}; the {@code worldTemp} arg = {@code getRoughTemperatureAt} omits block
     * heat, which is why we read WORLD here instead). Without Cold Sweat there is no such model, so we fall
     * back to the passed rough temp plus a block-light heat proxy. A carried hot waterskin adds heat either
     * way.
     */
    public static double getDryRate(ServerPlayer player) {
        double temp;
        if (LOADED) {
            temp = Temperature.get(player, Temperature.Trait.WORLD);
        } else {
            int blockLight = player.level().getBrightness(LightLayer.BLOCK, player.blockPosition());
            temp = getWorldTemp(player) + blockLight / 15.0;
        }
        // A carried hot Filled Waterskin acts as a portable heat source, drying the player faster.
        if (hasHotWaterskin(player)) temp += WATERSKIN_DRY_HEAT;
        return Math.max(0.00015, temp * 0.0015);
    }

    /**
     * Cold accumulation rate while wet.
     * worldTemp = 0.8 (plains) → 0.00006/tick (~14 min to Cold when Damp);
     * 0.0 (cold biome) → 0.00030 (~2.8 min); -0.5 (snow) → 0.00045 (~1.8 min).
     */
    public static double getColdRate(double worldTemp) {
        return Math.max(0.0, 1.0 - worldTemp) * 0.000300;
    }

    /**
     * Whether the player is warm enough for a respiratory illness to recover this tick. With Cold Sweat
     * this reads the player's full BODY temperature (−100…+100, 0 = comfortable), so anything that warms
     * the player — a campfire, insulation, a hot waterskin — lets a cold recover even in a frozen biome,
     * not just the raw biome ambient. Without Cold Sweat it falls back to the biome temp plus a block-
     * light heat bonus (a nearby torch/campfire/lava raises it above freezing). This ONLY gates recovery
     * on/off; the recovery RATE is a flat per-disease constant and is unaffected by how warm the player is.
     */
    public static boolean isWarmEnoughToRecover(ServerPlayer player) {
        double threshold = MIN_BODY_TEMP_TO_RECOVER + feverOffset(player);
        if (LOADED) {
            return Temperature.get(player, Temperature.Trait.BODY) >= threshold;
        }
        int blockLight = player.level().getBrightness(LightLayer.BLOCK, player.blockPosition());
        // Non-CS fallback: scale fever offset into the biome+light score (CS BODY ±100 ≈ biome 0–2).
        return getWorldTemp(player) + blockLight / 15.0 > threshold / 50.0;
    }

    // Septic shock pushes CS BODY toward -SEPTIC_SHOCK_STRENGTH at this rate per tick.
    // At 1.0/tick a plains player (BODY ≈ 0) reaches -55 in ~55 ticks (≈3 seconds).
    private static final double SEPTIC_SHOCK_RATE = 1.0;

    /**
     * Pushes the player's CS BODY temperature toward -SEPTIC_SHOCK_STRENGTH each tick,
     * simulating distributive vasodilation and cooling in septic shock. At SEPTIC_SHOCK_RATE
     * units/tick a plains player reaches near-hypothermia in ~55 ticks (≈3 seconds).
     * No-op without Cold Sweat.
     */
    public static void applySepticShock(ServerPlayer player) {
        if (!LOADED) return;
        double current = Temperature.get(player, Temperature.Trait.BODY);
        double target  = -DiseaseMobEffect.SEPTIC_SHOCK_STRENGTH;
        if (current > target) {
            Temperature.add(player, Temperature.Trait.BASE,
                    Math.max(target - current, -SEPTIC_SHOCK_RATE));
        }
    }

    // Bacterial recovery uses a fraction of the disease's fever offset as the warmth gate,
    // keeping the requirement lower than viral recovery while still making severity matter.
    private static final double BACTERIAL_FEVER_GATE_SCALE = 0.5;

    /**
     * Whether the player is warm enough for a bacterial infection to recover. Like
     * {@link #isWarmEnoughToRecover} but the fever offset is scaled by
     * {@code BACTERIAL_FEVER_GATE_SCALE}, so recovery is possible at a lower body temperature
     * than the equivalent viral disease while still being harder to achieve at higher fever tiers.
     */
    public static boolean isWarmEnoughForBacterialRecovery(ServerPlayer player) {
        double threshold = MIN_BODY_TEMP_TO_RECOVER + feverOffset(player) * BACTERIAL_FEVER_GATE_SCALE;
        if (LOADED) {
            return Temperature.get(player, Temperature.Trait.BODY) >= threshold;
        }
        int blockLight = player.level().getBrightness(LightLayer.BLOCK, player.blockPosition());
        return getWorldTemp(player) + blockLight / 15.0 > threshold / 50.0;
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

    /**
     * Whether a Damp player is in cold-enough conditions to accumulate illness. Dampness nullifies
     * insulation entirely: the gate ignores the player's body temperature/insulation and looks only at the
     * ambient they're exposed to. With Cold Sweat that ambient is CS's full WORLD temperature
     * ({@code Trait.WORLD}) — which already folds in genuine heat sources (campfire/lava/furnace via
     * {@code BlockTemp}, accurately, unlike a raw light level) and excludes the player's insulation, so a
     * fire/warm biome still protects a soaked player but armor does not. Note the mod's {@code worldTemp}
     * ({@code getRoughTemperatureAt}) is the rough biome temp WITHOUT block heat, which is why we read the
     * full WORLD trait here instead. Without Cold Sweat there is no such model, so we fall back to the
     * rough temp plus a block-light heat proxy (+ a carried hot waterskin). Gate only — RATE unaffected.
     */
    public static boolean isColdEnoughForDamp(ServerPlayer player) {
        if (LOADED) {
            return Temperature.get(player, Temperature.Trait.WORLD) < DAMP_COLD_THRESHOLD;
        }
        double ambient = getWorldTemp(player) + player.level().getBrightness(LightLayer.BLOCK, player.blockPosition()) / 15.0;
        if (hasHotWaterskin(player)) ambient += WATERSKIN_DRY_HEAT;
        return ambient < DAMP_COLD_THRESHOLD;
    }

    /**
     * Whether a windchilled (dry) player is cold enough to accumulate illness. Clothes are dry here, so
     * insulation still protects: with Cold Sweat this gates on the player's CS BODY temperature (which
     * already includes insulation) minus a windchill chill, below the comfort point — so a well-insulated
     * or fire-warmed player resists windchill. Without Cold Sweat there is no body temp, and the windchill
     * predicate already guarantees a cold context, so it simply passes (legacy behavior). Gate only.
     */
    public static boolean isColdEnoughForWindchill(ServerPlayer player) {
        if (!LOADED) return true;
        return Temperature.get(player, Temperature.Trait.BODY) - WINDCHILL_CHILL < BODY_TEMP_ACCUMULATE_BELOW;
    }
}
