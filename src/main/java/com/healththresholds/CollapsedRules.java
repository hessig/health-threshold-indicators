package com.healththresholds;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks which NPC cards are folded up in the panel, keyed the same way rules collide so a
 * renamed-but-equivalent rule keeps its state. Kept apart from the panel so it can be tested
 * without Swing.
 */
final class CollapsedRules
{
	private CollapsedRules()
	{
	}

	static boolean isCollapsed(Set<String> collapsed, NpcRule rule)
	{
		return collapsed.contains(ImportPlan.key(rule));
	}

	/**
	 * Folds a card up, or unfolds it if it already was.
	 *
	 * @return true if the card is now collapsed
	 */
	static boolean toggle(Set<String> collapsed, NpcRule rule)
	{
		String key = ImportPlan.key(rule);
		if (collapsed.remove(key))
		{
			return false;
		}
		collapsed.add(key);
		return true;
	}

	static void setAll(Set<String> collapsed, List<NpcRule> rules, boolean collapse)
	{
		collapsed.clear();
		if (collapse)
		{
			rules.forEach(rule -> collapsed.add(ImportPlan.key(rule)));
		}
	}

	static boolean anyExpanded(Set<String> collapsed, List<NpcRule> rules)
	{
		return rules.stream().anyMatch(rule -> !isCollapsed(collapsed, rule));
	}

	/**
	 * Drops keys for rules that no longer exist, so the saved setting can't grow forever.
	 */
	static void prune(Set<String> collapsed, List<NpcRule> rules)
	{
		Set<String> live = new HashSet<>();
		rules.forEach(rule -> live.add(ImportPlan.key(rule)));
		collapsed.retainAll(live);
	}
}
