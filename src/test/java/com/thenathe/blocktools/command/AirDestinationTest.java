package com.thenathe.blocktools.command;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.thenathe.blocktools.material.*;
import com.thenathe.blocktools.tone.ToneService;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;

/** Air is a free Blocktone destination, never a placeable Path Tool material. */
public final class AirDestinationTest {
    private AirDestinationTest() { }

    public static void run() {
        for (String spelling : List.of("air", "minecraft:air", "air[]", "air[100]", "minecraft:air[100]")) {
            OverlayPalette palette = OverlayPalette.parse(spelling);
            require(palette.exactDistribution(13).materialCounts().equals(Map.of(Blocks.AIR, 13)), "air spelling lost quota: " + spelling);
        }
        require(Palette.parseTone("minecraft:air:40,stone:60").canonical().equals("air:40,stone:60"), "legacy tone air palette changed");
        require(Palette.parseTone("air:100").exactAssignments(7, 1).stream().allMatch(entry -> entry.block() == Blocks.AIR), "legacy air assignments changed");
        for (String invalid : List.of("cave_air", "void_air", "minecraft:cave_air", "minecraft:void_air", "missing_air")) {
            fails(() -> OverlayPalette.parse(invalid));
            fails(() -> Palette.parseTone(invalid + ":100"));
        }
        fails(() -> OverlayPalette.parse("air,minecraft:air"));
        fails(() -> Palette.parseTone("air:50,minecraft:air:50"));
        fails(() -> Palette.parse("air:100", true, false));
        fails(() -> Palette.parse("air:100", false, true));
        fails(() -> Palette.parseBracketed("air[100]", true, false));
        fails(() -> Palette.parseBracketed(PathSelectors.parse("to:air[100]").to(), true, false));
        fails(() -> Palette.parseBracketed(PathSelectors.parse("to:stone[100] stairs:air[100]").stairs(), false, true));

        OverlayPalette.Distribution partial = OverlayPalette.parse("air[25]").exactDistribution(20);
        require(partial.materialCounts().equals(Map.of(Blocks.AIR, 5)) && partial.unchangedCount() == 15, "partial removal lost retained remainder");
        OverlayPalette.Distribution mixed = OverlayPalette.parse("air[20],bricks[30]").exactDistribution(20);
        require(mixed.materialCounts().equals(Map.of(Blocks.AIR, 4, Blocks.BRICKS, 6)) && mixed.unchangedCount() == 10, "mixed explicit quotas changed");
        require(OverlayPalette.parse("air,bricks").exactDistribution(10).materialCounts().equals(Map.of(Blocks.AIR, 5, Blocks.BRICKS, 5)), "regular automatic mode stopped sharing evenly");
        require(ReplacementStates.preserve(Blocks.OAK_SLAB.defaultBlockState(), Blocks.AIR).isAir(), "slab removal retained slab state");

        calculated("air", 10, Map.of(), Map.of(Blocks.STONE, 10), Map.of(Blocks.AIR, 10));
        calculated("air,bricks", 10, Map.of(Items.BRICKS, 3), Map.of(Blocks.STONE, 10), Map.of(Blocks.AIR, 7, Blocks.BRICKS, 3));
        calculated("bricks,air", 10, Map.of(Items.BRICKS, 3), Map.of(Blocks.BRICKS, 5, Blocks.STONE, 5), Map.of(Blocks.AIR, 2, Blocks.BRICKS, 8));
        calculated("air,bricks", 10, Map.of(Items.BRICKS, 20), Map.of(Blocks.STONE, 10), Map.of(Blocks.AIR, 0, Blocks.BRICKS, 10));
        calculated("air,bricks", 10, Map.of(Items.AIR, 99), Map.of(Blocks.AIR, 10), Map.of(Blocks.AIR, 10, Blocks.BRICKS, 0));
        calculated("air[20],bricks", 10, Map.of(Items.BRICKS, 3), Map.of(Blocks.STONE, 10), Map.of(Blocks.AIR, 2, Blocks.BRICKS, 3));
        calculated("air[20],bricks[30]", 10, Map.of(), Map.of(Blocks.STONE, 10), Map.of(Blocks.AIR, 2, Blocks.BRICKS, 3));
        calculated("air,bricks[20]", 10, Map.of(), Map.of(Blocks.STONE, 10), Map.of(Blocks.AIR, 8, Blocks.BRICKS, 2));
        suggestions();
        negation();
        System.out.println("Air destination quotas, free fallback, source negation, and completion checks passed.");
    }

