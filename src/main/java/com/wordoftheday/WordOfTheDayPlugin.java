package com.wordoftheday;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
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
	private boolean hasShownMedievalWordToday = false;
	private boolean hasShownCustomWordToday = false;
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
		hasShownMedievalWordToday = false;
		hasShownCustomWordToday = false;
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
			if (loginTickDelay == 0)
			{
				if (config.wordOfTheDay() && shouldShowWord())
				{
					showWordOfTheDay();
				}
				if (config.medievalWordOfTheDay() && shouldShowMedievalWord())
				{
					showMedievalWordOfTheDay();
				}
				if (config.customWordOfTheDay() && shouldShowCustomWord())
				{
					showCustomWordOfTheDay();
				}
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

	private boolean shouldShowMedievalWord()
	{
		// Check if we've already shown the medieval word today
		String today = java.time.LocalDate.now().toString();
		
		if (today.equals(lastDateChecked) && hasShownMedievalWordToday)
		{
			return false;
		}

		// Reset if it's a new day
		if (!today.equals(lastDateChecked))
		{
			hasShownMedievalWordToday = false;
			lastDateChecked = today;
		}

		return !hasShownMedievalWordToday;
	}

	private boolean shouldShowCustomWord()
	{
		// Check if we've already shown the custom word today
		String today = java.time.LocalDate.now().toString();
		
		if (today.equals(lastDateChecked) && hasShownCustomWordToday)
		{
			return false;
		}

		// Reset if it's a new day
		if (!today.equals(lastDateChecked))
		{
			hasShownCustomWordToday = false;
			lastDateChecked = today;
		}

		return !hasShownCustomWordToday;
	}

	private void showWordOfTheDay()
	{
		// Fetch words of the day from all sources asynchronously
		wordOfTheDayService.fetchAllWordsOfTheDay()
			.thenAccept(words -> {
				if (words != null && !words.isEmpty())
				{
					// Display each word on a separate line
					for (String wordEntry : words)
					{
						// Build message with color tags - pass directly to runeLiteFormattedMessage
						// ChatMessageBuilder.append() escapes HTML-like tags, so we build the message directly
						String coloredMessage = ColorUtil.wrapWithColorTag(wordEntry, config.messageColor());

						// ChatMessageManager.queue() is thread-safe and can be called from any thread
						chatMessageManager.queue(
							QueuedMessage.builder()
								.type(ChatMessageType.GAMEMESSAGE)
								.runeLiteFormattedMessage(coloredMessage)
								.build()
						);
					}

					hasShownWordToday = true;
					log.info("Displayed words of the day from all sources");
				}
				else
				{
					log.warn("Failed to fetch words of the day");
				}
			})
			.exceptionally(throwable -> {
				log.error("Error fetching words of the day", throwable);
				return null;
			});
	}

	private void showMedievalWordOfTheDay()
	{
		// Fetch medieval word of the day asynchronously
		wordOfTheDayService.fetchMedievalWordOfTheDay()
			.thenAccept(result -> {
				if (result != null && result.getWord() != null && !result.getWord().isEmpty())
				{
					String message = result.format("Medieval Word of the Day");
					String coloredMessage = ColorUtil.wrapWithColorTag(message, config.messageColor());

					chatMessageManager.queue(
						QueuedMessage.builder()
							.type(ChatMessageType.GAMEMESSAGE)
							.runeLiteFormattedMessage(coloredMessage)
							.build()
					);

					hasShownMedievalWordToday = true;
					log.info("Displayed medieval word of the day: {}", result.getWord());
				}
				else
				{
					log.warn("Failed to fetch medieval word of the day");
				}
			})
			.exceptionally(throwable -> {
				log.error("Error fetching medieval word of the day", throwable);
				return null;
			});
	}

	private void showCustomWordOfTheDay()
	{
		// Fetch custom word of the day asynchronously
		wordOfTheDayService.fetchCustomWordOfTheDay()
			.thenAccept(result -> {
				if (result != null && result.getWord() != null && !result.getWord().isEmpty())
				{
					String message = result.format("Custom Word of the Day");
					String coloredMessage = ColorUtil.wrapWithColorTag(message, config.messageColor());

					chatMessageManager.queue(
						QueuedMessage.builder()
							.type(ChatMessageType.GAMEMESSAGE)
							.runeLiteFormattedMessage(coloredMessage)
							.build()
					);

					hasShownCustomWordToday = true;
					log.info("Displayed custom word of the day: {}", result.getWord());
				}
				else
				{
					log.warn("Failed to fetch custom word of the day");
				}
			})
			.exceptionally(throwable -> {
				log.error("Error fetching custom word of the day", throwable);
				return null;
			});
	}

	@Provides
	WordOfTheDayConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WordOfTheDayConfig.class);
	}
}
