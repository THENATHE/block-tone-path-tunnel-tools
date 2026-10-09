package com.thenathe.blocktools.command;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.List;

public final class BlockSuggestionsTest {
	private BlockSuggestionsTest() {
	}

	public static void run() {
		testTypeSelectors();
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("", 0)).join()).containsAll(List.of("from:", "to:")),
			"named selector roots did not autocomplete");
		require(!text(BlockSuggestions.tone(new SuggestionsBuilder("sto", 0)).join()).contains("stone"),
			"an unlabeled regular destination was suggested despite mandatory to:");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("from:br", 0)).join()).contains("bricks"),
			"named from: material did not autocomplete");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("from:bricks ", 0)).join()).contains("to:"),
			"mandatory to: selector did not autocomplete after from:");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("to:sto", 0)).join()).contains("stone"),
			"regular to: block did not autocomplete");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("to:minecraft:sto", 0)).join()).contains("minecraft:stone"),
			"namespaced regular to: block did not autocomplete");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("to:iron_", 0)).join()).contains("iron_block"),
			"named to: material did not autocomplete");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("to:iron_block[10],qu", 0)).join()).contains("quartz_block"),
			"named to: material after a comma did not autocomplete");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("to:iron_block[10] ", 0)).join()).contains("from:"),
			"position-independent from: selector did not autocomplete after to:");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("from:any+", 0)).join()).contains("any+air"),
			"from:any+air did not autocomplete");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("from:a", 0)).join()).containsAll(List.of("air", "any", "any+air")),
			"air-aware source options did not autocomplete");
		require(text(BlockSuggestions.shuffle(new SuggestionsBuilder("", 0)).join()).contains("from:"),
			"shuffle did not suggest its required from: selector");
		require(text(BlockSuggestions.shuffle(new SuggestionsBuilder("from:polished_", 0)).join()).contains("polished_granite"),
			"shuffle source block did not autocomplete");
		require(text(BlockSuggestions.shuffle(new SuggestionsBuilder("from:stone,gr", 0)).join()).contains("granite"),
			"shuffle source block after a comma did not autocomplete");

		require(text(BlockSuggestions.calc(new SuggestionsBuilder("", 0)).join()).containsAll(List.of("from:", "to:")),
			"calculate selector roots did not autocomplete");
		require(!text(BlockSuggestions.calc(new SuggestionsBuilder("br", 0)).join()).contains("bricks"),
			"an unlabeled calculate destination was suggested despite mandatory to:");
		require(text(BlockSuggestions.calc(new SuggestionsBuilder("to:br", 0)).join()).contains("bricks"),
			"calculate to: material did not autocomplete");
		require(text(BlockSuggestions.calc(new SuggestionsBuilder("to:bricks,iron_", 0)).join()).contains("iron_block"),
			"calculate accent after a comma did not autocomplete");
		require(text(BlockSuggestions.calc(new SuggestionsBuilder("to:minecraft:br", 0)).join()).contains("minecraft:bricks"),
			"namespaced calculate material did not autocomplete");
		require(text(BlockSuggestions.calc(new SuggestionsBuilder("to:bricks,iron_block from:gr", 0)).join()).contains("granite"),
			"calculate source block did not autocomplete");
		require(text(BlockSuggestions.calc(new SuggestionsBuilder("from:bricks to:iron_", 0)).join()).contains("iron_block"),
			"named calculate to: material did not autocomplete");

		require(text(BlockSuggestions.path(new SuggestionsBuilder("", 0)).join()).containsAll(List.of("from:", "to:", "stairs:")),
			"Path Tool selector roots did not autocomplete");
		require(!text(BlockSuggestions.path(new SuggestionsBuilder("dirt", 0)).join()).contains("dirt_path"),
			"an unlabeled Path Tool destination was suggested despite mandatory to:");
		require(text(BlockSuggestions.path(new SuggestionsBuilder("to:dirt", 0)).join()).contains("dirt_path"),
			"dirt_path was missing from Path Tool to: suggestions");
		require(text(BlockSuggestions.path(new SuggestionsBuilder("to:stone[100] stairs:cob", 0)).join()).contains("cobblestone_stairs"),
			"stair palette did not autocomplete stair blocks");
		require(!text(BlockSuggestions.path(new SuggestionsBuilder("to:stone[100] stairs:cob", 0)).join()).contains("cobblestone"),
			"non-stair block leaked into stair suggestions");
		require(text(BlockSuggestions.path(new SuggestionsBuilder("from:gr", 0)).join()).contains("grass_block"),
			"Path Tool from: source did not autocomplete");
		require(text(BlockSuggestions.path(new SuggestionsBuilder("from:dirt to:cob", 0)).join()).contains("cobblestone"),
			"Path Tool to: material did not autocomplete");
		require(text(BlockSuggestions.path(new SuggestionsBuilder("to:stone[100] stairs:cob", 0)).join()).contains("cobblestone_stairs"),
			"Path Tool stairs: material did not autocomplete");
		require(text(BlockSuggestions.path(new SuggestionsBuilder("stairs:cobblestone_stairs[100] ", 0)).join()).contains("to:"),
			"position-independent Path Tool to: selector did not autocomplete");
	}

	private static void testTypeSelectors() {
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("", 0)).join()).contains("type:"),
			"tone did not suggest the type selector root");
		require(text(BlockSuggestions.calc(new SuggestionsBuilder("", 0)).join()).contains("type:"),
			"calculate did not suggest the type selector root");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("ty", 0)).join()).equals(List.of("type:")),
			"partial type selector did not autocomplete");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("type:", 0)).join()).containsAll(
			List.of("stairs", "slabs", "trapdoors", "fences", "walls", "bars", "railings", "panes", "fence_gates", "logs", "blocks", "liquids", "air",
				"light_sources", "torches", "lanterns", "buttons", "pressure_plates", "rails", "chains", "ladders", "leaves", "carpets", "pillars")),
			"type selector omitted supported categories");
		require(text(BlockSuggestions.calc(new SuggestionsBuilder("type:railings,li", 0)).join())
			.containsAll(List.of("light_sources", "liquids")), "combined railing/light selector did not autocomplete");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("type:rail", 0)).join())
			.equals(List.of("railings", "rails")), "railings and rails prefixes were conflated");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("from:type:light_sources,lan", 0)).join())
			.equals(List.of("lanterns")), "nested expanded category did not autocomplete after comma");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("type:sta", 0)).join()).equals(List.of("stairs")),
			"type category suggestions included block IDs or ignored the prefix");
		require(text(BlockSuggestions.calc(new SuggestionsBuilder("type:stairs,sl", 0)).join()).equals(List.of("slabs")),
			"calculate did not suggest a type after a comma");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("type:stairs ", 0)).join()).equals(List.of("to:")),
			"completed type source offered a duplicate/conflicting source selector");
		require(text(BlockSuggestions.calc(new SuggestionsBuilder("from:stone ", 0)).join()).equals(List.of("to:")),
			"completed block source offered a conflicting type selector");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("to:brick_stairs ", 0)).join())
			.containsAll(List.of("from:", "type:")), "type source was not offered after destination");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("to:brick_stairs type:st", 0)).join())
			.equals(List.of("stairs")), "type autocomplete changed with selector order");
		require(text(BlockSuggestions.tone(new SuggestionsBuilder("from:type:sl", 0)).join()).equals(List.of("slabs")),
			"nested from:type: source did not autocomplete");
		require(text(BlockSuggestions.calc(new SuggestionsBuilder("to:brick_stairs,stone_stairs type:tr", 0)).join())
			.equals(List.of("trapdoors")), "destination commas broke later type suggestions");
		String command = "/blocktone type:stairs,sl";
		var suggestions = BlockSuggestions.tone(new SuggestionsBuilder(command, "/blocktone ".length())).join();
		Suggestion slab = suggestions.getList().stream().filter(s -> s.getText().equals("slabs")).findFirst().orElseThrow();
		require(slab.getRange().getStart() == command.lastIndexOf(',') + 1,
			"type completion used the wrong absolute offset");
		require(slab.apply(command).equals("/blocktone type:stairs,slabs"),
			"type completion overwrote earlier categories or command text");
		String nestedCommand = "/blocktone to:brick_stairs from:type:sl";
		var nested = BlockSuggestions.tone(new SuggestionsBuilder(nestedCommand, "/blocktone ".length())).join();
		require(nested.getList().stream().anyMatch(s -> s.apply(nestedCommand)
			.equals("/blocktone to:brick_stairs from:type:slabs")), "nested type completion used the wrong offset");
	}

	private static List<String> text(final com.mojang.brigadier.suggestion.Suggestions suggestions) {
		return suggestions.getList().stream().map(Suggestion::getText).toList();
	}

	private static void require(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}
}
