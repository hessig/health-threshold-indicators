package com.healththresholds;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Health Threshold Indicators",
	description = "Draws configurable per-NPC HP threshold marks on NPC health bars",
	tags = {"npc", "boss", "health", "hp", "bar", "threshold", "overlay"}
)
public class HealthIndicatorsPlugin extends Plugin
{
	static final String RULES_KEY = "npcRules";

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private HealthIndicatorsOverlay overlay;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ColorPickerManager colorPickerManager;

	@Inject
	private Gson gson;

	private NavigationButton navButton;

	@Getter
	private volatile List<ThresholdRule> rules = Collections.emptyList();

	@Provides
	HealthIndicatorsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HealthIndicatorsConfig.class);
	}

	@Override
	protected void startUp()
	{
		List<NpcRule> npcRules = loadNpcRules();
		rules = RuleCompiler.compile(npcRules);

		HealthIndicatorsPanel panel = new HealthIndicatorsPanel(npcRules, this::saveNpcRules, gson, colorPickerManager);
		navButton = NavigationButton.builder()
			.tooltip("Health Threshold Indicators")
			.icon(HealthIndicatorsPanel.createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		rules = Collections.emptyList();
	}

	private List<NpcRule> loadNpcRules()
	{
		String json = configManager.getConfiguration(HealthIndicatorsConfig.GROUP, RULES_KEY);
		if (Strings.isNullOrEmpty(json))
		{
			return new ArrayList<>();
		}

		try
		{
			return NpcRule.sanitize(gson.fromJson(json, new TypeToken<ArrayList<NpcRule>>()
			{
			}.getType()));
		}
		catch (JsonParseException e)
		{
			log.warn("Unable to parse saved NPC threshold rules", e);
			return new ArrayList<>();
		}
	}

	private void saveNpcRules(List<NpcRule> npcRules)
	{
		rules = RuleCompiler.compile(npcRules);
		configManager.setConfiguration(HealthIndicatorsConfig.GROUP, RULES_KEY, gson.toJson(npcRules));
	}
}
