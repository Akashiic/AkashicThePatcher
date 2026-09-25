package com.akashic.thepatcher.module.startupaudit;

import org.junit.Assert;
import org.junit.Test;

public class FmlPhaseAuditHooksTest {

    @Test
    public void sortsSlowestFirstAndLimitsOutput() {
        String result =
            FmlPhaseAuditHooks.summarizeForTest(
                new String[] {
                    "fast",
                    "slow",
                    "middle"
                },
                new long[] {
                    10_000_000L,
                    900_000_000L,
                    250_000_000L
                },
                2);

        Assert.assertEquals(
            "slow:900ms,middle:250ms",
            result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedSamples() {
        FmlPhaseAuditHooks.summarizeForTest(
            new String[] {"a"},
            new long[] {1L, 2L},
            1);
    }
}
