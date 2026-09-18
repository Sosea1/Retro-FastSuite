package com.sosea1.fastsuite112.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MixinHookTrackerTest {

    @Test
    void testFormatHookState() {
        assertEquals("not applied", MixinHookTracker.formatHookState(false, false));
        assertEquals("not applied", MixinHookTracker.formatHookState(false, true));
        assertEquals("applied (idle)", MixinHookTracker.formatHookState(true, false));
        assertEquals("ACTIVE", MixinHookTracker.formatHookState(true, true));
    }

    @Test
    void testLookupHookDetection() {
        boolean origVanillaApplied = MixinHookTracker.vanillaCraftingHookApplied;
        boolean origUtApplied = MixinHookTracker.universalTweaksHookApplied;
        boolean origVanillaActive = MixinHookTracker.vanillaCraftingHookActive;
        boolean origUtActive = MixinHookTracker.universalTweaksHookActive;

        try {
            MixinHookTracker.vanillaCraftingHookApplied = false;
            MixinHookTracker.universalTweaksHookApplied = false;
            assertFalse(MixinHookTracker.hasAnyLookupHookApplied());

            MixinHookTracker.vanillaCraftingHookApplied = true;
            assertTrue(MixinHookTracker.hasAnyLookupHookApplied());

            MixinHookTracker.vanillaCraftingHookApplied = false;
            MixinHookTracker.universalTweaksHookApplied = true;
            assertTrue(MixinHookTracker.hasAnyLookupHookApplied());

            MixinHookTracker.vanillaCraftingHookActive = false;
            MixinHookTracker.universalTweaksHookActive = false;
            assertFalse(MixinHookTracker.hasAnyLookupHookActive());

            MixinHookTracker.universalTweaksHookActive = true;
            assertTrue(MixinHookTracker.hasAnyLookupHookActive());
        } finally {
            MixinHookTracker.vanillaCraftingHookApplied = origVanillaApplied;
            MixinHookTracker.universalTweaksHookApplied = origUtApplied;
            MixinHookTracker.vanillaCraftingHookActive = origVanillaActive;
            MixinHookTracker.universalTweaksHookActive = origUtActive;
        }
    }
}
