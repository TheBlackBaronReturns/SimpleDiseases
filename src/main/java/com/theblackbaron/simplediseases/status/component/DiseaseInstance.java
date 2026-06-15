package com.theblackbaron.simplediseases.status.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Live per-player state for one disease the player currently has (or is recovering from). Holds a
 * typed bag of {@link DiseaseComponent}s — exactly the set its category declares. State is read and
 * written through {@link #get(ComponentType)}; the category owns the meaning.
 */
public final class DiseaseInstance {
    private final ResourceLocation diseaseId;
    private final Map<ComponentType<?>, DiseaseComponent> components = new IdentityHashMap<>();

    /** Fresh instance with default-constructed components for each declared type. */
    public DiseaseInstance(ResourceLocation diseaseId, Collection<ComponentType<?>> types) {
        this.diseaseId = diseaseId;
        for (ComponentType<?> type : types) components.put(type, type.create());
    }

    public ResourceLocation diseaseId() { return diseaseId; }

    @SuppressWarnings("unchecked")
    public <T extends DiseaseComponent> T get(ComponentType<T> type) {
        return (T) components.get(type);
    }

    public boolean has(ComponentType<?> type) {
        return components.containsKey(type);
    }

    // --- Serialization (codec per component; keyed by component NBT key) ---

    public CompoundTag save() {
        CompoundTag tag   = new CompoundTag();
        tag.putString("id", diseaseId.toString());
        CompoundTag comps = new CompoundTag();
        for (Map.Entry<ComponentType<?>, DiseaseComponent> e : components.entrySet()) {
            encode(comps, e.getKey(), e.getValue());
        }
        tag.put("components", comps);
        return tag;
    }

    private static <T extends DiseaseComponent> void encode(CompoundTag comps, ComponentType<T> type, DiseaseComponent value) {
        @SuppressWarnings("unchecked") T typed = (T) value;
        type.codec().encodeStart(NbtOps.INSTANCE, typed).result()
            .ifPresent(nbt -> comps.put(type.nbtKey(), nbt));
    }

    /** Loads an instance, seeding defaults for {@code types} then overwriting any persisted ones. */
    public static DiseaseInstance load(CompoundTag tag, Collection<ComponentType<?>> types) {
        DiseaseInstance inst = new DiseaseInstance(new ResourceLocation(tag.getString("id")), types);
        CompoundTag comps = tag.getCompound("components");
        for (ComponentType<?> type : types) {
            if (comps.contains(type.nbtKey())) decode(inst, comps, type);
        }
        return inst;
    }

    private static <T extends DiseaseComponent> void decode(DiseaseInstance inst, CompoundTag comps, ComponentType<T> type) {
        type.codec().parse(NbtOps.INSTANCE, comps.get(type.nbtKey())).result()
            .ifPresent(v -> inst.components.put(type, v));
    }
}
