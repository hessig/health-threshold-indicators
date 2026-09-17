package com.healththresholds;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Value;

@Value
class Threshold
{
	// Bounds work per frame for tiny steps on huge HP pools; the bar is at most a few hundred pixels wide anyway
	private static final int MAX_REPEATS = 10_000;

	double value;
	boolean percent;
	/**
	 * When set, marks repeat every {@code value} (percent or HP) across the bar instead of appearing once.
	 */
	boolean repeating;
	Color color;

	/**
	 * @return this mark as it reads in the UI, such as "50%" or "every 200 HP"
	 */
	String describe()
	{
		String number = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
		String unit = percent ? number + "%" : number + " HP";
		return repeating ? "every " + unit : unit;
	}

	/**
	 * @return positions along the bar from 0 to 1; empty for HP thresholds when the NPC's max HP is unknown
	 */
	List<Double> ratios(@Nullable Integer maxHealth)
	{
		double total;
		if (percent)
		{
			total = 100;
		}
		else if (maxHealth != null && maxHealth > 0)
		{
			total = maxHealth;
		}
		else
		{
			return Collections.emptyList();
		}

		if (!repeating)
		{
			return value <= total ? List.of(value / total) : Collections.emptyList();
		}

		if (value <= 0)
		{
			return Collections.emptyList();
		}

		List<Double> ratios = new ArrayList<>();
		for (int k = 1; k <= MAX_REPEATS && k * value < total - 1e-9; k++)
		{
			ratios.add(k * value / total);
		}
		return ratios;
	}
}
