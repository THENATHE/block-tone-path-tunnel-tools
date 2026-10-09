package com.thenathe.blocktools.material;

import java.util.Locale;

/** Parses the source-only selector used by /blocktone shuffle. */
public record ShuffleSelector(BlockFilter from) {
	public static ShuffleSelector parse(final String input) {
		if (input == null || input.isBlank()) {
			throw new IllegalArgumentException("Shuffle requires from:<blocks>.");
		}
		String specification = input.trim();
		if (!specification.toLowerCase(Locale.ROOT).startsWith("from:")) {
			throw new IllegalArgumentException("Shuffle requires from:<blocks> and does not use to:.");
		}
		String value = specification.substring("from:".length()).trim();
		if (value.isEmpty()) throw new IllegalArgumentException("The shuffle from: selector needs a block list.");

		String compact = value.replaceAll("\\s*,\\s*", ",");
		if (compact.chars().anyMatch(Character::isWhitespace)) {
			throw new IllegalArgumentException("Shuffle accepts exactly one from:<blocks> selector.");
		}
		return new ShuffleSelector(BlockFilter.parse(compact));
	}
}
