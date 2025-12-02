package com.wordoftheday;

import java.util.ArrayList;
import java.util.List;
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
	private static final String OLD_ENGLISH_WORDHORD_API = "https://oldenglishwordhord.com/";

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private WordOfTheDayConfig config;

	/**
	 * Fetches the word of the day from Merriam-Webster (always)
	 * @return CompletableFuture containing the word of the day, or null if failed
	 */
	public CompletableFuture<String> fetchWordOfTheDay()
	{
		return CompletableFuture.supplyAsync(() -> {
			try
			{
				WordAndDefinition result = fetchFromMerriamWebster();
				return result != null ? result.getWord() : null;
			}
			catch (Exception e)
			{
				log.error("Error fetching word of the day", e);
				return null;
			}
		});
	}

	/**
	 * Fetches words of the day from all available sources
	 * @return CompletableFuture containing a list of formatted strings like "Source: word - definition"
	 */
	public CompletableFuture<List<String>> fetchAllWordsOfTheDay()
	{
		return CompletableFuture.supplyAsync(() -> {
			List<String> results = new ArrayList<>();
			
			// Fetch from Merriam-Webster
			WordAndDefinition mwResult = fetchFromMerriamWebster();
			if (mwResult != null && mwResult.getWord() != null && !mwResult.getWord().isEmpty())
			{
				results.add(mwResult.format("Word of the day"));
			}
			else
			{
				results.add("Word of the day: Failed to fetch");
			}
			
			return results;
		});
	}

	/**
	 * Fetches the medieval word of the day from Old English Wordhord
	 * @return CompletableFuture containing WordAndDefinition with the medieval word
	 */
	public CompletableFuture<WordAndDefinition> fetchMedievalWordOfTheDay()
	{
		return CompletableFuture.supplyAsync(() -> {
			return fetchFromOldEnglishWordhord();
		});
	}

	/**
	 * Fetches the custom word of the day from a user-specified URL
	 * @return CompletableFuture containing WordAndDefinition with the custom word
	 */
	public CompletableFuture<WordAndDefinition> fetchCustomWordOfTheDay()
	{
		return CompletableFuture.supplyAsync(() -> {
			return fetchFromCustomUrl();
		});
	}

	/**
	 * Fetches word of the day from Merriam-Webster
	 */
	private WordAndDefinition fetchFromMerriamWebster()
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

				String word = null;
				String definition = null;

				// Try multiple selectors to find the word
				Element wordElement = null;
				
				// Try specific selectors first (more likely to be the actual word)
				String[] specificSelectors = {
					"[data-word]",
					".wod-headword",
					"h1.word-header-txt",
					".word-and-pronunciation h1",
					".wotd-word",
					"h1.wotd-word",
					".word-header h1",
					"h1.word"
				};
				
				for (String selector : specificSelectors)
				{
					wordElement = doc.selectFirst(selector);
					if (wordElement != null)
					{
						String extractedWord = extractWord(wordElement.text());
						if (isValidWord(extractedWord) && extractedWord.length() >= 4)
						{
							word = extractedWord;
							break;
						}
					}
				}
				
				// Fallback: try general selectors
				if (word == null)
				{
					String[] generalSelectors = {
						"article h1",
						"main h1",
						".word-header h1",
						"h1[class*=word]"
					};
					
					for (String selector : generalSelectors)
					{
						wordElement = doc.selectFirst(selector);
						if (wordElement != null)
						{
							String text = wordElement.text().trim();
							String lowerText = text.toLowerCase();
							if (lowerText.contains("word of the day") || 
							    lowerText.equals("word") ||
							    lowerText.equals("day") ||
							    lowerText.startsWith("what") ||
							    lowerText.startsWith("how") ||
							    lowerText.startsWith("when") ||
							    lowerText.startsWith("where") ||
							    lowerText.startsWith("why"))
							{
								continue;
							}
							String extractedWord = extractWord(text);
							if (isValidWord(extractedWord) && extractedWord.length() >= 4)
							{
								word = extractedWord;
								break;
							}
						}
					}
				}
				
				// Try meta tags as last resort for word
				if (word == null)
				{
					Element metaWord = doc.selectFirst("meta[property='og:title'], meta[name='twitter:title']");
					if (metaWord != null)
					{
						String content = metaWord.attr("content");
						if (content != null && !content.isEmpty())
						{
							String[] parts = content.split(":");
							if (parts.length > 1)
							{
								String extractedWord = extractWord(parts[parts.length - 1].trim());
								if (isValidWord(extractedWord) && extractedWord.length() >= 4)
								{
									word = extractedWord;
								}
							}
						}
					}
				}

				// Now find the definition
				if (word != null)
				{
					// Try to find definition in various containers
					String[] definitionSelectors = {
						".wod-definition-container p",
						".wod-definition-text-container p",
						".word-definition p",
						".definition p",
						".wotd-definition p",
						"p.wod-definition",
						".wod-definition-container",
						".wod-definition-text-container"
					};
					
					for (String selector : definitionSelectors)
					{
						Element defElement = doc.selectFirst(selector);
						if (defElement != null)
						{
							String defText = defElement.text().trim();
							// Get first paragraph or first 200 characters
							if (defText.length() > 10 && defText.length() < 500)
							{
								definition = defText;
								break;
							}
							else if (defText.length() >= 500)
							{
								// Take first sentence or first 200 chars
								int endIndex = Math.min(defText.indexOf('.'), 200);
								if (endIndex > 10)
								{
									definition = defText.substring(0, endIndex + 1);
								}
								else
								{
									definition = defText.substring(0, Math.min(200, defText.length()));
								}
								break;
							}
						}
					}
					
					// Fallback: look for any paragraph near the word
					if (definition == null && wordElement != null)
					{
						Element parent = wordElement.parent();
						if (parent != null)
						{
							Element defPara = parent.selectFirst("p");
							if (defPara != null)
							{
								String defText = defPara.text().trim();
								if (defText.length() > 10)
								{
									int endIndex = Math.min(defText.indexOf('.'), 200);
									if (endIndex > 10)
									{
										definition = defText.substring(0, endIndex + 1);
									}
									else
									{
										definition = defText.substring(0, Math.min(200, defText.length()));
									}
								}
							}
						}
					}
				}

				if (word != null)
				{
					return new WordAndDefinition(word, definition);
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
	private WordAndDefinition fetchFromDictionaryCom()
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

				String word = null;
				String definition = null;
				Element wordElement = null;
				
				// Dictionary.com structure - try multiple selectors
				String[] specificSelectors = {
					"h1.wotd-item__headword",
					".wotd-item-headword",
					"h1[class*=headword]",
					".wotd-headword",
					"h1.wotd-headword",
					"[data-headword]",
					".otd-item-headword",
					"h1.otd-item-headword",
					".wotd-item__headword",
					"h1[data-headword]"
				};
				
				for (String selector : specificSelectors)
				{
					wordElement = doc.selectFirst(selector);
					if (wordElement != null)
					{
						String extractedWord = extractWord(wordElement.text());
						if (isValidWord(extractedWord) && extractedWord.length() >= 4)
						{
							word = extractedWord;
							break;
						}
					}
				}
				
				// Fallback: try general selectors but filter
				if (word == null)
				{
					String[] generalSelectors = {
						"article h1",
						"main h1",
						".wotd-item h1",
						"[class*=wotd] h1"
					};
					
					for (String selector : generalSelectors)
					{
						wordElement = doc.selectFirst(selector);
						if (wordElement != null)
						{
							String text = wordElement.text().trim();
							String lowerText = text.toLowerCase();
							if (lowerText.contains("word of the day") || 
							    lowerText.equals("word") ||
							    lowerText.length() < 3)
							{
								continue;
							}
							String extractedWord = extractWord(text);
							if (isValidWord(extractedWord) && extractedWord.length() >= 4)
							{
								word = extractedWord;
								break;
							}
						}
					}
				}
				
				// Try looking for the word in meta tags or structured data
				if (word == null)
				{
					Element metaWord = doc.selectFirst("meta[property='og:title'], meta[name='twitter:title']");
					if (metaWord != null)
					{
						String content = metaWord.attr("content");
						if (content != null && !content.isEmpty())
						{
							String extractedWord = extractWord(content);
							if (isValidWord(extractedWord) && extractedWord.length() >= 4)
							{
								word = extractedWord;
							}
						}
					}
				}

				// Find definition
				if (word != null)
				{
					String[] definitionSelectors = {
						".wotd-item__definition p",
						".wotd-item__definition",
						".otd-item__definition p",
						".wotd-definition p",
						"p.wotd-definition"
					};
					
					for (String selector : definitionSelectors)
					{
						Element defElement = doc.selectFirst(selector);
						if (defElement != null)
						{
							String defText = defElement.text().trim();
							if (defText.length() > 10)
							{
								int endIndex = Math.min(defText.indexOf('.', 50), 200);
								if (endIndex > 10)
								{
									definition = defText.substring(0, endIndex + 1);
								}
								else
								{
									definition = defText.substring(0, Math.min(200, defText.length()));
								}
								break;
							}
						}
					}
				}

				if (word != null)
				{
					return new WordAndDefinition(word, definition);
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
	private WordAndDefinition fetchFromWordsmith()
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

				String word = null;
				String definition = null;
				Element wordElement = null;

				// Wordsmith.org structure - the word is usually in a specific container
				String[] selectors = {
					"table[width='100%'] h2",
					"table h2",
					"h2",
					".word"
				};
				
				for (String selector : selectors)
				{
					wordElement = doc.selectFirst(selector);
					if (wordElement != null)
					{
						String text = wordElement.text().trim();
						String lowerText = text.toLowerCase();
						if (lowerText.contains("a word a day") || 
						    lowerText.contains("wordsmith") ||
						    lowerText.contains("word of the day") ||
						    lowerText.equals("word") ||
						    lowerText.length() < 3)
						{
							continue;
						}
						
						// Extract just the word (first word, removing punctuation)
						String[] parts = text.split("\\s+");
						for (String part : parts)
						{
							String cleaned = part.replaceAll("[^a-zA-Z]", "");
							if (isValidWord(cleaned) && cleaned.length() >= 4)
							{
								word = cleaned;
								break;
							}
						}
						if (word != null)
						{
							break;
						}
					}
				}
				
				// Last resort: look for bold text that's likely the word
				if (word == null)
				{
					for (Element bold : doc.select("b, strong"))
					{
						String text = bold.text().trim();
						String extractedWord = extractWord(text);
						if (isValidWord(extractedWord) && extractedWord.length() >= 4)
						{
							word = extractedWord;
							wordElement = bold;
							break;
						}
					}
				}

				// Find definition (usually in a table cell or paragraph near the word)
				if (word != null && wordElement != null)
				{
					// Look for definition in the same table or nearby
					Element parent = wordElement.parent();
					if (parent != null)
					{
						// Try to find definition in table cells or paragraphs
						Element defElement = parent.selectFirst("td p, td, p");
						if (defElement == null)
						{
							defElement = parent.parent().selectFirst("td p, td, p");
						}
						if (defElement != null)
						{
							String defText = defElement.text().trim();
							if (defText.length() > 10 && !defText.toLowerCase().contains(word.toLowerCase()))
							{
								int endIndex = Math.min(defText.indexOf('.', 50), 200);
								if (endIndex > 10)
								{
									definition = defText.substring(0, endIndex + 1);
								}
								else
								{
									definition = defText.substring(0, Math.min(200, defText.length()));
								}
							}
						}
					}
				}

				if (word != null)
				{
					return new WordAndDefinition(word, definition);
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
	private WordAndDefinition fetchFromCustomUrl()
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

				String word = null;
				String definition = null;
				
				// Try to find word using custom selector or common patterns
				String customSelector = config.customSelector();
				Element wordElement = null;
				
				if (customSelector != null && !customSelector.isEmpty())
				{
					wordElement = doc.selectFirst(customSelector);
					if (wordElement != null)
					{
						word = extractWord(wordElement.text());
						if (isValidWord(word) && word.length() >= 4)
						{
							// Try to find definition nearby
							Element parent = wordElement.parent();
							if (parent != null)
							{
								Element defElement = parent.selectFirst("p, .definition, .def");
								if (defElement != null)
								{
									String defText = defElement.text().trim();
									if (defText.length() > 10)
									{
										int endIndex = Math.min(defText.indexOf('.', 50), 200);
										if (endIndex > 10)
										{
											definition = defText.substring(0, endIndex + 1);
										}
										else
										{
											definition = defText.substring(0, Math.min(200, defText.length()));
										}
									}
								}
							}
							return new WordAndDefinition(word, definition);
						}
					}
				}

				// Fallback: try common selectors
				wordElement = doc.selectFirst("h1, h2, .word, .wotd, [class*=word]");
				if (wordElement != null)
				{
					word = extractWord(wordElement.text());
					if (isValidWord(word) && word.length() >= 4)
					{
						return new WordAndDefinition(word, null);
					}
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

	/**
	 * Fetches word of the day from Old English Wordhord
	 */
	private WordAndDefinition fetchFromOldEnglishWordhord()
	{
		try
		{
			Request request = new Request.Builder()
				.url(OLD_ENGLISH_WORDHORD_API)
				.build();

			try (Response response = okHttpClient.newCall(request).execute())
			{
				if (!response.isSuccessful())
				{
					log.warn("Old English Wordhord API returned: {}", response.code());
					return null;
				}

				String html = response.body().string();
				Document doc = Jsoup.parse(html);

				String word = null;
				String definition = null;

				// The word of the day is typically in the first post/article on the main page
				// Look for the first entry/article/post
				Element firstPost = doc.selectFirst("article.post, article, .entry, .post, .word-entry");
				
				if (firstPost == null)
				{
					// Try finding posts by class or structure
					firstPost = doc.selectFirst("[class*='post'], [class*='entry']");
				}
				
				if (firstPost != null)
				{
					// Extract word from the post heading - usually h1 or h2
					Element wordElement = firstPost.selectFirst("h1, h2, .entry-title, .post-title, h1.entry-title");
					if (wordElement == null)
					{
						// Try finding any h1 or h2 in the post
						wordElement = firstPost.selectFirst("h1, h2");
					}
					
					if (wordElement != null)
					{
						String wordText = wordElement.text().trim();
						// The word is usually the first word or first line
						// Remove any "Posted on" or date info
						if (!wordText.toLowerCase().contains("posted") && 
						    !wordText.toLowerCase().contains("old english wordhord"))
						{
							// Extract just the word (first word, may contain special chars)
							String[] parts = wordText.split("\\s+");
							if (parts.length > 0)
							{
								word = parts[0].trim();
								// Keep Old English characters: āēīōūǣȳæþðƿ
								word = word.replaceAll("[^a-zA-Zāēīōūǣȳæþðƿ-]", "").trim();
							}
						}
					}
					
					// Extract definition from the post content
					// The definition follows the pattern: "word, part-of-speech: definition. (pronunciation)"
					// Look for all text elements in the post to find the definition line
					String fullPostText = firstPost.text();
					
					// Try to find the line that matches the pattern: word, type: definition
					// Look for lines containing the word followed by a comma and colon
					if (word != null)
					{
						// Search for pattern: "word, " followed by part of speech, colon, and definition
						String pattern = word + ",\\s*[^:]+:\\s*([^.]+)\\.";
						java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
						java.util.regex.Matcher matcher = regex.matcher(fullPostText);
						
						if (matcher.find())
						{
							definition = matcher.group(1).trim();
							// Remove any pronunciation that might be in the definition
							definition = definition.replaceAll("\\([^)]*\\/[^)]*\\)", "").trim();
						}
						else
						{
							// Fallback: look for the word followed by colon in any paragraph
							for (Element p : firstPost.select("p"))
							{
								String text = p.text().trim();
								// Check if this paragraph contains the word followed by a colon
								if (text.contains(word + ",") || text.contains(word + ":"))
								{
									// Extract text after the colon, before the period and pronunciation
									int colonIndex = text.indexOf(":");
									if (colonIndex > 0)
									{
										String afterColon = text.substring(colonIndex + 1).trim();
										// Find the period before the pronunciation
										int periodIndex = afterColon.indexOf(".");
										if (periodIndex > 0)
										{
											definition = afterColon.substring(0, periodIndex).trim();
											// Remove any pronunciation that might be included
											definition = definition.replaceAll("\\([^)]*\\/[^)]*\\)", "").trim();
										}
										else
										{
											// No period found, take up to first pronunciation or 200 chars
											definition = afterColon.replaceAll("\\([^)]*\\/[^)]*\\)", "").trim();
											if (definition.length() > 200)
											{
												definition = definition.substring(0, 200).trim();
											}
										}
										if (definition != null && definition.length() > 3)
										{
											break;
										}
									}
								}
							}
						}
					}
					
					// Limit definition length
					if (definition != null && definition.length() > 200)
					{
						definition = definition.substring(0, 200).trim();
						if (!definition.endsWith(".") && !definition.endsWith("..."))
						{
							definition += "...";
						}
					}
				}
				
				// Fallback: try to find word in main content area if post structure not found
				if (word == null)
				{
					Element mainContent = doc.selectFirst("main, .main-content, #content, #main");
					if (mainContent != null)
					{
						// Look for the first h1 or h2 that's not a page title
						for (Element heading : mainContent.select("h1, h2"))
						{
							String headingText = heading.text().trim();
							if (!headingText.toLowerCase().contains("old english") &&
							    !headingText.toLowerCase().contains("wordhord") &&
							    !headingText.toLowerCase().contains("navigation") &&
							    !headingText.toLowerCase().contains("word of the day") &&
							    headingText.length() < 50 && headingText.length() > 1)
							{
								word = headingText.split("\\s+")[0].trim();
								word = word.replaceAll("[^a-zA-Zāēīōūǣȳæþðƿ-]", "").trim();
								if (word.length() >= 2)
								{
									break;
								}
							}
						}
					}
				}

				if (word != null && !word.isEmpty())
				{
					return new WordAndDefinition(word, definition);
				}

				log.warn("Could not find word element on Old English Wordhord page");
				return null;
			}
		}
		catch (Exception e)
		{
			log.error("Error fetching from Old English Wordhord", e);
			return null;
		}
	}
	
	/**
	 * Extracts a valid word from text, filtering out common phrases
	 */
	private String extractWord(String text)
	{
		if (text == null || text.isEmpty())
		{
			return null;
		}
		
		text = text.trim();
		
		// Common phrases to skip
		String[] skipPhrases = {
			"word of the day",
			"word of day",
			"today's word",
			"the word",
			"word",
			"day",
			"what is",
			"what's",
			"how to",
			"when is",
			"where is",
			"why is"
		};
		
		String lowerText = text.toLowerCase();
		for (String phrase : skipPhrases)
		{
			if (lowerText.equals(phrase) || lowerText.startsWith(phrase + " "))
			{
				// Try to extract word after the phrase
				String remaining = text.substring(phrase.length()).trim();
				if (!remaining.isEmpty())
				{
					String[] words = remaining.split("\\s+");
					if (words.length > 0)
					{
						String candidate = words[0].replaceAll("[^a-zA-Z]", "");
						if (candidate.length() > 2 && !candidate.toLowerCase().equals("the"))
						{
							return candidate;
						}
					}
				}
				return null;
			}
		}
		
		// Extract first word, removing punctuation
		String[] words = text.split("\\s+");
		for (String w : words)
		{
			String cleaned = w.replaceAll("[^a-zA-Z]", "");
			String lowerCleaned = cleaned.toLowerCase();
			// Skip common words, question words, and definition words
			if (cleaned.length() >= 4 && 
			    !lowerCleaned.equals("the") && 
			    !lowerCleaned.equals("word") && 
			    !lowerCleaned.equals("day") &&
			    !lowerCleaned.equals("what") &&
			    !lowerCleaned.equals("how") &&
			    !lowerCleaned.equals("when") &&
			    !lowerCleaned.equals("where") &&
			    !lowerCleaned.equals("why") &&
			    !lowerCleaned.equals("who") &&
			    !lowerCleaned.equals("which") &&
			    !lowerCleaned.equals("this") &&
			    !lowerCleaned.equals("that") &&
			    !lowerCleaned.equals("means") &&
			    !lowerCleaned.equals("mean") &&
			    !lowerCleaned.equals("example") &&
			    !lowerCleaned.equals("definition") &&
			    !lowerCleaned.equals("pronunciation") &&
			    !lowerCleaned.equals("awordaday") &&
			    !lowerCleaned.equals("wordsmith"))
			{
				return cleaned;
			}
		}
		
		// If no good word found, return first alphanumeric word
		if (words.length > 0)
		{
			return words[0].replaceAll("[^a-zA-Z]", "");
		}
		
		return text.replaceAll("[^a-zA-Z]", "");
	}
	
	/**
	 * Validates that a word is actually a word (not a common phrase or too short)
	 */
	private boolean isValidWord(String word)
	{
		if (word == null || word.isEmpty())
		{
			return false;
		}
		
		word = word.trim();
		
		// Must be at least 3 characters
		if (word.length() < 3)
		{
			return false;
		}
		
		// Must be only letters
		if (!word.matches("^[a-zA-Z]+$"))
		{
			return false;
		}
		
		// Skip common stop words, phrases, and definition-related words
		String lower = word.toLowerCase();
		String[] invalidWords = {
			"word", "day", "the", "of", "and", "or", "but", "in", "on", "at", "to", "for",
			"what", "how", "when", "where", "why", "who", "which", "this", "that", "these", "those",
			"means", "mean", "example", "definition", "pronunciation", "awordaday", "wordsmith",
			"with", "from", "into", "onto", "upon", "about", "above", "below", "under", "over"
		};
		
		for (String invalid : invalidWords)
		{
			if (lower.equals(invalid))
			{
				return false;
			}
		}
		
		return true;
	}
}

