package com.healththresholds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Works out which imported rules collide with existing ones, and folds a chosen set into the list.
 * Kept apart from the dialog so the rules can be tested without Swing.
 */
final class ImportPlan
{
	private ImportPlan()
	{
	}

	/**
	 * Rules are matched case-insensitively in game, so names collide the same way.
	 */
	static String key(NpcRule rule)
	{
		return rule.getName() == null ? "" : rule.getName().trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * @return the keys of imported rules that already exist
	 */
	static Set<String> conflicts(List<NpcRule> existing, List<NpcRule> imported)
	{
		Set<String> existingKeys = new HashSet<>();
		for (NpcRule rule : existing)
		{
			existingKeys.add(key(rule));
		}

		Set<String> conflicts = new HashSet<>();
		for (NpcRule rule : imported)
		{
			if (existingKeys.contains(key(rule)))
			{
				conflicts.add(key(rule));
			}
		}
		return conflicts;
	}

	/**
	 * Replaces same-named rules in place, keeping their position in the list, and appends the rest.
	 * Any extra duplicates of a replaced name are dropped, tidying up lists that were appended to
	 * before this existed.
	 *
	 * @return the merged list
	 */
	static List<NpcRule> apply(List<NpcRule> existing, List<NpcRule> selected)
	{
		List<NpcRule> result = new ArrayList<>(existing);

		for (NpcRule rule : selected)
		{
			String key = key(rule);
			int first = -1;
			for (int i = result.size() - 1; i >= 0; i--)
			{
				if (key(result.get(i)).equals(key))
				{
					if (first >= 0)
					{
						result.remove(first);
					}
					first = i;
				}
			}

			if (first >= 0)
			{
				result.set(first, rule);
			}
			else
			{
				result.add(rule);
			}
		}
		return result;
	}
}
