package com.theblackbaron.simplediseases.status.component;

/**
 * Marker for a unit of live, per-player, per-disease state (e.g. accumulation progress, immunity
 * window, symptom pool). A {@link com.theblackbaron.simplediseases.status.category.DiseaseCategory}
 * declares which components its diseases carry; a category that needs new state (bacterial
 * incubation, parasite stages, …) adds a new component type rather than touching existing ones.
 */
public interface DiseaseComponent {
}
