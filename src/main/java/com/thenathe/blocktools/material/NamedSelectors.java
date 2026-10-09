package com.thenathe.blocktools.material;

import java.util.Locale;
import java.util.regex.Pattern;

/** Position-independent {@code from:}/{@code type:} source and {@code to:} destination clauses. */
public record NamedSelectors(String from, String to) {
	private static final Pattern TO_SELECTOR = Pattern.compile("(?:^|\\s)to:", Pattern.CASE_INSENSITIVE);

	public static NamedSelectors parse(final String input) {
		if (input == null || input.isBlank()) throw new IllegalArgumentException("A to: selector is required.");
		if (!TO_SELECTOR.matcher(input.trim()).find()) {
			throw new IllegalArgumentException("A to: selector is required; use to:<materials>.");
		}
		String from = null;
		String to = null;
		for (String token : input.trim().split("\\s+")) {
			String lower = token.toLowerCase(Locale.ROOT);
			if (lower.startsWith("type:")) {
                if (from != null) throw new IllegalArgumentException("Use either from: or type:, not both or repeated source selectors.");
                if (token.length() == 5) throw new IllegalArgumentException("The type: selector needs a type list.");
                from = token;
            } else if (lower.startsWith("from:")) {
				if (from != null) throw new IllegalArgumentException("The from: selector appears more than once or conflicts with type:.");
				from = token.substring("from:".length());
				if (from.isBlank()) throw new IllegalArgumentException("The from: selector needs a block, block list, or all.");
			} else if (lower.startsWith("to:")) {
				if (to != null) throw new IllegalArgumentException("The to: selector appears more than once.");
				to = token.substring("to:".length());
				if (to.isBlank()) throw new IllegalArgumentException("The to: selector needs a material list.");
			} else {
				throw new IllegalArgumentException("Unknown selector clause '" + token + "'. Use from:<blocks> or type:<types>, and to:<materials>.");
			}
		}
		if (to == null) throw new IllegalArgumentException("A to: selector is required.");
		return new NamedSelectors(from, to);
	}
}
