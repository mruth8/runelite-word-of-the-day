package com.wordoftheday;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import java.awt.Color;

@ConfigGroup("wordoftheday")
public interface WordOfTheDayConfig extends Config
{
	@ConfigItem(
		keyName = "messageColor",
		name = "Message Color",
		description = "Color of the word of the day message in chat",
		position = 0
	)
	default Color messageColor()
	{
		return Color.CYAN;
	}

	@ConfigItem(
		keyName = "wordOfTheDay",
		name = "Word of the Day",
		description = "Display the word of the day when you log in",
		position = 1
	)
	default boolean wordOfTheDay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "medievalWordOfTheDay",
		name = "Word of the Day (Medieval)",
		description = "Display the medieval word of the day when you log in",
		position = 2
	)
	default boolean medievalWordOfTheDay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "customWordOfTheDay",
		name = "Word of the Day (Custom)",
		description = "Display a custom word of the day from a URL you specify",
		position = 3
	)
	default boolean customWordOfTheDay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "customUrl",
		name = "Custom URL",
		description = "Custom URL to scrape for word of the day (only used if Custom Word of the Day is enabled)",
		position = 4
	)
	default String customUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "customSelector",
		name = "Custom CSS Selector",
		description = "CSS selector to find the word on the custom page (e.g., 'h1.word', '.wotd')",
		position = 5
	)
	default String customSelector()
	{
		return "h1";
	}
}
