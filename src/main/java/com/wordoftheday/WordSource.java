package com.wordoftheday;

public enum WordSource
{
	MERRIAM_WEBSTER("Merriam-Webster"),
	DICTIONARY_COM("Dictionary.com"),
	WORDSMITH("Wordsmith.org"),
	CUSTOM("Custom URL");

	private final String name;

	WordSource(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}

