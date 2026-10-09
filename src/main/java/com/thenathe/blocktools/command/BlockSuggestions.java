package com.thenathe.blocktools.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

final class BlockSuggestions {
	private static final Pattern NAMED_SELECTOR = Pattern.compile("(?:^|\\s)(?:from|type|to):", Pattern.CASE_INSENSITIVE);
	private static final Pattern NAMED_FROM = Pattern.compile("(?:^|\\s)(?:from|type):", Pattern.CASE_INSENSITIVE);
	private static final Pattern NAMED_TO = Pattern.compile("(?:^|\\s)to:", Pattern.CASE_INSENSITIVE);
	private static final Pattern PATH_SELECTOR = Pattern.compile("(?:^|\\s)(?:from|to|stairs):", Pattern.CASE_INSENSITIVE);
	private static final Pattern PATH_STAIRS = Pattern.compile("(?:^|\\s)stairs:", Pattern.CASE_INSENSITIVE);

	private BlockSuggestions() {
	}

	static CompletableFuture<Suggestions> tone(final SuggestionsBuilder builder) {
		String tail = builder.getRemaining();
		if (NAMED_SELECTOR.matcher(tail).find()) return suggestNamed(builder, false);
		if (tail.isEmpty()) return suggestRoot(builder);
		if (selectorPrefix(tail)) return suggestSelectorNames(builder, 0, false, false);
		return suggestSelectorNames(builder, 0, false, false);
	}

	static CompletableFuture<Suggestions> shuffle(final SuggestionsBuilder builder) {
		String tail = builder.getRemaining();
		String lower = tail.toLowerCase(java.util.Locale.ROOT);
		if (lower.startsWith("from:")) return suggestCurrentSource(builder, "from:".length());
		return SharedSuggestionProvider.suggest(List.of("from:"), builder);
	}

	static CompletableFuture<Suggestions> path(final SuggestionsBuilder builder) {
		String tail = builder.getRemaining();
		if (PATH_SELECTOR.matcher(tail).find()) return suggestNamedPath(builder);
		if (tail.isEmpty()) return suggestPathRoot(builder);
		if (pathSelectorPrefix(tail)) return suggestPathSelectorNames(builder, 0, false, false, false);
		return suggestPathSelectorNames(builder, 0, false, false, false);
	}

	private static CompletableFuture<Suggestions> suggestNamedPath(final SuggestionsBuilder builder) {
		String tail = builder.getRemaining();
		int tokenStart = 0;
		for (int index = 0; index < tail.length(); index++) {
			if (Character.isWhitespace(tail.charAt(index))) tokenStart = index + 1;
		}
		String lower = tail.substring(tokenStart).toLowerCase(java.util.Locale.ROOT);
		if (lower.startsWith("from:")) return suggestCurrentSource(builder, tokenStart + "from:".length());
		if (lower.startsWith("to:")) {
			return suggestCurrentBracketBlock(builder, true, false, tokenStart + "to:".length());
		}
		if (lower.startsWith("stairs:")) {
			return suggestCurrentBracketBlock(builder, false, true, tokenStart + "stairs:".length());
		}
		return suggestPathSelectorNames(
			builder,
			tokenStart,
			NAMED_FROM.matcher(tail).find(),
			NAMED_TO.matcher(tail).find(),
			PATH_STAIRS.matcher(tail).find()
		);
	}

	private static CompletableFuture<Suggestions> suggestPathRoot(final SuggestionsBuilder builder) {
		List<String> names = new ArrayList<>();
		names.add("from:");
		names.add("to:");
		names.add("stairs:");
		names.sort(Comparator.naturalOrder());
		return SharedSuggestionProvider.suggest(names, builder);
	}

	private static CompletableFuture<Suggestions> suggestPathSelectorNames(
		final SuggestionsBuilder builder,
		final int tokenStart,
		final boolean hasFrom,
		final boolean hasTo,
		final boolean hasStairs
	) {
		List<String> names = new ArrayList<>();
		if (!hasFrom) names.add("from:");
		if (!hasTo) names.add("to:");
		if (!hasStairs) names.add("stairs:");
		return SharedSuggestionProvider.suggest(names, builder.createOffset(builder.getStart() + tokenStart));
	}

	private static boolean pathSelectorPrefix(final String tail) {
		String lower = tail.toLowerCase(java.util.Locale.ROOT);
		return "from:".startsWith(lower) || "to:".startsWith(lower) || "stairs:".startsWith(lower);
	}

