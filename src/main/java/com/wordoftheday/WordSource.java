package com.wordoftheday;

public enum WordSource
{
	MERRIAM_WEBSTER("Merriam-Webster"),
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

