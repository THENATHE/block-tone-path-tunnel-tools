package com.thenathe.blocktools.tunnel;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

final class TunnelSuggestions {
	private static final List<String> CLAUSES = List.of(
		"width:", "height:", "to:", "walls:", "floor:", "ceiling:", "rail:"
	);

	private TunnelSuggestions() {
	}

	static CompletableFuture<Suggestions> suggest(final SuggestionsBuilder builder) {
		String tail = builder.getRemaining();
		int tokenStart = 0;
		for (int index = 0; index < tail.length(); index++) {
			if (Character.isWhitespace(tail.charAt(index))) tokenStart = index + 1;
		}
		String token = tail.substring(tokenStart);
		String lower = token.toLowerCase(Locale.ROOT);
		if (lower.startsWith("width:")) return values(builder, tokenStart + 6, List.of("1", "2", "3", "4", "5"));
		if (lower.startsWith("height:")) return values(builder, tokenStart + 7, List.of("2", "3", "4", "5"));
		if (lower.startsWith("rail:") || lower.startsWith("rails:")) {
			return values(builder, tokenStart + token.indexOf(':') + 1, List.of("off", "on", "only"));
		}
		for (String prefix : List.of("to:", "all:", "wall:", "walls:", "floor:", "floors:", "ceiling:", "ceilings:")) {
			if (lower.startsWith(prefix)) return blocks(builder, tokenStart + prefix.length(),
				prefix.startsWith("wall") || prefix.startsWith("ceiling"));
		}
		List<String> remaining = new ArrayList<>();
		for (String clause : CLAUSES) {
			String name = clause.substring(0, clause.length() - 1).toLowerCase(Locale.ROOT) + ":";
			if (!tail.toLowerCase(Locale.ROOT).matches(".*(?:^|\\s)" + java.util.regex.Pattern.quote(name) + ".*")) {
				remaining.add(clause);
			}
		}
		return SharedSuggestionProvider.suggest(remaining, builder.createOffset(builder.getStart() + tokenStart));
	}

	private static CompletableFuture<Suggestions> blocks(final SuggestionsBuilder builder, final int sectionStart, final boolean allowAir) {
		String tail = builder.getRemaining();
		int comma = tail.lastIndexOf(',');
		int start = Math.max(sectionStart, comma < sectionStart ? sectionStart : comma + 1);
		String entry = tail.substring(start);
		if (entry.contains("[")) return builder.buildFuture();
		boolean full = entry.contains(":");
		List<String> names = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			if (!(allowAir && block == net.minecraft.world.level.block.Blocks.AIR) && !TunnelPalette.isSafeLiningBlock(block)) continue;
			String name = full || !"minecraft".equals(id.getNamespace()) ? id.toString() : id.getPath();
			names.add(name + "[100]");
		}
		names.sort(Comparator.naturalOrder());
		return SharedSuggestionProvider.suggest(names, builder.createOffset(builder.getStart() + start));
	}

	private static CompletableFuture<Suggestions> values(
		final SuggestionsBuilder builder,
		final int start,
		final List<String> values
	) {
		return SharedSuggestionProvider.suggest(values, builder.createOffset(builder.getStart() + start));
	}
}
