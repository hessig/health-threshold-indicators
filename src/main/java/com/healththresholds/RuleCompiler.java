package com.healththresholds;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Turns saved NPC rules into render-ready rules, dropping blank names, invalid
 * regexes and out-of-range markers.
 */
final class RuleCompiler
{
	private RuleCompiler()
	{
	}

	static List<ThresholdRule> compile(List<NpcRule> npcRules)
	{
		if (npcRules == null || npcRules.isEmpty())
		{
			return Collections.emptyList();
		}

		List<ThresholdRule> rules = new ArrayList<>();
		for (NpcRule npcRule : npcRules)
		{
			Pattern pattern = compilePattern(npcRule.getName());
			if (pattern == null || npcRule.getMarkers() == null)
			{
				continue;
			}

			List<Threshold> thresholds = new ArrayList<>();
			for (NpcRule.Marker marker : npcRule.getMarkers())
			{
				if (marker == null || marker.getValue() < 0 || (marker.isPercent() && marker.getValue() > 100)
					|| (marker.isRepeating() && marker.getValue() <= 0))
				{
					continue;
				}
				thresholds.add(new Threshold(marker.getValue(), marker.isPercent(), marker.isRepeating(), new Color(marker.getColor(), true)));
			}

			if (!thresholds.isEmpty())
			{
				rules.add(new ThresholdRule(pattern, thresholds));
			}
		}
		return rules;
	}

	/**
	 * @return the case-insensitive pattern for an NPC name, or null if blank or invalid
	 */
	static Pattern compilePattern(String name)
	{
		if (name == null || name.isBlank())
		{
			return null;
		}
		try
		{
			return Pattern.compile(name.trim(), Pattern.CASE_INSENSITIVE);
		}
		catch (PatternSyntaxException e)
		{
			return null;
		}
	}
}
