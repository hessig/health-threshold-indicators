package com.healththresholds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Saved form of one NPC's markers, stored as JSON in the plugin's config.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
class NpcRule
{
	private String name = "";
	private List<Marker> markers = new ArrayList<>();

	/**
	 * Replaces nulls from hand-edited or imported JSON so the rules are safe to edit.
	 */
	static List<NpcRule> sanitize(List<NpcRule> rules)
	{
		List<NpcRule> result = new ArrayList<>();
		if (rules == null)
		{
			return result;
		}
		for (NpcRule rule : rules)
		{
			if (rule == null)
			{
				continue;
			}
			List<Marker> markers = new ArrayList<>();
			if (rule.getMarkers() != null)
			{
				rule.getMarkers().stream().filter(Objects::nonNull).forEach(markers::add);
			}
			result.add(new NpcRule(rule.getName() == null ? "" : rule.getName(), markers));
		}
		return result;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class Marker
	{
		private double value = 50;
		private boolean percent = true;
		private boolean repeating;
		private int color = 0xFFFFFFFF;
	}
}
