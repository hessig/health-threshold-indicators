package com.healththresholds;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.runelite.api.Point;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * Uses the sprites from RuneLite's Interface Styles plugin (2010/healthbar), which replace the
 * standard bars when "High detail health bars" is on.
 */
public class HighDetailBarTest
{
	private static final int W = 300;
	private static final int H = 100;
	private static final int GROUND = 0x303030;
	private static final Point ANCHOR = new Point(150, 50);

	private static BufferedImage sprite(String name) throws IOException
	{
		try (InputStream in = HighDetailBarTest.class.getResourceAsStream("highdetail/" + name))
		{
			return ImageIO.read(in);
		}
	}

	/**
	 * Draws the bar the way the client does: the back sprite, then the front sprite up to the
	 * health fraction, filled inside the 1px border
	 */
	private static int[] frameWithBar(int spriteWidth, int x, int y, double health) throws IOException
	{
		BufferedImage back = sprite("default_back_" + spriteWidth + "px.png");
		BufferedImage front = sprite("default_front_" + spriteWidth + "px.png");
		int frontWidth = 1 + (int) Math.round((spriteWidth - 2) * health);

		int[] pixels = new int[W * H];
		Arrays.fill(pixels, GROUND);
		for (int row = 0; row < back.getHeight(); row++)
		{
			for (int col = 0; col < back.getWidth(); col++)
			{
				int argb = col < frontWidth ? front.getRGB(col, row) : back.getRGB(col, row);
				if ((argb >>> 24) != 0)
				{
					pixels[(y + row) * W + x + col] = argb & 0xFFFFFF;
				}
			}
		}
		return pixels;
	}

	@Test
	public void estimatesTheFillInsideTheBorder()
	{
		assertEquals(new Rectangle(51, 47, 98, 5), HealthIndicatorsOverlay.estimateBar(new Point(100, 50), 100, true));
	}

	@Test
	public void thirtyPixelBarsUseTheFortyPixelSprite()
	{
		assertEquals(new Rectangle(81, 47, 38, 5), HealthIndicatorsOverlay.estimateBar(new Point(100, 50), 30, true));
	}

	@Test
	public void findsTheFillAtAnyHealth() throws IOException
	{
		for (int spriteWidth : new int[]{40, 100})
		{
			for (double health : new double[]{1, 0.75, 0.5, 0.25, 0})
			{
				int x = ANCHOR.getX() - spriteWidth / 2;
				int[] pixels = frameWithBar(spriteWidth, x, 40, health);
				Rectangle estimate = HealthIndicatorsOverlay.estimateBar(ANCHOR, spriteWidth, true);

				assertEquals(spriteWidth + "px at " + health, new Rectangle(x + 1, 41, spriteWidth - 2, 5),
					HealthIndicatorsOverlay.findBar(pixels, W, H, estimate, true));
			}
		}
	}

	@Test
	public void findsTheFillOffsetFromTheEstimate() throws IOException
	{
		int[] pixels = frameWithBar(40, 120, 44, 0.6);
		Rectangle estimate = HealthIndicatorsOverlay.estimateBar(new Point(138, 50), 30, true);

		assertEquals(new Rectangle(121, 45, 38, 5), HealthIndicatorsOverlay.findBar(pixels, W, H, estimate, true));
	}

	@Test
	public void standardScanDoesNotFindHighDetailBars() throws IOException
	{
		// Why marks vanished with high detail bars on: the standard colors never match them
		int[] pixels = frameWithBar(100, 100, 46, 1);
		Rectangle estimate = HealthIndicatorsOverlay.estimateBar(ANCHOR, 100, false);

		assertNull(HealthIndicatorsOverlay.findBar(pixels, W, H, estimate, false));
	}
}