	static CompletableFuture<Suggestions> calc(final SuggestionsBuilder builder) {
		String tail = builder.getRemaining();
		if (NAMED_SELECTOR.matcher(tail).find()) return suggestNamed(builder, true);
		if (tail.isEmpty()) return suggestRoot(builder);
		if (selectorPrefix(tail)) return suggestSelectorNames(builder, 0, false, false);
		return suggestSelectorNames(builder, 0, false, false);
	}

	private static CompletableFuture<Suggestions> suggestNamed(
		final SuggestionsBuilder builder,
		final boolean calculate
	) {
		String tail = builder.getRemaining();
		int tokenStart = 0;
		for (int index = 0; index < tail.length(); index++) {
			if (Character.isWhitespace(tail.charAt(index))) tokenStart = index + 1;
		}
		String token = tail.substring(tokenStart);
		String lower = token.toLowerCase(java.util.Locale.ROOT);
		if (lower.startsWith("type:")) return suggestCurrentType(builder, tokenStart + "type:".length());
		if (lower.startsWith("from:")) return suggestCurrentSource(builder, tokenStart + "from:".length());
		if (lower.startsWith("to:")) {
			return calculate
				? suggestCurrentMaterialList(builder, tokenStart + "to:".length())
				: suggestCurrentOverlayBlock(builder, tokenStart + "to:".length());
		}
		boolean hasFrom = NAMED_FROM.matcher(tail).find();
		boolean hasTo = NAMED_TO.matcher(tail).find();
		return suggestSelectorNames(builder, tokenStart, hasFrom, hasTo);
	}

	private static CompletableFuture<Suggestions> suggestRoot(final SuggestionsBuilder builder) {
		List<String> names = new ArrayList<>();
        names.add("type:");
		names.add("from:");
		names.add("to:");
		names.sort(Comparator.naturalOrder());
		return SharedSuggestionProvider.suggest(names, builder);
	}

	private static CompletableFuture<Suggestions> suggestSelectorNames(
		final SuggestionsBuilder builder,
		final int tokenStart,
		final boolean hasFrom,
		final boolean hasTo
	) {
		List<String> names = new ArrayList<>();
		if (!hasFrom) { names.add("from:"); names.add("type:"); }
		if (!hasTo) names.add("to:");
		SuggestionsBuilder offset = builder.createOffset(builder.getStart() + tokenStart);
		return SharedSuggestionProvider.suggest(names, offset);
	}

	private static boolean selectorPrefix(final String tail) {
		String lower = tail.toLowerCase(java.util.Locale.ROOT);
		return "from:".startsWith(lower) || "type:".startsWith(lower) || "to:".startsWith(lower);
	}

