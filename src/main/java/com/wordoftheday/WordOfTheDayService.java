package com.wordoftheday;

import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@Slf4j
@Singleton
public class WordOfTheDayService
{
	private static final String MERRIAM_WEBSTER_API = "https://www.merriam-webster.com/word-of-the-day";
	private static final String DICTIONARY_COM_API = "https://www.dictionary.com/e/word-of-the-day/";
	private static final String WORDSMITH_ORG_API = "https://wordsmith.org/words/today.html";

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private WordOfTheDayConfig config;

	/**
	 * Fetches the word of the day from the configured source
	 * @return CompletableFuture containing the word of the day, or null if failed
	 */
	public CompletableFuture<String> fetchWordOfTheDay()
	{
		return CompletableFuture.supplyAsync(() -> {
			try
			{
				WordSource source = config.wordSource();
				String word = null;

				switch (source)
				{
					case MERRIAM_WEBSTER:
						word = fetchFromMerriamWebster();
						break;
					case DICTIONARY_COM:
						word = fetchFromDictionaryCom();
						break;
					case WORDSMITH:
						word = fetchFromWordsmith();
						break;
					case CUSTOM:
						word = fetchFromCustomUrl();
						break;
					default:
						word = fetchFromMerriamWebster(); // Default fallback
				}

				return word;
			}
			catch (Exception e)
			{
				log.error("Error fetching word of the day", e);
				return null;
			}
		});
	}

	/**
	 * Fetches word of the day from Merriam-Webster
	 */
	private String fetchFromMerriamWebster()
	{
		try
		{
			Request request = new Request.Builder()
				.url(MERRIAM_WEBSTER_API)
				.build();

			try (Response response = okHttpClient.newCall(request).execute())
			{
				if (!response.isSuccessful())
				{
					log.warn("Merriam-Webster API returned: {}", response.code());
					return null;
				}

				String html = response.body().string();
				Document doc = Jsoup.parse(html);

				// Try to find the word in various possible locations
				Element wordElement = doc.selectFirst("h1.word-header-txt, .word-header h1, .wod-headword");
				if (wordElement != null)
				{
					return wordElement.text().trim();
				}

				// Fallback: look for any h1 with "word" class
				wordElement = doc.selectFirst("h1[class*=word]");
				if (wordElement != null)
				{
					return wordElement.text().trim();
				}

				log.warn("Could not find word element on Merriam-Webster page");
				return null;
			}
		}
		catch (Exception e)
		{
			log.error("Error fetching from Merriam-Webster", e);
			return null;
		}
	}

	/**
	 * Fetches word of the day from Dictionary.com
	 */
	private String fetchFromDictionaryCom()
	{
		try
		{
			Request request = new Request.Builder()
				.url(DICTIONARY_COM_API)
				.build();

			try (Response response = okHttpClient.newCall(request).execute())
			{
				if (!response.isSuccessful())
				{
					log.warn("Dictionary.com API returned: {}", response.code());
					return null;
				}

				String html = response.body().string();
				Document doc = Jsoup.parse(html);

				// Dictionary.com structure
				Element wordElement = doc.selectFirst("h1.wotd-item__headword, .wotd-item-headword, h1[class*=headword]");
				if (wordElement != null)
				{
					return wordElement.text().trim();
				}

				log.warn("Could not find word element on Dictionary.com page");
				return null;
			}
		}
		catch (Exception e)
		{
			log.error("Error fetching from Dictionary.com", e);
			return null;
		}
	}

	/**
	 * Fetches word of the day from Wordsmith.org
	 */
	private String fetchFromWordsmith()
	{
		try
		{
			Request request = new Request.Builder()
				.url(WORDSMITH_ORG_API)
				.build();

			try (Response response = okHttpClient.newCall(request).execute())
			{
				if (!response.isSuccessful())
				{
					log.warn("Wordsmith.org API returned: {}", response.code());
					return null;
				}

				String html = response.body().string();
				Document doc = Jsoup.parse(html);

				// Wordsmith.org structure
				Element wordElement = doc.selectFirst("h2, h3, .word");
				if (wordElement != null)
				{
					String text = wordElement.text().trim();
					// Extract just the word (usually first part before definition)
					String[] parts = text.split("\\s+");
					if (parts.length > 0)
					{
						return parts[0].replaceAll("[^a-zA-Z]", "");
					}
					return text;
				}

				log.warn("Could not find word element on Wordsmith.org page");
				return null;
			}
		}
		catch (Exception e)
		{
			log.error("Error fetching from Wordsmith.org", e);
			return null;
		}
	}

	/**
	 * Fetches word of the day from a custom URL
	 */
	private String fetchFromCustomUrl()
	{
		String customUrl = config.customUrl();
		if (customUrl == null || customUrl.isEmpty())
		{
			log.warn("Custom URL not configured");
			return null;
		}

		try
		{
			HttpUrl url = HttpUrl.parse(customUrl);
			if (url == null)
			{
				log.warn("Invalid custom URL: {}", customUrl);
				return null;
			}

			Request request = new Request.Builder()
				.url(url)
				.build();

			try (Response response = okHttpClient.newCall(request).execute())
			{
				if (!response.isSuccessful())
				{
					log.warn("Custom URL returned: {}", response.code());
					return null;
				}

				String html = response.body().string();
				Document doc = Jsoup.parse(html);

				// Try to find word using custom selector or common patterns
				String customSelector = config.customSelector();
				if (customSelector != null && !customSelector.isEmpty())
				{
					Element wordElement = doc.selectFirst(customSelector);
					if (wordElement != null)
					{
						return wordElement.text().trim();
					}
				}

				// Fallback: try common selectors
				Element wordElement = doc.selectFirst("h1, h2, .word, .wotd, [class*=word]");
				if (wordElement != null)
				{
					return wordElement.text().trim();
				}

				log.warn("Could not find word element on custom URL page");
				return null;
			}
		}
		catch (Exception e)
		{
			log.error("Error fetching from custom URL", e);
			return null;
		}
	}
}

