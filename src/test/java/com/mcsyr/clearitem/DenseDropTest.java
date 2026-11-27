package com.mcsyr.clearitem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DenseDropTest {

    @Test
    public void chunkCoordinateUsesFloorDivision() {
        assertEquals(0, DenseDrop.toChunk(0.0));
        assertEquals(0, DenseDrop.toChunk(15.9));
        assertEquals(1, DenseDrop.toChunk(16.0));
        assertEquals(-1, DenseDrop.toChunk(-0.1));
        assertEquals(-1, DenseDrop.toChunk(-16.0));
        assertEquals(-2, DenseDrop.toChunk(-16.1));
    }

    @Test
    public void alertStartsAtThreshold() {
        assertFalse("999 items is below the 1000 threshold", DenseDrop.shouldAlert(999, 1000));
        assertTrue("1000 items reaches the threshold", DenseDrop.shouldAlert(1000, 1000));
        assertTrue("20000 items is far above the threshold", DenseDrop.shouldAlert(20000, 1000));
    }

    @Test
    public void disabledThresholdNeverAlerts() {
        assertFalse(DenseDrop.shouldAlert(5000, 0));
        assertFalse(DenseDrop.shouldAlert(5000, -1));
    }

    @Test
    public void cooldownSuppressesRepeatedAlerts() {
        long last = 1000000L;
        assertTrue("59s after the last alert is still inside a 60s cooldown",
                DenseDrop.isCoolingDown(last + 59000L, last, 60));
        assertFalse("exactly 60s later the cooldown is over",
                DenseDrop.isCoolingDown(last + 60000L, last, 60));
        assertFalse(DenseDrop.isCoolingDown(last + 120000L, last, 60));
    }

    @Test
    public void neverAlertedIsNotCoolingDown() {
        assertFalse(DenseDrop.isCoolingDown(System.currentTimeMillis(), 0L, 60));
    }

    @Test
    public void zeroCooldownDisablesSuppression() {
        assertFalse(DenseDrop.isCoolingDown(1000L, 999L, 0));
    }

    @Test
    public void verifyTriggersOnFirstThresholdCrossing() {
        assertFalse("one item below the threshold does not verify",
                DenseDrop.needsVerify(999, 0, 1000));
        assertTrue("reaching the threshold verifies",
                DenseDrop.needsVerify(1000, 0, 1000));
    }

    @Test
    public void verifyTriggersWhenManyItemsSpawnAtOnce() {
        assertTrue("a burst that jumps from 0 to 15000 still verifies",
                DenseDrop.needsVerify(15000, 0, 1000));
    }

    @Test
    public void verifyDoesNotRepeatForTheSameBatch() {
        assertFalse("after verifying at 1000, 1500 does not verify again",
                DenseDrop.needsVerify(1500, 1000, 1000));
        assertTrue("another 1000 items later verifies again",
                DenseDrop.needsVerify(2000, 1000, 1000));
    }

    @Test
    public void chunkKeyIsStable() {
        assertEquals("world:3:-7", DenseDrop.chunkKey("world", 3, -7));
    }
}
