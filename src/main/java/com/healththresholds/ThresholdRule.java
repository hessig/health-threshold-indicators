package com.healththresholds;

import java.util.List;
import java.util.regex.Pattern;
import lombok.Value;

@Value
class ThresholdRule
{
	Pattern namePattern;
	List<Threshold> thresholds;

	boolean matches(String npcName)
	{
		return namePattern.matcher(npcName).matches();
	}
}
