package com.healththresholds;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CollapsedRulesTest
{
	private static NpcRule rule(String name)
	{
		return new NpcRule(name, new ArrayList<>());
	}

	@Test
	public void togglingFoldsAndUnfolds()
	{
		Set<String> collapsed = new LinkedHashSet<>();
		NpcRule hydra = rule("Alchemical Hydra");

		assertTrue(CollapsedRules.toggle(collapsed, hydra));
		assertTrue(CollapsedRules.isCollapsed(collapsed, hydra));

		assertFalse(CollapsedRules.toggle(collapsed, hydra));
		assertFalse(CollapsedRules.isCollapsed(collapsed, hydra));
		assertTrue(collapsed.isEmpty());
	}

	@Test
	public void stateIgnoresCaseAndSurroundingSpace()
	{
		Set<String> collapsed = new LinkedHashSet<>();
		CollapsedRules.toggle(collapsed, rule("Alchemical Hydra"));

		assertTrue(CollapsedRules.isCollapsed(collapsed, rule(" alchemical hydra ")));
	}

	@Test
	public void setAllCollapsesOrExpandsEverything()
	{
		List<NpcRule> rules = List.of(rule("Nex"), rule("Cerberus"));
		Set<String> collapsed = new LinkedHashSet<>();

		CollapsedRules.setAll(collapsed, rules, true);
		assertEquals(2, collapsed.size());
		assertFalse(CollapsedRules.anyExpanded(collapsed, rules));

		CollapsedRules.setAll(collapsed, rules, false);
		assertTrue(collapsed.isEmpty());
		assertTrue(CollapsedRules.anyExpanded(collapsed, rules));
	}

	@Test
	public void oneExpandedCardCountsAsExpanded()
	{
		List<NpcRule> rules = List.of(rule("Nex"), rule("Cerberus"));
		Set<String> collapsed = new LinkedHashSet<>();
		CollapsedRules.toggle(collapsed, rules.get(0));

		assertTrue(CollapsedRules.anyExpanded(collapsed, rules));
	}

	@Test
	public void pruningDropsRulesThatNoLongerExist()
	{
		Set<String> collapsed = new LinkedHashSet<>(List.of("nex", "cerberus", "deleted boss"));

		CollapsedRules.prune(collapsed, List.of(rule("Nex"), rule("Cerberus")));

		assertEquals(Set.of("nex", "cerberus"), collapsed);
	}
}
