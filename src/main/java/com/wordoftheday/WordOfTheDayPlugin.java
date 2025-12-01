package com.wordoftheday;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ColorUtil;

@Slf4j
@PluginDescriptor(
	name = "Word of the Day",
	description = "Displays the word of the day in chat upon login",
	tags = {"word", "daily", "chat", "education"}
)
public class WordOfTheDayPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private WordOfTheDayConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private WordOfTheDayService wordOfTheDayService;

	private boolean hasShownWordToday = false;
	private String lastDateChecked = "";

	@Override
	protected void startUp() throws Exception
	{
		log.info("Word of the Day plugin started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Word of the Day plugin stopped!");
		hasShownWordToday = false;
		lastDateChecked = "";
	}

	private int loginTickDelay = 0;

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Check if player just logged in
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Reset delay counter
			loginTickDelay = 3; // Wait 3 game ticks (about 1.8 seconds) for chat to be ready
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Only show word if we're logged in and delay has passed
		if (client.getGameState() == GameState.LOGGED_IN && loginTickDelay > 0)
		{
			loginTickDelay--;
			if (loginTickDelay == 0 && config.showOnLogin() && shouldShowWord())
			{
				showWordOfTheDay();
			}
		}
	}

	private boolean shouldShowWord()
	{
		// Check if we've already shown the word today
		String today = java.time.LocalDate.now().toString();
		
		if (today.equals(lastDateChecked) && hasShownWordToday)
		{
			return false;
		}

		// Reset if it's a new day
		if (!today.equals(lastDateChecked))
		{
			hasShownWordToday = false;
			lastDateChecked = today;
		}

		return !hasShownWordToday;
	}

	private void showWordOfTheDay()
	{
		// Fetch word of the day asynchronously
		wordOfTheDayService.fetchWordOfTheDay()
			.thenAccept(word -> {
				if (word != null && !word.isEmpty())
				{
					String message = ColorUtil.wrapWithColorTag(
						"Word of the Day: " + word,
						config.messageColor()
					);
					
					final String chatMessage = new ChatMessageBuilder()
						.append(message)
						.build();

					// ChatMessageManager.queue() is thread-safe and can be called from any thread
					chatMessageManager.queue(
						QueuedMessage.builder()
							.type(ChatMessageType.GAMEMESSAGE)
							.runeLiteFormattedMessage(chatMessage)
							.build()
					);

					hasShownWordToday = true;
					log.info("Displayed word of the day: {}", word);
				}
				else
				{
					log.warn("Failed to fetch word of the day");
				}
			})
			.exceptionally(throwable -> {
				log.error("Error fetching word of the day", throwable);
				return null;
			});
	}

	@Provides
	WordOfTheDayConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WordOfTheDayConfig.class);
	}
}
