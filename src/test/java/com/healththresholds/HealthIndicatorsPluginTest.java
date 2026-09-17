package com.healththresholds;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HealthIndicatorsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HealthIndicatorsPlugin.class);
		RuneLite.main(args);
	}
}
