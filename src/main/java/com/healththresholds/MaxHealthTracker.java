package com.healththresholds;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Picks an NPC's max HP for HP marks. The boss health overlay's value is exact, so it wins whenever
 * the overlay shows this NPC, and is remembered for the moments the overlay briefly shows nothing.
 * RuneLite's NPC stats are the fallback, but lack some bosses (Araxxor) and give another form's HP
 * for others (Vardorvis).
 */
class MaxHealthTracker
{
	// Overlay readings by NPC id; in memory only, so a changed max HP is picked up on the next reading
	private final Map<Integer, Integer> fromOverlay = new HashMap<>();

	/**
	 * @param overlayNpcId the NPC id the boss health overlay is showing, or -1 for none
	 * @param overlayMax the overlay's max HP, or 0 if unset
	 * @param statsMax RuneLite's NPC stats value, or null if it has none
	 * @return the max HP to place marks against, or null if unknown
	 */
	@Nullable
	Integer resolve(int npcId, int overlayNpcId, int overlayMax, @Nullable Integer statsMax)
	{
		if (overlayNpcId == npcId && overlayMax > 0)
		{
			fromOverlay.put(npcId, overlayMax);
			return overlayMax;
		}
		Integer remembered = fromOverlay.get(npcId);
		return remembered != null ? remembered : statsMax;
	}
}
