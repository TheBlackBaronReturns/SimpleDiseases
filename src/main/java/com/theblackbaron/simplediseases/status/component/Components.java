package com.theblackbaron.simplediseases.status.component;

/**
 * Registry of built-in component types. Categories pick the subset they need from here; future
 * categories add their own component types (e.g. an IncubationComponent for bacterial, a
 * StageComponent for parasite) without altering existing diseases.
 */
public final class Components {
    private Components() {}

    public static final ComponentType<ProgressComponent> PROGRESS =
        new ComponentType<>("progress", ProgressComponent.CODEC, ProgressComponent::new);

    public static final ComponentType<ImmunityComponent> IMMUNITY =
        new ComponentType<>("immunity", ImmunityComponent.CODEC, ImmunityComponent::new);

    public static final ComponentType<SymptomPoolComponent> SYMPTOMS =
        new ComponentType<>("symptoms", SymptomPoolComponent.CODEC, SymptomPoolComponent::new);

    public static final ComponentType<TierComponent> TIER =
        new ComponentType<>("tier", TierComponent.CODEC, TierComponent::new);

    /** Viral-complication state (which source disease caused it, randomized onset, chosen symptom). */
    public static final ComponentType<SourceComponent> SOURCE =
        new ComponentType<>("source", SourceComponent.CODEC, SourceComponent::new);
}
