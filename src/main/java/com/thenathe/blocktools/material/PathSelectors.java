package com.thenathe.blocktools.material;

import java.util.Locale;
import java.util.regex.Pattern;

/** Position-independent source, path, and stair clauses for {@code /pathtool}. */
public record PathSelectors(String from, String to, String stairs) {
	private static final Pattern TO_SELECTOR = Pattern.compile("(?:^|\\s)to:", Pattern.CASE_INSENSITIVE);

	public static PathSelectors parse(final String input) {
		if (input == null || input.isBlank()) throw new IllegalArgumentException("A to: selector is required.");
		if (!TO_SELECTOR.matcher(input.trim()).find()) {
			throw new IllegalArgumentException("A to: selector is required; use to:<materials>.");
		}
		String from = null;
		String to = null;
		String stairs = null;
		for (String token : input.trim().split("\\s+")) {
			String lower = token.toLowerCase(Locale.ROOT);
			if (lower.startsWith("from:")) {
				if (from != null) throw new IllegalArgumentException("The from: selector appears more than once.");
				from = value(token, "from:", "block, block list, or all");
			} else if (lower.startsWith("to:")) {
				if (to != null) throw new IllegalArgumentException("The to: selector appears more than once.");
				to = value(token, "to:", "path material palette");
			} else if (lower.startsWith("stairs:")) {
				if (stairs != null) throw new IllegalArgumentException("The stairs: selector appears more than once.");
				stairs = value(token, "stairs:", "stair material palette");
			} else {
				throw new IllegalArgumentException("Unknown Path Tool clause '" + token
					+ "'. Use from:<blocks>, to:<materials>, and optionally stairs:<materials>.");
			}
		}
		if (to == null) throw new IllegalArgumentException("A to: selector is required.");
		return new PathSelectors(from, to, stairs);
	}

	private static String value(final String token, final String prefix, final String expected) {
		String value = token.substring(prefix.length());
		if (value.isBlank()) throw new IllegalArgumentException("The " + prefix + " selector needs a " + expected + ".");
		return value;
	}
}
