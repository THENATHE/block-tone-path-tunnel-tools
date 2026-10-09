package com.thenathe.blocktools.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class BlockFilter {
	private static final BlockFilter ANY = new BlockFilter(true, false, Set.of());
	private static final BlockFilter ANY_WITH_AIR = new BlockFilter(true, true, Set.of());
	private final boolean any;
	private final boolean includeAir;
	private final Set<Block> blocks;
	private final Set<BlockType> types;
    private final BlockFilter excluded;

	private BlockFilter(final boolean any, final boolean includeAir, final Set<Block> blocks) {
		this(any, includeAir, blocks, Set.of());
	}

	private BlockFilter(final boolean any, final boolean includeAir, final Set<Block> blocks, final Set<BlockType> types) {
        this(any, includeAir, blocks, types, null);
    }

    private BlockFilter(final boolean any, final boolean includeAir, final Set<Block> blocks, final Set<BlockType> types, final BlockFilter excluded) {
        this.excluded = excluded;
		this.types = Set.copyOf(types);
		this.any = any;
		this.includeAir = includeAir;
		this.blocks = Set.copyOf(blocks);
	}

	public static BlockFilter all() {
		return ANY;
	}

	public static BlockFilter parse(final String input) {
		if (input == null || input.isBlank()) return ANY;
		String normalized = input.trim().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("not(")) return negate(normalized, false);
        if (normalized.startsWith("type:")) {
            String typed = normalized.substring(5);
            return typed.startsWith("not(") ? negate(typed, true) : parseTypes(typed);
        }
		if ("any".equals(normalized) || "all".equals(normalized)) return ANY;
		if ("any+air".equals(normalized) || "all+air".equals(normalized)) return ANY_WITH_AIR;
		Set<Block> blocks = new LinkedHashSet<>();
		boolean includesAir = false;
		for (String raw : input.split(",", -1)) {
			String name = raw.trim().toLowerCase(Locale.ROOT);
			if (name.isEmpty()) throw new IllegalArgumentException("The source block list contains an empty entry.");
			if ("any".equals(name) || "all".equals(name) || "any+air".equals(name) || "all+air".equals(name)) {
				throw new IllegalArgumentException("Use any/any+air by itself, or use an explicit block list.");
			}
			Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
			Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
			if (block == null) throw new IllegalArgumentException("Unknown source block '" + name + "'.");
			if (!blocks.add(block)) throw new IllegalArgumentException("Source block '" + name + "' appears more than once.");
			if (block.defaultBlockState().isAir()) includesAir = true;
		}
		return new BlockFilter(false, includesAir, blocks);
	}

    private static BlockFilter negate(final String expression, final boolean typed) {
        if (!expression.endsWith(")")) throw new IllegalArgumentException("The not(...) source filter needs a closing parenthesis.");
        String value = expression.substring(4, expression.length() - 1).trim();
        if (value.isEmpty()) throw new IllegalArgumentException("The not(...) source filter needs a block or type list.");
        if (value.contains("(") || value.contains(")")) throw new IllegalArgumentException("Nested or extra parentheses are not supported in not(...).");
        BlockFilter inner = typed ? parseTypes(value) : parse(value);
        // Exclusion never opts into empty cells or pure fluids implicitly.
        return new BlockFilter(false, false, Set.of(), Set.of(), inner);
    }

	private static BlockFilter parseTypes(final String input) {
        Set<BlockType> types = new LinkedHashSet<>();
        for (String raw : input.split(",", -1)) {
            if (raw.isBlank()) throw new IllegalArgumentException("The source type list contains an empty entry.");
            BlockType type = BlockType.parse(raw);
            if (!types.add(type)) throw new IllegalArgumentException("Source type '" + type.key() + "' appears more than once.");
        }
        return new BlockFilter(false, types.contains(BlockType.AIR), Set.of(), types);
    }

    /** Pure fluids require an explicit liquids category or an explicit water/lava block ID. */
    public boolean includesLiquids() {
        return types.contains(BlockType.LIQUIDS)
            || blocks.stream().anyMatch(block -> block instanceof net.minecraft.world.level.block.LiquidBlock);
    }

	public boolean matches(final Block block) {
        if (excluded != null) return !block.defaultBlockState().isAir() && !excluded.matches(block);
		if (!types.isEmpty()) return types.stream().anyMatch(type -> type.matches(block));
		if (block.defaultBlockState().isAir()) return includeAir && (any || blocks.contains(block));
		return any || blocks.contains(block);
	}

	public boolean isAll() {
		return any;
	}

	public boolean isDefault() {
		return any && !includeAir;
	}

	public boolean includesAir() {
		return includeAir;
	}

	public String canonical() {
        if (excluded != null) return "not(" + excluded.canonical() + ")";
		if (!types.isEmpty()) return "type:" + types.stream().map(BlockType::key).sorted().collect(java.util.stream.Collectors.joining(","));
		if (any) return includeAir ? "any+air" : "any";
		return blocks.stream()
			.map(BuiltInRegistries.BLOCK::getKey)
			.map(Identifier::toShortString)
			.sorted()
			.reduce((left, right) -> left + "," + right)
			.orElse("any");
	}
}
