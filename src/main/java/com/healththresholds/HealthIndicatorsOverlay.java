package com.healththresholds;

import com.google.common.collect.ImmutableSet;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.Set;
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
import net.runelite.client.RuneLiteProperties;
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
	// The bar stays within a few pixels of the estimate sideways, but moves further vertically while
	// a tall NPC animates (Hydra's bar was measured 8px+ above the estimate while walking). Matches
	// must be the bar's exact width, so a taller window doesn't risk catching other red or green rows.
	private static final int SEARCH_RADIUS = 8;
	private static final int VERTICAL_SEARCH_RADIUS = 20;
	// How far a run of bar pixels may differ from the expected width and still count as the bar
	private static final int WIDTH_TOLERANCE = 4;
	// Gaps a run may bridge, such as another overlay's mark drawn across the bar
	private static final int MAX_GAP = 2;
	private static final long DEBUG_LOG_INTERVAL_MS = 250;
	// Interface Styles' high detail bars are 7px sprites filled inside a 1px border, so marks go on that
	// inner area. These are the standard bar widths it replaces; 30px bars get its 40px sprite.
	private static final Set<Integer> HIGH_DETAIL_WIDTHS = ImmutableSet.of(30, 40, 50, 60, 70, 80, 90, 100, 120, 140, 160);
	private static final int HIGH_DETAIL_BORDER = 1;
	// Measured in game: the sprite's bottom lines up with a standard bar's, so its top sits this much higher
	private static final int HIGH_DETAIL_EXTRA_HEIGHT = 2;

	private final Client client;
	private final HealthIndicatorsPlugin plugin;
	private final HealthIndicatorsConfig config;
	private final NPCManager npcManager;

	private long lastDebugLog;
	private boolean debugHeaderLogged;
	// Counts rendered frames, so the gap between debug lines shows how many frames were drawn
	private long frame;
	private final BarTracker barTracker = new BarTracker();

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

		frame++;
		boolean debug = false;
		if (config.debugLogging())
		{
			if (!debugHeaderLogged)
			{
				logDebugHeader(rules.size());
				debugHeaderLogged = true;
			}

			long now = System.currentTimeMillis();
			if (now - lastDebugLog >= DEBUG_LOG_INTERVAL_MS)
			{
				lastDebugLog = now;
				debug = true;
			}
		}
		else
		{
			// Log the header again whenever logging is turned back on, so each report starts with it
			debugHeaderLogged = false;
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
					renderMarks(graphics, npc, rule, debug);
				}
			}
		}

		return null;
	}

	private void renderMarks(Graphics2D graphics, NPC npc, ThresholdRule rule, boolean debug)
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
		int scale = npc.getHealthScale();
		boolean highDetail = plugin.isHighDetailBars() && HIGH_DETAIL_WIDTHS.contains(scale);
		Rectangle estimate = estimateBar(anchor, scale, highDetail);
		boolean advanced = config.advancedDrawing();
		Rectangle detected = advanced ? findBar(estimate, highDetail) : null;
		Rectangle bar = advanced
			? placeOnDetectedBar(barTracker, npc.getIndex(), npc.isDead(), estimate, detected, System.currentTimeMillis())
			: estimate;

		if (debug)
		{
			logDebug(npc, rule, lp, wv, tileHeight, baseHeight, highDetail, estimate, detected, bar);
		}

		if (bar == null)
		{
			return;
		}

		int barX = bar.x;
		int barY = bar.y;
		int markWidth = config.markWidth();
		boolean hidePassed = config.hidePassedMarks();
		Integer maxHealth = null;

		for (Threshold threshold : rule.getThresholds())
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
	 * @return where the bar's fill should be drawn: the whole bar for a standard one, or the area
	 * inside the border for a high detail one
	 */
	static Rectangle estimateBar(Point anchor, int scale, boolean highDetail)
	{
		if (!highDetail)
		{
			return new Rectangle(anchor.getX() - scale / 2, anchor.getY() - BAR_TOP_OFFSET, scale, BAR_HEIGHT);
		}
		int spriteWidth = scale == 30 ? 40 : scale;
		int spriteTop = anchor.getY() - BAR_TOP_OFFSET - HIGH_DETAIL_EXTRA_HEIGHT;
		return new Rectangle(anchor.getX() - spriteWidth / 2 + HIGH_DETAIL_BORDER, spriteTop + HIGH_DETAIL_BORDER,
			spriteWidth - 2 * HIGH_DETAIL_BORDER, BAR_HEIGHT);
	}

	/**
	 * Where to draw in advanced mode. Marks only go on a bar that was actually found: this frame,
	 * or recently enough that a brief miss keeps them in place. A bar that is never found (another
	 * color, such as a shield, or no overhead bar at all) gets no marks rather than floating ones.
	 *
	 * @return the bar to draw on, or null to draw nothing
	 */
	@Nullable
	static Rectangle placeOnDetectedBar(BarTracker tracker, int npcIndex, boolean dead, Rectangle estimate,
		@Nullable Rectangle detected, long now)
	{
		// A dead NPC's bar is hidden, so drop the marks with it rather than holding them
		if (detected == null && dead)
		{
			return null;
		}
		return tracker.resolve(npcIndex, estimate, detected, now);
	}

	/**
	 * Finds the drawn health bar nearest the estimate by scanning the client's frame buffer for a
	 * row of health-green/red pixels, since the bar is already drawn when this layer renders.
	 *
	 * @return the bar's bounds, or null if no bar is drawn near the estimate
	 */
	@Nullable
	private Rectangle findBar(Rectangle estimate, boolean highDetail)
	{
		BufferProvider buffer = client.getBufferProvider();
		return findBar(buffer.getPixels(), buffer.getWidth(), buffer.getHeight(), estimate, highDetail);
	}

	@Nullable
	static Rectangle findBar(int[] pixels, int width, int height, Rectangle estimate, boolean highDetail)
	{
		// The bar is always within a few pixels of the estimate sideways too, so the window stays
		// narrow even for wide boss bars; wider windows picked up red and green scenery nearby.
		int x0 = Math.max(0, estimate.x - SEARCH_RADIUS);
		int x1 = Math.min(width, estimate.x + estimate.width + SEARCH_RADIUS);
		int y0 = Math.max(0, estimate.y - VERTICAL_SEARCH_RADIUS);
		int y1 = Math.min(height, estimate.y + BAR_HEIGHT + VERTICAL_SEARCH_RADIUS);

		Rectangle best = null;
		int barTop = -1;
		int[] barRun = null;
		for (int y = y0; y <= y1; y++)
		{
			int[] run = y < y1 ? longestRun(pixels, width, y, x0, x1, highDetail) : null;
			// A bar's width equals its health scale, so a run much shorter or longer is something else
			boolean matched = run != null && Math.abs(run[1] - estimate.width) <= WIDTH_TOLERANCE;
			if (matched && barTop < 0)
			{
				barTop = y;
				barRun = run;
			}
			else if (matched && y - barTop < BAR_HEIGHT && widthError(run, estimate) < widthError(barRun, estimate))
			{
				// A high detail bar's top row is shortened by its rounded corners, so take the
				// position from the row that best fits
				barRun = run;
			}
			else if (!matched && barTop >= 0)
			{
				// Prefer the bar nearest the estimate
				if (best == null || Math.abs(barTop - estimate.y) < Math.abs(best.y - estimate.y))
				{
					best = new Rectangle(trimmedStart(barRun, estimate), barTop, estimate.width, BAR_HEIGHT);
				}
				barTop = -1;
			}
		}
		return best;
	}

	private static int widthError(int[] run, Rectangle estimate)
	{
		return Math.abs(run[1] - estimate.width);
	}

	/**
	 * A matched run can still carry a few pixels of touching scenery at one end. The bar's real
	 * width is its health scale, so keep that width and anchor it at whichever end of the run lines
	 * up better with the estimate.
	 */
	private static int trimmedStart(int[] run, Rectangle estimate)
	{
		int start = run[0];
		int end = run[0] + run[1];
		int fromEnd = end - estimate.width;
		return Math.abs(start - estimate.x) <= Math.abs(fromEnd - estimate.x) ? start : fromEnd;
	}

	/**
	 * @return {start, length} of the longest run of bar pixels in a row, bridging gaps of up to
	 * {@link #MAX_GAP} pixels so another overlay's mark over the bar doesn't split it
	 */
	private static int[] longestRun(int[] pixels, int width, int y, int x0, int x1, boolean highDetail)
	{
		int bestStart = -1;
		int bestLength = 0;
		int runStart = -1;
		int lastBar = -1;
		for (int x = x0; x < x1; x++)
		{
			if (!(highDetail ? isHighDetailBarPixel(pixels[y * width + x]) : isBarPixel(pixels[y * width + x])))
			{
				continue;
			}
			if (runStart < 0 || x - lastBar - 1 > MAX_GAP)
			{
				runStart = x;
			}
			lastBar = x;
			if (lastBar - runStart + 1 > bestLength)
			{
				bestStart = runStart;
				bestLength = lastBar - runStart + 1;
			}
		}
		return new int[]{bestStart, bestLength};
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

	/**
	 * High detail bars are shaded: the green runs from yellow-green to dark green and the red is
	 * darker, so they need a looser test. It only applies while those bars are in use.
	 */
	private static boolean isHighDetailBarPixel(int rgb)
	{
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return (g >= 0x90 && g - r >= 0x20 && b < 0x30) || (r >= 0x70 && g < 0x30 && b < 0x20);
	}

	/**
	 * Settings that affect where the bar is drawn and how it is found, logged once per report
	 */
	private void logDebugHeader(int ruleCount)
	{
		BufferProvider buffer = client.getBufferProvider();
		log.info("healthindicators debug header runelite={} gpu={} stretched={} canvas={}x{} buffer={}x{} "
				+ "advancedDrawing={} markWidth={} hidePassedMarks={} highDetailBars={} rules={}",
			RuneLiteProperties.getVersion(), client.isGpu(), client.isStretchedEnabled(),
			client.getCanvasWidth(), client.getCanvasHeight(),
			buffer == null ? "?" : buffer.getWidth(), buffer == null ? "?" : buffer.getHeight(),
			config.advancedDrawing(), config.markWidth(), config.hidePassedMarks(), plugin.isHighDetailBars(), ruleCount);
	}

	private void logDebug(NPC npc, ThresholdRule rule, LocalPoint lp, WorldView wv, int tileHeight, int baseHeight,
		boolean highDetail, Rectangle estimate, @Nullable Rectangle detected, @Nullable Rectangle drawn)
	{
		Point p0 = Perspective.localToCanvas(client, wv.getId(), lp.getX(), lp.getY(), tileHeight - baseHeight);
		Point p20 = Perspective.localToCanvas(client, wv.getId(), lp.getX(), lp.getY(), tileHeight - baseHeight - 20);
		String scale = p0 == null || p20 == null ? "?" : String.format("%.3f", (p0.getY() - p20.getY()) / 20.0);

		boolean advanced = config.advancedDrawing();
		String source = !advanced ? "estimate" : detected != null ? "detected" : drawn != null ? "held" : "none";
		// When no bar is found, the color where it was expected shows whether it's a bar the scan
		// doesn't recognise (a shield, a custom style) or not a bar at all
		String pixel = advanced && detected == null
			? pixelAt(estimate.x + estimate.width / 2, estimate.y + BAR_HEIGHT / 2)
			: "-";

		log.info("healthindicators debug frame={} t={} npc={} id={} index={} rule={} maxHp={} dead={} ratio={}/{} "
				+ "logicalHeight={} animOffset={} local={},{} zoom={} pitch={} yaw={} scalePxPerUnit={} "
				+ "estimate={},{} {}px detected={} dx={} dy={} source={} pixel={} drawn={} highDetail={}",
			frame, System.currentTimeMillis(), npc.getName(), npc.getId(), npc.getIndex(), rule.getNamePattern().pattern(),
			npcManager.getHealth(npc.getId()), npc.isDead(), npc.getHealthRatio(), npc.getHealthScale(),
			npc.getLogicalHeight(), npc.getAnimationHeightOffset(), lp.getX(), lp.getY(),
			client.getScale(), client.getCameraPitch(), client.getCameraYaw(), scale,
			estimate.x, estimate.y, estimate.width,
			detected == null ? "none" : detected.x + "," + detected.y + " " + detected.width + "px",
			detected == null ? "-" : detected.x - estimate.x,
			detected == null ? "-" : detected.y - estimate.y,
			source, pixel,
			drawn == null ? "none" : drawn.x + "," + drawn.y + " " + drawn.width + "px", highDetail);
	}

	private String pixelAt(int x, int y)
	{
		BufferProvider buffer = client.getBufferProvider();
		if (buffer == null || x < 0 || y < 0 || x >= buffer.getWidth() || y >= buffer.getHeight())
		{
			return "offscreen";
		}
		return String.format("#%06X", buffer.getPixels()[y * buffer.getWidth() + x] & 0xFFFFFF);
	}
}
