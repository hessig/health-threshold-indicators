package com.healththresholds;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class HidePassedMarksTest
{
	@Test
	public void marksAboveCurrentHealthArePassed()
	{
		// A standard bar reports health in 30 steps; 15/30 is half health
		assertTrue(HealthIndicatorsOverlay.isPassed(0.75, 15, 30));
		assertFalse(HealthIndicatorsOverlay.isPassed(0.25, 15, 30));
	}

	@Test
	public void markAtExactlyCurrentHealthStillShows()
	{
		assertFalse(HealthIndicatorsOverlay.isPassed(0.5, 15, 30));
	}

	@Test
	public void bossBarsUseTheirOwnScale()
	{
		// Hydra's bar has a larger scale, so marks resolve more finely
		assertTrue(HealthIndicatorsOverlay.isPassed(0.5, 59, 120));
		assertFalse(HealthIndicatorsOverlay.isPassed(0.5, 60, 120));
	}

	@Test
	public void nothingIsPassedWithoutHealthInfo()
	{
		assertFalse(HealthIndicatorsOverlay.isPassed(0.75, -1, 30));
		assertFalse(HealthIndicatorsOverlay.isPassed(0.75, 15, 0));
	}
}
