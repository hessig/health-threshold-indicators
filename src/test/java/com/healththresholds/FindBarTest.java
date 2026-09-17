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
		drawBar(pixels, 100, 32, 30, 30);
		drawBar(pixels, 100, 50, 30, 5);

		assertEquals(new Rectangle(100, 50, 30, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(100, 48, 30, 5)));
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

		assertEquals(new Rectangle(40, 60, 120, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(42, 58, 116, 5)));
	}

	@Test
	public void clampsSearchAtFrameEdges()
	{
		int[] pixels = frame();
		drawBar(pixels, 0, 0, 30, 30);

		assertEquals(new Rectangle(0, 0, 30, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, new Rectangle(-3, -2, 30, 5)));
	}
}
