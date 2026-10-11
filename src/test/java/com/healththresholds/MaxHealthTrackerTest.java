package com.healththresholds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class MaxHealthTrackerTest
{
	private static final int ARAXXOR = 13668;
	private static final int OTHER = 1;

	@Test
	public void overlayIsPreferredOverStats()
	{
		// The stats can hold another form's HP, as they do for Vardorvis
		assertEquals(Integer.valueOf(600), new MaxHealthTracker().resolve(OTHER, OTHER, 600, 500));
	}

	@Test
	public void overlayFillsInMissingStats()
	{
		assertEquals(Integer.valueOf(1020), new MaxHealthTracker().resolve(ARAXXOR, ARAXXOR, 1020, null));
	}

	@Test
	public void briefOverlayGapKeepsTheLastReading()
	{
		// Seen at Araxxor: the overlay reported no NPC for half a second mid-fight
		MaxHealthTracker tracker = new MaxHealthTracker();
		tracker.resolve(ARAXXOR, ARAXXOR, 1020, null);

		assertEquals(Integer.valueOf(1020), tracker.resolve(ARAXXOR, -1, 0, null));
	}

	@Test
	public void newOverlayReadingReplacesTheOldOne()
	{
		MaxHealthTracker tracker = new MaxHealthTracker();
		tracker.resolve(ARAXXOR, ARAXXOR, 1020, null);
		tracker.resolve(ARAXXOR, ARAXXOR, 1100, null);

		assertEquals(Integer.valueOf(1100), tracker.resolve(ARAXXOR, -1, 0, null));
	}

	@Test
	public void overlayForAnotherNpcIsIgnored()
	{
		assertEquals(Integer.valueOf(250), new MaxHealthTracker().resolve(OTHER, ARAXXOR, 1020, 250));
	}

	@Test
	public void unknownWithoutOverlayOrStats()
	{
		assertNull(new MaxHealthTracker().resolve(ARAXXOR, -1, 0, null));
	}
}
