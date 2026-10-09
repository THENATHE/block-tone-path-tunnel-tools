package com.thenathe.blocktools.tunnel;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TunnelPalette {
	private static final Pattern ENTRY = Pattern.compile("^(.+)\\[(\\d+)]$");
	private final List<WeightedBlock> entries;
	private final String canonical;

	private TunnelPalette(final List<WeightedBlock> entries) {
		this.entries = List.copyOf(entries);
		this.canonical = entries.stream()
			.map(entry -> BuiltInRegistries.BLOCK.getKey(entry.block()).toString() + "[" + entry.percent() + "]")
			.reduce((left, right) -> left + "," + right)
			.orElse("");
	}

	public static TunnelPalette parse(final String input) {
		return parse(input, false);
	}

	/** Air is a deliberate wall/ceiling choice; floors must remain solid. */
	public static TunnelPalette parse(final String input, final boolean allowAir) {
		if (input == null || input.isBlank()) throw new IllegalArgumentException("A tunnel material mix is empty.");
		Map<Block, Integer> weights = new LinkedHashMap<>();
		for (String raw : input.split(",", -1)) {
			String token = raw.trim();
			Matcher matcher = ENTRY.matcher(token);
			if (!matcher.matches()) {
				throw new IllegalArgumentException("Use block[percent] entries, for example stone_bricks[70],andesite[30].");
			}
			String name = matcher.group(1).trim().toLowerCase(Locale.ROOT);
			int percent;
			try {
				percent = Integer.parseInt(matcher.group(2));
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException("'" + token + "' does not contain a whole-number percentage.");
			}
			if (percent <= 0 || percent > 100) {
				throw new IllegalArgumentException("Every tunnel percentage must be between 1 and 100.");
			}
			Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
			Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
			if (block == Blocks.AIR && !allowAir) {
				throw new IllegalArgumentException("Air is allowed only in walls: and ceiling: palettes; floor: and to: must use solid blocks.");
			}
			if (!(allowAir && block == Blocks.AIR) && !isSafeLiningBlock(block)) {
				throw new IllegalArgumentException("'" + name + "' is not a safe full-cube, item-backed tunnel lining block.");
			}
			if (weights.putIfAbsent(block, percent) != null) {
				throw new IllegalArgumentException("Block '" + name + "' appears more than once in one mix.");
			}
		}
		int total = weights.values().stream().mapToInt(Integer::intValue).sum();
		if (total != 100) throw new IllegalArgumentException("Tunnel mix percentages must total 100; this mix totals " + total + ".");
		return new TunnelPalette(weights.entrySet().stream()
			.map(entry -> new WeightedBlock(entry.getKey(), entry.getValue())).toList());
	}

	public Block pick(final long hash) {
		int target = Math.floorMod(hash, 100);
		for (WeightedBlock entry : entries) {
			target -= entry.percent();
			if (target < 0) return entry.block();
		}
		return entries.getLast().block();
	}

	public String canonical() {
		return canonical;
	}

	public List<Block> blocks() {
		List<Block> result = new ArrayList<>();
		for (WeightedBlock entry : entries) result.add(entry.block());
		return List.copyOf(result);
	}

	static boolean isSafeLiningBlock(final Block block) {
		return block != null && block != Blocks.AIR && block.asItem() != Items.AIR
			&& !(block instanceof BaseRailBlock) && !block.defaultBlockState().hasBlockEntity()
			&& !(block instanceof FallingBlock) && !(block instanceof PistonBaseBlock)
			&& !(block instanceof TntBlock) && (block.defaultBlockState().isSolid() && block != Blocks.COBWEB && block != Blocks.BAMBOO_SAPLING)
			&& block.defaultBlockState().getFluidState().isEmpty()
			&& Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
	}

	private record WeightedBlock(Block block, int percent) {
	}
}
