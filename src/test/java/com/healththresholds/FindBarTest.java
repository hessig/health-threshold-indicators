package com.healththresholds;

import java.awt.Rectangle;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class FindBarTest
{
	private static final int W = 200;
	private static final int H = 100;
	private static final int GREEN = 0x00FF00;
	private static final int RED = 0xFF0000;
	private static final int GROUND = 0x303030;

	private static int[] frame()
	{
		int[] pixels = new int[W * H];
		java.util.Arrays.fill(pixels, GROUND);
		return pixels;
	}

	private static void drawBar(int[] pixels, int x, int y, int width, int greenWidth)
	{
		for (int row = y; row < y + 5; row++)
		{
			for (int col = x; col < x + width; col++)
			{
				pixels[row * W + col] = col < x + greenWidth ? GREEN : RED;
			}
		}
	}

	@Test
	public void findsBarOffsetFromEstimate()
	{
		int[] pixels = frame();
		drawBar(pixels, 96, 47, 30, 20);

		assertEquals(new Rectangle(96, 47, 30, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(100, 44, 30, 5)));
	}

	@Test
	public void prefersBarNearestEstimate()
	{
		int[] pixels = frame();
		drawBar(pixels, 100, 41, 30, 30);
		drawBar(pixels, 100, 51, 30, 5);

		assertEquals(new Rectangle(100, 51, 30, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(100, 49, 30, 5)));
	}

	@Test
	public void noBarReturnsNull()
	{
		assertNull(HealthIndicatorsOverlay.findBar(frame(), W, H, new Rectangle(100, 48, 30, 5)));
	}

	@Test
	public void ignoresShortColoredRuns()
	{
		int[] pixels = frame();
		drawBar(pixels, 100, 48, 12, 12);

		assertNull(HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(100, 48, 30, 5)));
	}

	@Test
	public void measuresWiderBossBar()
	{
		int[] pixels = frame();
		drawBar(pixels, 40, 60, 120, 90);

		assertEquals(new Rectangle(40, 60, 120, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(42, 58, 120, 5)));
	}

	@Test
	public void clampsSearchAtFrameEdges()
	{
		int[] pixels = frame();
		drawBar(pixels, 0, 0, 30, 30);

		assertEquals(new Rectangle(0, 0, 30, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(-3, -2, 30, 5)));
	}

	@Test
	public void rejectsBarWidenedBySceneryTouchingIt()
	{
		// The Hydra bug: red flames right beside a 120px bar were read as bar, giving 165-182px widths
		int[] pixels = frame();
		drawBar(pixels, 40, 60, 120, 90);
		fill(pixels, 160, 60, 40, 5, RED);

		assertNull(HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(40, 60, 120, 5)));
	}

	@Test
	public void sceneryWithAGapDoesNotWidenTheBar()
	{
		int[] pixels = frame();
		drawBar(pixels, 40, 60, 120, 90);
		fill(pixels, 164, 60, 30, 5, GREEN);

		assertEquals(new Rectangle(40, 60, 120, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(40, 60, 120, 5)));
	}

	@Test
	public void bridgesAMarkDrawnAcrossTheBar()
	{
		int[] pixels = frame();
		drawBar(pixels, 100, 48, 30, 20);
		fill(pixels, 115, 48, 1, 5, 0xFFFFFF);

		assertEquals(new Rectangle(100, 48, 30, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(100, 48, 30, 5)));
	}

	private static void fill(int[] pixels, int x, int y, int width, int height, int rgb)
	{
		for (int row = y; row < y + height; row++)
		{
			for (int col = x; col < x + width; col++)
			{
				pixels[row * W + col] = rgb;
			}
		}
	}

	@Test
	public void findsABarThatHasMovedWellAboveTheEstimate()
	{
		// Hydra's bar rode 8px+ above the estimate while it walked; the old window clipped it
		int[] pixels = frame();
		drawBar(pixels, 40, 40, 120, 90);

		assertEquals(new Rectangle(40, 40, 120, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(41, 52, 120, 5)));
	}

	@Test
	public void trimsSceneryTouchingTheRightEnd()
	{
		// Seen in the second Hydra fight: 121-124px runs with the left edge in place
		int[] pixels = frame();
		drawBar(pixels, 40, 60, 120, 90);
		fill(pixels, 160, 60, 3, 5, RED);

		assertEquals(new Rectangle(40, 60, 120, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(40, 60, 120, 5)));
	}

	@Test
	public void trimsSceneryTouchingTheLeftEnd()
	{
		int[] pixels = frame();
		drawBar(pixels, 40, 60, 120, 90);
		fill(pixels, 37, 60, 3, 5, GREEN);

		assertEquals(new Rectangle(40, 60, 120, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(40, 60, 120, 5)));
	}
}
