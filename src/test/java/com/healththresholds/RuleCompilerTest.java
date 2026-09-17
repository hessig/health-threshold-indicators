package com.healththresholds;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RuleCompilerTest
{
	@Test
	public void compilesMarkersWithColors()
	{
		List<ThresholdRule> rules = RuleCompiler.compile(List.of(new NpcRule("Alchemical Hydra", Arrays.asList(
			new NpcRule.Marker(75, true, false, 0xFFFFFFFF),
			new NpcRule.Marker(275, false, false, 0x80FF0000)))));

		assertEquals(1, rules.size());
		ThresholdRule rule = rules.get(0);
		assertTrue(rule.matches("alchemical hydra"));
		assertFalse(rule.matches("Alchemical Hydra Jr"));
		assertEquals(List.of(
			new Threshold(75, true, false, Color.WHITE),
			new Threshold(275, false, false, new Color(0xFF, 0, 0, 0x80))), rule.getThresholds());
	}

	@Test
	public void regexNames()
	{
		List<ThresholdRule> rules = RuleCompiler.compile(List.of(new NpcRule("(?:Vorkath|Zulrah)", List.of(new NpcRule.Marker()))));

		assertTrue(rules.get(0).matches("Zulrah"));
		assertFalse(rules.get(0).matches("Zulrah's snakeling"));
	}

	@Test
	public void skipsInvalidRulesAndMarkers()
	{
		List<ThresholdRule> rules = RuleCompiler.compile(Arrays.asList(
			new NpcRule("[bad regex", List.of(new NpcRule.Marker())),
			new NpcRule("  ", List.of(new NpcRule.Marker())),
			new NpcRule("Kraken", new ArrayList<>()),
			new NpcRule("Goblin", null),
			new NpcRule("Imp", Arrays.asList(new NpcRule.Marker(150, true, false, 0), new NpcRule.Marker(-5, false, false, 0), null, new NpcRule.Marker(0, true, true, 0), new NpcRule.Marker(30, true, false, 0)))));

		assertEquals(1, rules.size());
		assertEquals(List.of(new Threshold(30, true, false, new Color(0, true))), rules.get(0).getThresholds());
	}

	@Test
	public void phaseMarksKeepTheirOrderAndValues()
	{
		// Example only; the plugin ships with no rules
		List<ThresholdRule> rules = RuleCompiler.compile(List.of(new NpcRule("Alchemical Hydra", exampleMarkers())));

		assertEquals(1, rules.size());
		assertTrue(rules.get(0).matches("Alchemical Hydra"));
		assertEquals(List.of(75.0, 50.0, 25.0), rules.get(0).getThresholds().stream().map(Threshold::getValue).collect(Collectors.toList()));
	}

	@Test
	public void jsonRoundTripAndSanitize()
	{
		Gson gson = new Gson();
		List<NpcRule> original = List.of(new NpcRule("Alchemical Hydra", exampleMarkers()));
		String json = gson.toJson(original);

		List<NpcRule> imported = NpcRule.sanitize(gson.fromJson(json, new TypeToken<ArrayList<NpcRule>>()
		{
		}.getType()));
		assertEquals(original, imported);

		List<NpcRule> messy = NpcRule.sanitize(gson.fromJson("[null, {\"markers\": [null, {\"value\": 10}]}, {\"name\": \"Kraken\"}]",
			new TypeToken<ArrayList<NpcRule>>()
			{
			}.getType()));
		assertEquals(2, messy.size());
		assertEquals("", messy.get(0).getName());
		assertEquals(1, messy.get(0).getMarkers().size());
		assertTrue(messy.get(1).getMarkers().isEmpty());
	}

	@Test
	public void emptyInput()
	{
		assertTrue(RuleCompiler.compile(null).isEmpty());
		assertTrue(RuleCompiler.compile(List.of()).isEmpty());
	}

	@Test
	public void singleRatios()
	{
		assertEquals(List.of(0.125), new Threshold(12.5, true, false, Color.WHITE).ratios(null));
		assertEquals(List.of(0.25), new Threshold(275, false, false, Color.WHITE).ratios(1100));
		assertTrue(new Threshold(275, false, false, Color.WHITE).ratios(null).isEmpty());
		assertTrue(new Threshold(2000, false, false, Color.WHITE).ratios(1100).isEmpty());
	}

	@Test
	public void repeatingRatios()
	{
		assertEquals(List.of(0.25, 0.5, 0.75), new Threshold(25, true, true, Color.WHITE).ratios(null));
		assertEquals(List.of(0.3, 0.6, 0.9), round(new Threshold(30, true, true, Color.WHITE).ratios(null)));
		assertEquals(5, new Threshold(200, false, true, Color.WHITE).ratios(1100).size());
		assertEquals(1000 / 1100.0, new Threshold(200, false, true, Color.WHITE).ratios(1100).get(4), 1e-9);
		assertEquals(3, new Threshold(275, false, true, Color.WHITE).ratios(1100).size());
		assertTrue(new Threshold(200, false, true, Color.WHITE).ratios(null).isEmpty());
		assertTrue(new Threshold(0, true, true, Color.WHITE).ratios(null).isEmpty());
		assertEquals(10_000, new Threshold(1, false, true, Color.WHITE).ratios(1_000_000).size());
	}

	private static List<NpcRule.Marker> exampleMarkers()
	{
		return new ArrayList<>(List.of(
			new NpcRule.Marker(75, true, false, 0xFF3DA5FF),
			new NpcRule.Marker(50, true, false, 0xFFFF3B30),
			new NpcRule.Marker(25, true, false, 0xFFD0D0D0)));
	}

	private static List<Double> round(List<Double> values)
	{
		return values.stream().map(v -> Math.round(v * 1e9) / 1e9).collect(Collectors.toList());
	}
}
