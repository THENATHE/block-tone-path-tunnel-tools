package com.thenathe.blocktools.tunnel;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.thenathe.blocktools.command.BlockToolsCommands;
import com.thenathe.blocktools.config.ModConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Blocks;
import java.util.List;

public final class IntegrationLogicTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        airPalettes();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        for (Class<?> type : List.of(BlockToolsCommands.class, TunnelCommands.class)) {
            var registration = type.getDeclaredMethod("register", CommandDispatcher.class);
            registration.setAccessible(true);
            registration.invoke(null, dispatcher);
        }
        for (String root : List.of("blocktone", "pathtool", "blocktools", "tunneltool", "tunnel")) {
            var node = dispatcher.getRoot().getChild(root);
            require(node != null && node.getCommand() != null, "Missing bare command: " + root);
        }
        require(dispatcher.getRoot().getChild("tunnel").getRedirect() == dispatcher.getRoot().getChild("tunneltool"), "Tunnel alias target");
        for (String command : List.of("tunneltool help verbose", "tunnel help verbose", "tunneltool width 5", "tunnel height 2",
                "tunnel rail:only width:2", "blocktone calc apply to:stone", "pathtool off")) {
            var parsed = dispatcher.parse(command, null);
            require(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(), "Command parse: " + command);
        }
        var blockSuggestion = TunnelSuggestions.suggest(new SuggestionsBuilder("tunneltool to:stone[50],gran", 11)).join();
        require(blockSuggestion.getList().stream().anyMatch(s -> s.apply("tunneltool to:stone[50],gran").equals("tunneltool to:stone[50],granite[100]")), "Comma completion offset");
        require(TunnelSuggestions.suggest(new SuggestionsBuilder("rail:o", 0)).join().getList().size() == 3, "Rail completion");
        require(TunnelSuggestions.suggest(new SuggestionsBuilder("to:minecraft:sto", 0)).join().getList().stream().anyMatch(s -> s.getText().equals("minecraft:stone[100]")), "Namespaced completion");
        require(TunnelSuggestions.suggest(new SuggestionsBuilder("to:stone[", 0)).join().isEmpty(), "Do not replace partial percentage");
        require(TunnelSuggestions.suggest(new SuggestionsBuilder("to:tnt", 0)).join().isEmpty(), "Unsafe autocomplete");
        for (int weight = 1; weight < 100; weight++) {
            var palette = TunnelPalette.parse("stone[" + weight + "],granite[" + (100-weight) + "]");
            int stone = 0;
            for (int hash = -100; hash < 100; hash++) if (palette.pick(hash) == Blocks.STONE) stone++;
            require(stone == 2 * weight, "Weighted picker, including negative hashes: " + weight);
        }
        for (int size : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2, 3, 5, 32, 128, Integer.MAX_VALUE}) {
            TunnelConfig tunnel = new TunnelConfig();
            tunnel.defaultWidth = tunnel.defaultHeight = tunnel.maxCatchUp = tunnel.poweredRailInterval = size;
            tunnel.validate();
            require(tunnel.defaultWidth >= 1 && tunnel.defaultWidth <= 5, "Width normalization");
            require(tunnel.defaultHeight >= 2 && tunnel.defaultHeight <= 5, "Height normalization");
            require(tunnel.maxCatchUp >= 1 && tunnel.maxCatchUp <= 32, "Catch-up normalization");
            require(tunnel.poweredRailInterval >= 8 && tunnel.poweredRailInterval <= 128, "Booster normalization");
            ModConfig config = new ModConfig();
            config.selectionItem = null;
            config.maxToneBlocks = config.maxPathCatchUp = config.pathVerticalSearch = size;
            config.normalize();
            require("minecraft:breeze_rod".equals(config.selectionItem), "Null tool item normalization");
            require(config.maxToneBlocks > 0 && config.maxToneBlocks <= 1_000_000, "Tone normalization");
        }
        System.out.println("Integration logic checks passed: " + checks);
    }
    private static void airPalettes() {
        var spec = TunnelSpec.parse("walls:air[100] floor:stone[100] ceiling:minecraft:air[100]", 3, 3);
        require(spec.walls().pick(0) == Blocks.AIR && spec.ceiling().pick(0) == Blocks.AIR,
            "separate wall and ceiling palettes support normal and namespaced air");
        require(spec.floor().pick(0) == Blocks.STONE, "open tunnel retains solid floor");
        var mix = TunnelPalette.parse("air[30],stone[70]", true);
        int air = 0;
        for (long hash = -100; hash < 100; hash++) if (mix.pick(hash) == Blocks.AIR) air++;
        require(air == 60, "air obeys weighted palette distribution");
        for (String invalid : List.of("to:air[100]", "all:stone[50],air[50]",
                "walls:air[100] floor:air[100] ceiling:air[100]",
                "walls:water[100] floor:stone[100] ceiling:air[100]",
                "walls:air[50],minecraft:air[50] floor:stone[100] ceiling:air[100]")) {
            boolean refused = false;
            try { TunnelSpec.parse(invalid, 3, 3); }
            catch (IllegalArgumentException expected) { refused = true; }
            require(refused, "unsafe/malformed air palette refused: " + invalid);
        }
        for (String prefix : List.of("walls:", "wall:", "ceiling:", "ceilings:")) {
            var suggestions = TunnelSuggestions.suggest(new SuggestionsBuilder(prefix + "ai", 0)).join();
            require(suggestions.getList().stream().anyMatch(value -> value.getText().equals("air[100]")),
                "air suggested for " + prefix);
        }
        for (String prefix : List.of("to:", "all:", "floor:", "floors:")) {
            var suggestions = TunnelSuggestions.suggest(new SuggestionsBuilder(prefix + "ai", 0)).join();
            require(suggestions.getList().stream().noneMatch(value -> value.getText().equals("air[100]")),
                "air excluded from solid floor palette " + prefix);
        }
        String command = "tunneltool walls:stone[50],minecraft:ai";
        var completion = TunnelSuggestions.suggest(new SuggestionsBuilder(command, 11)).join();
        require(completion.getList().stream().anyMatch(value -> value.apply(command)
            .equals("tunneltool walls:stone[50],minecraft:air[100]")), "namespaced air comma completion offset");
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
