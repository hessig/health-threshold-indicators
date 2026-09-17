package com.healththresholds;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.NPCManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Slf4j
class HealthIndicatorsOverlay extends Overlay
{
	private static final int BAR_HEIGHT = 5;
	// Measured in-game across zoom levels by reading the client's drawn frame: the health bar's top sits
	// BAR_TOP_OFFSET px above the point projected OVERHEAD_HEIGHT units above the actor
	private static final int OVERHEAD_HEIGHT = 16;
	private static final int BAR_TOP_OFFSET = 2;
	// The estimate is within a few pixels of the real bar; search this far around it
	private static final int SEARCH_RADIUS = 8;
	private static final long DEBUG_LOG_INTERVAL_MS = 250;

	private final Client client;
	private final HealthIndicatorsPlugin plugin;
	private final HealthIndicatorsConfig config;
	private final NPCManager npcManager;

	private long lastDebugLog;

	@Inject
	private HealthIndicatorsOverlay(Client client, HealthIndicatorsPlugin plugin, HealthIndicatorsConfig config, NPCManager npcManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.npcManager = npcManager;
		setPosition(OverlayPosition.DYNAMIC);
		// Overheads are already drawn into the frame at this layer, so marks draw on top of the
		// health bar and the bar itself can be located in the buffer; interfaces still cover marks
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<ThresholdRule> rules = plugin.getRules();
		WorldView topLevel = client.getTopLevelWorldView();
		if (rules.isEmpty() || topLevel == null)
		{
			return null;
		}

		boolean debug = false;
		if (config.debugLogging())
		{
			long now = System.currentTimeMillis();
			if (now - lastDebugLog >= DEBUG_LOG_INTERVAL_MS)
			{
				lastDebugLog = now;
				debug = true;
			}
		}

		for (NPC npc : topLevel.npcs())
		{
			String name = npc.getName();
			if (name == null || npc.getHealthRatio() < 0 || npc.getHealthScale() <= 0)
			{
				continue;
			}

			for (ThresholdRule rule : rules)
			{
				if (rule.matches(name))
				{
					renderMarks(graphics, npc, rule.getThresholds(), debug);
				}
			}
		}

		return null;
	}

	private void renderMarks(Graphics2D graphics, NPC npc, List<Threshold> thresholds, boolean debug)
	{
		LocalPoint lp = npc.getLocalLocation();
		WorldView wv = npc.getWorldView();
		if (lp == null || wv == null)
		{
			return;
		}

		int baseHeight = npc.getLogicalHeight() + npc.getAnimationHeightOffset();
		int tileHeight = Perspective.getFootprintTileHeight(client, lp, wv.getPlane(), npc.getFootprintSize());
		Point anchor = Perspective.localToCanvas(client, wv.getId(), lp.getX(), lp.getY(), tileHeight - baseHeight - OVERHEAD_HEIGHT);
		if (anchor == null)
		{
			return;
		}

		// A health bar's scale is its width in pixels (30 for standard NPCs, wider for bosses)
		int estimatedWidth = npc.getHealthScale();
		Rectangle estimate = new Rectangle(anchor.getX() - estimatedWidth / 2, anchor.getY() - BAR_TOP_OFFSET, estimatedWidth, BAR_HEIGHT);
		Rectangle detected = config.advancedDrawing() ? findBar(estimate) : null;

		if (debug)
		{
			logDebug(npc, lp, wv, tileHeight, baseHeight, estimate, detected);
		}

		Rectangle bar = detected;
		if (bar == null)
		{
			// A dead NPC's bar is hidden, so drop the marks with it rather than leaving them floating.
			// Otherwise the scan is off, or the bar uses a style it can't detect, so fall back to the estimate.
			if (config.advancedDrawing() && npc.isDead())
			{
				return;
			}
			bar = estimate;
		}

		int barX = bar.x;
		int barY = bar.y;
		int markWidth = config.markWidth();
		boolean hidePassed = config.hidePassedMarks();
		Integer maxHealth = null;

		for (Threshold threshold : thresholds)
		{
			if (!threshold.isPercent() && maxHealth == null)
			{
				maxHealth = npcManager.getHealth(npc.getId());
			}

			graphics.setColor(threshold.getColor());
			int lastMarkX = Integer.MIN_VALUE;
			for (double ratio : threshold.ratios(maxHealth))
			{
				if (hidePassed && isPassed(ratio, npc.getHealthRatio(), npc.getHealthScale()))
				{
					continue;
				}

				int markX = barX + (int) Math.round(bar.width * ratio) - markWidth / 2;
				// Tightly spaced repeating marks can round to the same pixel
				if (markX != lastMarkX)
				{
					graphics.fillRect(markX, barY, markWidth, bar.height);
					lastMarkX = markX;
				}
			}
		}
	}

