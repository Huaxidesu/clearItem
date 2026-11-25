package com.mcsyr.clearitem;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MinAgeTest {

    @Test
    public void recentlyDroppedItemIsProtectedWithinMinAge() {
        int ticksLived = 59 * 20;
        int minAgeSeconds = 60;
        assertTrue("59s old item must be protected when MinAge=60s",
                tools.isTooYoungToClear(ticksLived, minAgeSeconds));
    }

    @Test
    public void itemAtExactMinAgeCanBeCleared() {
        int ticksLived = 60 * 20;
        int minAgeSeconds = 60;
        assertFalse("60s old item is eligible for clearing when MinAge=60s",
                tools.isTooYoungToClear(ticksLived, minAgeSeconds));
    }

    @Test
    public void olderItemCanBeCleared() {
        int ticksLived = 121 * 20;
        int minAgeSeconds = 60;
        assertFalse("121s old item is eligible for clearing",
                tools.isTooYoungToClear(ticksLived, minAgeSeconds));
    }

    @Test
    public void disabledMinAgeClearsAllDrops() {
        int ticksLived = 0;
        int minAgeSeconds = 0;
        assertFalse("MinAge=0 disables protection; fresh drop is eligible",
                tools.isTooYoungToClear(ticksLived, minAgeSeconds));
    }

    @Test
    public void freshlyDroppedItemProtected() {
        int ticksLived = 10;
        int minAgeSeconds = 60;
        assertTrue("0.5s old item must be protected",
                tools.isTooYoungToClear(ticksLived, minAgeSeconds));
    }
}
