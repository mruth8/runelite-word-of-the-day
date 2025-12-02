package com.wordoftheday;

import lombok.Value;

@Value
public class WordAndDefinition
{
	String word;
	String definition;
	
	public String format(String source)
	{
		if (definition != null && !definition.isEmpty())
		{
			return source + ": " + word + " - " + definition;
		}
		return source + ": " + word;
	}
}