	/**
	 * Finds the drawn health bar nearest the estimate by scanning the client's frame buffer for a
	 * row of health-green/red pixels, since the bar is already drawn when this layer renders.
	 *
	 * @return the bar's bounds, or null if no bar is drawn near the estimate
	 */
	@Nullable
	private Rectangle findBar(Rectangle estimate)
	{
		BufferProvider buffer = client.getBufferProvider();
		return findBar(buffer.getPixels(), buffer.getWidth(), buffer.getHeight(), estimate);
	}

	@Nullable
	static Rectangle findBar(int[] pixels, int width, int height, Rectangle estimate)
	{
		int horizontalRadius = Math.max(SEARCH_RADIUS, estimate.width / 2);
		int x0 = Math.max(0, estimate.x - horizontalRadius);
		int x1 = Math.min(width, estimate.x + estimate.width + horizontalRadius);
		int y0 = Math.max(0, estimate.y - SEARCH_RADIUS);
		int y1 = Math.min(height, estimate.y + BAR_HEIGHT + SEARCH_RADIUS);
		int minCount = Math.max(10, estimate.width * 3 / 4);

		Rectangle best = null;
		boolean previousRowMatched = false;
		for (int y = y0; y < y1; y++)
		{
			int count = 0;
			int left = -1;
			int right = -1;
			for (int x = x0; x < x1; x++)
			{
				if (isBarPixel(pixels[y * width + x]))
				{
					count++;
					left = left < 0 ? x : left;
					right = x;
				}
			}

			boolean matched = count >= minCount;
			// Only consider the top row of each bar, and prefer the one nearest the estimate
			if (matched && !previousRowMatched
				&& (best == null || Math.abs(y - estimate.y) < Math.abs(best.y - estimate.y)))
			{
				best = new Rectangle(left, y, right - left + 1, BAR_HEIGHT);
			}
			previousRowMatched = matched;
		}
		return best;
	}

	/**
	 * @return true once the NPC's health has dropped past this mark. The server only sends health in
	 * {@code healthScale} steps, so this is accurate to one step.
	 */
	static boolean isPassed(double ratio, int healthRatio, int healthScale)
	{
		if (healthRatio < 0 || healthScale <= 0)
		{
			return false;
		}
		return ratio > healthRatio / (double) healthScale;
	}

	private static boolean isBarPixel(int rgb)
	{
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return (g > 200 && r < 60 && b < 60) || (r > 200 && g < 60 && b < 60);
	}

	private void logDebug(NPC npc, LocalPoint lp, WorldView wv, int tileHeight, int baseHeight, Rectangle estimate, @Nullable Rectangle detected)
	{
		Point p0 = Perspective.localToCanvas(client, wv.getId(), lp.getX(), lp.getY(), tileHeight - baseHeight);
		Point p20 = Perspective.localToCanvas(client, wv.getId(), lp.getX(), lp.getY(), tileHeight - baseHeight - 20);
		String scale = p0 == null || p20 == null ? "?" : String.format("%.3f", (p0.getY() - p20.getY()) / 20.0);

		log.info("healthindicators debug npc={} id={} index={} dead={} ratio={}/{} logicalHeight={} animOffset={} local={},{} scalePxPerUnit={} "
				+ "estimate={},{} {}px detected={} dx={} dy={}",
			npc.getName(), npc.getId(), npc.getIndex(), npc.isDead(), npc.getHealthRatio(), npc.getHealthScale(),
			npc.getLogicalHeight(), npc.getAnimationHeightOffset(), lp.getX(), lp.getY(), scale,
			estimate.x, estimate.y, estimate.width,
			detected == null ? "none" : detected.x + "," + detected.y + " " + detected.width + "px",
			detected == null ? "-" : detected.x - estimate.x,
			detected == null ? "-" : detected.y - estimate.y);
	}
}
