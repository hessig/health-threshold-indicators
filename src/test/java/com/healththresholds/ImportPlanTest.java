package com.healththresholds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ImportPlanTest
{
	private static NpcRule rule(String name, double markValue)
	{
		return new NpcRule(name, new ArrayList<>(List.of(new NpcRule.Marker(markValue, true, false, 0xFFFFFFFF))));
	}

	@Test
	public void conflictsIgnoreCaseAndSurroundingSpace()
	{
		Set<String> conflicts = ImportPlan.conflicts(
			List.of(rule("Alchemical Hydra", 75), rule("Nex", 80)),
			List.of(rule(" alchemical hydra ", 50), rule("Cerberus", 50)));

		assertEquals(Set.of("alchemical hydra"), conflicts);
	}

	@Test
	public void replacementKeepsItsPlaceInTheList()
	{
		List<NpcRule> existing = List.of(rule("Nex", 80), rule("Alchemical Hydra", 75), rule("Cerberus", 50));
		List<NpcRule> merged = ImportPlan.apply(existing, List.of(rule("alchemical hydra", 25)));

		assertEquals(3, merged.size());
		assertEquals("Nex", merged.get(0).getName());
		assertEquals("alchemical hydra", merged.get(1).getName());
		assertEquals(25.0, merged.get(1).getMarkers().get(0).getValue(), 1e-9);
		assertEquals("Cerberus", merged.get(2).getName());
	}

	@Test
	public void newRulesAreAppended()
	{
		List<NpcRule> merged = ImportPlan.apply(List.of(rule("Nex", 80)), List.of(rule("Zebak", 85), rule("Akkha", 80)));

		assertEquals(List.of("Nex", "Zebak", "Akkha"),
			merged.stream().map(NpcRule::getName).collect(java.util.stream.Collectors.toList()));
	}

	@Test
	public void replacingCollapsesDuplicatesLeftByOlderImports()
	{
		List<NpcRule> existing = List.of(rule("Nex", 80), rule("Nex", 60), rule("Cerberus", 50), rule("Nex", 40));
		List<NpcRule> merged = ImportPlan.apply(existing, List.of(rule("Nex", 20)));

		assertEquals(2, merged.size());
		assertEquals("Nex", merged.get(0).getName());
		assertEquals(20.0, merged.get(0).getMarkers().get(0).getValue(), 1e-9);
		assertEquals("Cerberus", merged.get(1).getName());
	}

	@Test
	public void nothingSelectedChangesNothing()
	{
		List<NpcRule> existing = List.of(rule("Nex", 80));
		assertEquals(existing, ImportPlan.apply(existing, List.of()));
	}

	@Test
	public void emptyListsHaveNoConflicts()
	{
		assertTrue(ImportPlan.conflicts(List.of(), List.of(rule("Nex", 80))).isEmpty());
		assertTrue(ImportPlan.conflicts(List.of(rule("Nex", 80)), List.of()).isEmpty());
	}
}