    private static void calculated(String palette, int size, Map<net.minecraft.world.item.Item, Integer> inventory,
            Map<Block, Integer> existing, Map<Block, Integer> expected) {
        Map<Block, Integer> actual = ToneService.calculatedCounts(size, OverlayPalette.parse(palette), inventory, existing);
        require(actual.equals(expected), "calculated free-air allocation changed for " + palette + ": " + actual + " expected " + expected);
    }

    private static void suggestions() {
        for (String prefix : List.of("to:a", "to:stone[20],a")) {
            require(BlockSuggestions.tone(new SuggestionsBuilder(prefix, 0)).join().getList().stream().anyMatch(s -> s.getText().equals("air")), "tone omitted air completion: " + prefix);
            require(BlockSuggestions.calc(new SuggestionsBuilder(prefix, 0)).join().getList().stream().anyMatch(s -> s.getText().equals("air")), "calc omitted air completion: " + prefix);
        }
        String input = "/blocktone to:stone[20],minecraft:a";
        for (var completion : List.of(BlockSuggestions.tone(new SuggestionsBuilder(input, 11)).join(), BlockSuggestions.calc(new SuggestionsBuilder(input, 11)).join())) {
            require(completion.getList().stream().anyMatch(s -> s.apply(input).equals("/blocktone to:stone[20],minecraft:air")), "namespaced air completion overwrote earlier selector");
        }
        for (String prefix : List.of("to:a", "to:stone[20],a", "stairs:a", "to:minecraft:a")) {
            require(BlockSuggestions.path(new SuggestionsBuilder(prefix, 0)).join().getList().stream().noneMatch(s -> s.getText().equals("air") || s.getText().equals("minecraft:air")), "air leaked into Path Tool destination: " + prefix);
        }
    }

    private static void negation() {
        BlockFilter protectedGround = BlockFilter.parse("not(grass_block,dirt)");
        require(!protectedGround.matches(Blocks.GRASS_BLOCK) && !protectedGround.matches(Blocks.DIRT) && protectedGround.matches(Blocks.STONE), "negated block list changed inclusion");
        require(!protectedGround.matches(Blocks.AIR) && !protectedGround.includesAir() && !protectedGround.includesLiquids(), "negation enabled implicit air/fluid replacement");
        require(BlockFilter.parse("not(minecraft:dirt,minecraft:grass_block)").canonical().equals(protectedGround.canonical()), "negated namespaced source did not normalize");
        require(BlockFilter.parse("type:not(stairs,slabs)").canonical().equals("not(type:slabs,stairs)"), "negated type shorthand failed canonical normalization");
        BlockFilter excludedShapes = BlockFilter.parse("not(type:stairs,slabs)");
        require(!excludedShapes.matches(Blocks.OAK_STAIRS) && !excludedShapes.matches(Blocks.STONE_SLAB) && excludedShapes.matches(Blocks.STONE), "negated type list changed inclusion");
        for (String invalid : List.of("not()", "not(stone", "not(stone))", "not(stone)junk", "not(not(stone))", "not(missing_block)", "type:not()", "type:not(stairs")) fails(() -> BlockFilter.parse(invalid));
        require(BlockSuggestions.tone(new SuggestionsBuilder("from:not(gr", 0)).join().getList().stream().anyMatch(s -> s.getText().equals("grass_block")), "negated block list did not complete");
        String source = "/blocktone from:not(grass_block,di";
        require(BlockSuggestions.tone(new SuggestionsBuilder(source, 11)).join().getList().stream().anyMatch(s -> s.apply(source).equals("/blocktone from:not(grass_block,dirt")), "negated source comma completion overwrote earlier source");
        require(BlockSuggestions.calc(new SuggestionsBuilder("type:not(st", 0)).join().getList().stream().anyMatch(s -> s.getText().equals("stairs")), "negated category did not complete");
        require(BlockSuggestions.tone(new SuggestionsBuilder("from:not(grass_block,dirt) ", 0)).join().getList().stream().anyMatch(s -> s.getText().equals("to:")), "closed negation did not offer destination selector");
        require(BlockSuggestions.calc(new SuggestionsBuilder("type:not(stairs,slabs) ", 0)).join().getList().stream().anyMatch(s -> s.getText().equals("to:")), "closed type negation did not offer destination selector");
    }

    private static void fails(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid palette/filter to fail"); }
        catch (IllegalArgumentException expected) { require(expected.getMessage() != null && !expected.getMessage().isBlank(), "rejection omitted reason"); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
