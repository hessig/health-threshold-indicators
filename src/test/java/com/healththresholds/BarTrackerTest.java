package com.healththresholds;

import java.awt.Rectangle;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class BarTrackerTest
{
	private static final Rectangle ESTIMATE = new Rectangle(1013, 175, 120, 5);

	@Test
	public void detectionIsUsedAsIs()
	{
		BarTracker tracker = new BarTracker();
		Rectangle detected = new Rectangle(1012, 173, 120, 5);

		assertEquals(detected, tracker.resolve(1, ESTIMATE, detected, 0));
	}

	@Test
	public void aMissReusesTheLastOffset()
	{
		// Hydra's bar sat 1px left and 2px above the estimate; a miss should keep it there
		BarTracker tracker = new BarTracker();
		tracker.resolve(1, ESTIMATE, new Rectangle(1012, 173, 120, 5), 0);

		Rectangle moved = new Rectangle(1030, 140, 120, 5);
		assertEquals(new Rectangle(1029, 138, 120, 5), tracker.resolve(1, moved, null, 1_000));
	}

	@Test
	public void aMissWithNoHistoryHasNothingToOffer()
	{
		assertNull(new BarTracker().resolve(1, ESTIMATE, null, 0));
	}

	@Test
	public void staleOffsetsExpire()
	{
		BarTracker tracker = new BarTracker();
		tracker.resolve(1, ESTIMATE, new Rectangle(1012, 173, 120, 5), 0);

		assertNull(tracker.resolve(1, ESTIMATE, null, BarTracker.HOLD_MS + 1));
	}

	@Test
	public void eachNpcKeepsItsOwnOffset()
	{
		BarTracker tracker = new BarTracker();
		tracker.resolve(1, ESTIMATE, new Rectangle(1012, 173, 120, 5), 0);
		tracker.resolve(2, ESTIMATE, new Rectangle(1015, 176, 120, 5), 0);

		assertEquals(new Rectangle(1012, 173, 120, 5), tracker.resolve(1, ESTIMATE, null, 100));
		assertEquals(new Rectangle(1015, 176, 120, 5), tracker.resolve(2, ESTIMATE, null, 100));
	}

	@Test
	public void longGoneNpcsAreForgotten()
	{
		BarTracker tracker = new BarTracker();
		tracker.resolve(1, ESTIMATE, new Rectangle(1012, 173, 120, 5), 0);
		tracker.resolve(2, ESTIMATE, new Rectangle(1012, 173, 120, 5), 60_000);

		assertEquals(1, tracker.size());
	}
}
