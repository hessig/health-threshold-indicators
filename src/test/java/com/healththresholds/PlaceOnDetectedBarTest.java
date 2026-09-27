package com.healththresholds;

import java.awt.Rectangle;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class PlaceOnDetectedBarTest
{
	private static final Rectangle ESTIMATE = new Rectangle(500, 300, 30, 5);
	private static final Rectangle DETECTED = new Rectangle(501, 298, 30, 5);

	@Test
	public void aDetectedBarIsDrawnOn()
	{
		assertEquals(DETECTED, HealthIndicatorsOverlay.placeOnDetectedBar(new BarTracker(), 1, false, ESTIMATE, DETECTED, 0));
	}

	@Test
	public void aBarNeverFoundGetsNoMarks()
	{
		// A shield-colored bar, or a boss with no overhead bar, must not get marks at the estimate
		assertNull(HealthIndicatorsOverlay.placeOnDetectedBar(new BarTracker(), 1, false, ESTIMATE, null, 0));
	}

	@Test
	public void aBriefMissKeepsTheMarksOnTheBar()
	{
		BarTracker tracker = new BarTracker();
		HealthIndicatorsOverlay.placeOnDetectedBar(tracker, 1, false, ESTIMATE, DETECTED, 0);

		assertEquals(DETECTED, HealthIndicatorsOverlay.placeOnDetectedBar(tracker, 1, false, ESTIMATE, null, 1_000));
	}

	@Test
	public void marksGoOnceTheBarHasBeenMissingAWhile()
	{
		BarTracker tracker = new BarTracker();
		HealthIndicatorsOverlay.placeOnDetectedBar(tracker, 1, false, ESTIMATE, DETECTED, 0);

		assertNull(HealthIndicatorsOverlay.placeOnDetectedBar(tracker, 1, false, ESTIMATE, null, BarTracker.HOLD_MS + 1));
	}

	@Test
	public void aDeadNpcsMarksGoWithItsBar()
	{
		BarTracker tracker = new BarTracker();
		HealthIndicatorsOverlay.placeOnDetectedBar(tracker, 1, false, ESTIMATE, DETECTED, 0);

		assertNull(HealthIndicatorsOverlay.placeOnDetectedBar(tracker, 1, true, ESTIMATE, null, 100));
	}
}
