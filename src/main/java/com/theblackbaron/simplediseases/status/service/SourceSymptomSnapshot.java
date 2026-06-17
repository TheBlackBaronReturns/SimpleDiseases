package com.theblackbaron.simplediseases.status.service;

import com.theblackbaron.simplediseases.status.def.SymptomConfig;

/** Active symptom roster on a complication's source disease during pre-latch inheritance. */
public record SourceSymptomSnapshot(SymptomConfig config, int sourceMask) {}