	private static CompletableFuture<Suggestions> suggestCurrentBracketBlock(
		final SuggestionsBuilder builder,
		final boolean allowDirtPath,
		final boolean stairsOnly,
		final int sectionStart
	) {
		String tail = builder.getRemaining();
		int comma = tail.lastIndexOf(',');
		int entryStart = Math.max(sectionStart, comma < sectionStart ? sectionStart : comma + 1);
		while (entryStart < tail.length() && Character.isWhitespace(tail.charAt(entryStart))) entryStart++;
		String entry = tail.substring(entryStart);
		if (entry.contains("[")) return builder.buildFuture();

		boolean fullIds = entry.contains(":");
		List<String> names = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			if (block == Blocks.AIR) continue;
			if (stairsOnly && !(block instanceof StairBlock)) continue;
			if (!stairsOnly && block == Blocks.DIRT_PATH && !allowDirtPath) continue;
			if (block != Blocks.DIRT_PATH && block.asItem() == Items.AIR) continue;
			names.add(fullIds || !"minecraft".equals(id.getNamespace()) ? id.toString() : id.getPath());
		}
		names.sort(Comparator.naturalOrder());
		SuggestionsBuilder offset = builder.createOffset(builder.getStart() + entryStart);
		return SharedSuggestionProvider.suggest(names, offset);
	}

    private static CompletableFuture<Suggestions> suggestCurrentType(final SuggestionsBuilder builder, final int sectionStart) {
        return suggestCurrentType(builder, sectionStart, true);
    }

    private static CompletableFuture<Suggestions> suggestCurrentType(final SuggestionsBuilder builder, final int sectionStart, final boolean allowNegation) {
        String tail = builder.getRemaining();
        String section = tail.substring(sectionStart).toLowerCase(java.util.Locale.ROOT);
        if (section.startsWith("not(")) {
            if (!allowNegation || section.contains(")")) return builder.buildFuture();
            return suggestCurrentType(builder, sectionStart + 4, false);
        }
        int comma = tail.lastIndexOf(',');
        int entryStart = comma < sectionStart ? sectionStart : comma + 1;
        List<String> names = new ArrayList<>(com.thenathe.blocktools.material.BlockType.keys());
        if (allowNegation && entryStart == sectionStart) names.add("not(");
        return SharedSuggestionProvider.suggest(names, builder.createOffset(builder.getStart() + entryStart));
    }

	private static CompletableFuture<Suggestions> suggestCurrentSource(
		final SuggestionsBuilder builder,
		final int sectionStart
	) {
        return suggestCurrentSource(builder, sectionStart, true);
    }

    private static CompletableFuture<Suggestions> suggestCurrentSource(final SuggestionsBuilder builder, final int sectionStart, final boolean allowNegation) {
		String tail = builder.getRemaining();
        String section = tail.substring(sectionStart).toLowerCase(java.util.Locale.ROOT);
        if (section.startsWith("not(")) {
            if (!allowNegation || section.contains(")")) return builder.buildFuture();
            return suggestCurrentSource(builder, sectionStart + 4, false);
        }
        if (section.startsWith("type:")) return suggestCurrentType(builder, sectionStart + 5, allowNegation);
		int comma = tail.lastIndexOf(',');
		int entryStart = Math.max(sectionStart, comma < sectionStart ? sectionStart : comma + 1);
		while (entryStart < tail.length() && Character.isWhitespace(tail.charAt(entryStart))) entryStart++;
		String entry = tail.substring(entryStart);
		boolean fullIds = entry.contains(":");
		List<String> names = new ArrayList<>();
		names.add("type:");
        if (allowNegation && entryStart == sectionStart) names.add("not(");
		names.add("any");
		names.add("any+air");
		names.add("all");
		names.add("air");
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (BuiltInRegistries.BLOCK.getValue(id) == Blocks.AIR) continue;
			names.add(fullIds || !"minecraft".equals(id.getNamespace()) ? id.toString() : id.getPath());
		}
		names.sort(Comparator.naturalOrder());
		SuggestionsBuilder offset = builder.createOffset(builder.getStart() + entryStart);
		return SharedSuggestionProvider.suggest(names, offset);
	}

	private static CompletableFuture<Suggestions> suggestCurrentMaterialList(
		final SuggestionsBuilder builder,
		final int sectionStart
	) {
		String tail = builder.getRemaining();
		int comma = tail.lastIndexOf(',');
		int entryStart = Math.max(sectionStart, comma < sectionStart ? sectionStart : comma + 1);
		while (entryStart < tail.length() && Character.isWhitespace(tail.charAt(entryStart))) entryStart++;
		String entry = tail.substring(entryStart);
		boolean fullIds = entry.contains(":");
		List<String> names = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			if (block != Blocks.AIR && block.asItem() == Items.AIR) continue;
			names.add(fullIds || !"minecraft".equals(id.getNamespace()) ? id.toString() : id.getPath());
		}
		names.sort(Comparator.naturalOrder());
		SuggestionsBuilder offset = builder.createOffset(builder.getStart() + entryStart);
		return SharedSuggestionProvider.suggest(names, offset);
	}

	private static CompletableFuture<Suggestions> suggestCurrentOverlayBlock(
		final SuggestionsBuilder builder,
		final int sectionStart
	) {
		String tail = builder.getRemaining();
		int comma = tail.lastIndexOf(',');
		int entryStart = Math.max(sectionStart, comma < sectionStart ? sectionStart : comma + 1);
		while (entryStart < tail.length() && Character.isWhitespace(tail.charAt(entryStart))) entryStart++;
		String entry = tail.substring(entryStart);
		if (entry.contains("[")) return builder.buildFuture();
		boolean fullIds = entry.contains(":");
		List<String> names = inventoryBlockNames(fullIds);
		SuggestionsBuilder offset = builder.createOffset(builder.getStart() + entryStart);
		return SharedSuggestionProvider.suggest(names, offset);
	}

	private static List<String> inventoryBlockNames(final boolean fullIds) {
		List<String> names = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			if (block == Blocks.DIRT_PATH || (block != Blocks.AIR && block.asItem() == Items.AIR)) continue;
			names.add(fullIds || !"minecraft".equals(id.getNamespace()) ? id.toString() : id.getPath());
		}
		return names;
	}

}
