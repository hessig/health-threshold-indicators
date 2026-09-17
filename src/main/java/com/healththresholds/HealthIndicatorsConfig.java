package com.healththresholds;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/**
 * Display settings. The per-NPC markers themselves are edited in the sidebar panel and saved
 * separately under {@link HealthIndicatorsPlugin#RULES_KEY}.
 */
@ConfigGroup(HealthIndicatorsConfig.GROUP)
public interface HealthIndicatorsConfig extends Config
{
	String GROUP = "healththresholds";

	@Range(min = 1, max = 5)
	@ConfigItem(
		keyName = "markWidth",
		name = "Mark width",
		description = "Width of each threshold mark in pixels.",
		position = 0
	)
	default int markWidth()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "hidePassedMarks",
		name = "Hide passed marks",
		description = "Stop drawing a mark once the NPC's health drops past it, leaving only the thresholds still ahead. "
			+ "The game reports health in steps, so a mark disappears within a step of its exact value.",
		position = 1
	)
	default boolean hidePassedMarks()
	{
		return false;
	}

	@ConfigItem(
		keyName = "advancedDrawing",
		name = "Advanced drawing mode",
		description = "Reads the drawn frame to find each health bar and place marks exactly on it, including boss bars. "
			+ "Turning this off calculates the bar's position instead, which is normally within a pixel but drifts while "
			+ "the camera moves. The scan costs well under a millisecond per frame; turn it off if you suspect it of "
			+ "affecting performance or it misreads a custom health bar style.",
		position = 2
	)
	default boolean advancedDrawing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugLogging",
		name = "Debug logging",
		description = "Logs each marked NPC's calculated and detected health bar position to the client log about four "
			+ "times a second. Only useful when reporting a problem with mark alignment.",
		position = 3
	)
	default boolean debugLogging()
	{
		return false;
	}
}
