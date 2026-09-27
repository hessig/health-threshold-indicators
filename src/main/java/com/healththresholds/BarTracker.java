package com.healththresholds;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Remembers where each NPC's bar was last found relative to the calculated estimate, so a frame
 * where the scan misses (a brief occlusion, a busy animation) reuses that offset instead of
 * snapping the marks back to the estimate. The offset for a given NPC barely changes, so it stays
 * valid for a few seconds.
 */
class BarTracker
{
	static final long HOLD_MS = 5_000;
	// Forget NPCs not seen for a while, so the map doesn't grow across a long session
	private static final long FORGET_MS = 30_000;

	@Value
	private static class Offset
	{
		int dx;
		int dy;
		int width;
		long seenAt;
	}

	private final Map<Integer, Offset> offsets = new HashMap<>();
	private long lastPrune;

	/**
	 * @return where to draw: the detected bar if there is one, otherwise the estimate shifted by
	 * this NPC's last measured offset, or null if there's no recent measurement
	 */
	@Nullable
	Rectangle resolve(int npcIndex, Rectangle estimate, @Nullable Rectangle detected, long now)
	{
		prune(now);

		if (detected != null)
		{
			offsets.put(npcIndex, new Offset(detected.x - estimate.x, detected.y - estimate.y, detected.width, now));
			return detected;
		}

		Offset last = offsets.get(npcIndex);
		if (last == null || now - last.getSeenAt() > HOLD_MS)
		{
			return null;
		}
		return new Rectangle(estimate.x + last.getDx(), estimate.y + last.getDy(), last.getWidth(), estimate.height);
	}

	int size()
	{
		return offsets.size();
	}

	private void prune(long now)
	{
		if (now - lastPrune < FORGET_MS)
		{
			return;
		}
		lastPrune = now;
		offsets.values().removeIf(offset -> now - offset.getSeenAt() > FORGET_MS);
	}
}
