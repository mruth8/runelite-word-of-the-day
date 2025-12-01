package com.wordoftheday;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import java.awt.Color;

@ConfigGroup("wordoftheday")
public interface WordOfTheDayConfig extends Config
{
	@ConfigItem(
		keyName = "wordSource",
		name = "Word Source",
		description = "Choose the source for the word of the day"
	)
	default WordSource wordSource()
	{
		return WordSource.MERRIAM_WEBSTER;
	}

	@ConfigItem(
		keyName = "customUrl",
		name = "Custom URL",
		description = "Custom URL to scrape for word of the day (only used if source is Custom)",
		hidden = true,
		unhide = "wordSource",
		unhideValue = "CUSTOM"
	)
	default String customUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "customSelector",
		name = "Custom CSS Selector",
		description = "CSS selector to find the word on the custom page (e.g., 'h1.word', '.wotd')",
		hidden = true,
		unhide = "wordSource",
		unhideValue = "CUSTOM"
	)
	default String customSelector()
	{
		return "h1";
	}

	@ConfigItem(
		keyName = "messageColor",
		name = "Message Color",
		description = "Color of the word of the day message in chat"
	)
	default Color messageColor()
	{
		return Color.CYAN;
	}

	@ConfigItem(
		keyName = "showOnLogin",
		name = "Show on Login",
		description = "Display the word of the day when you log in"
	)
	default boolean showOnLogin()
	{
		return true;
	}
}
