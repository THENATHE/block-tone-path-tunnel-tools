package com.thenathe.blocktools.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Palette {
	private static final Pattern BRACKET_ENTRY = Pattern.compile("^(.+)\\[(\\d+)]$");
	private final List<Entry> entries;
	private final String canonical;

	private Palette(final List<Entry> entries, final boolean bracketed) {
		this.entries = List.copyOf(entries);
		this.canonical = entries.stream()
			.map(entry -> BuiltInRegistries.BLOCK.getKey(entry.block()).toShortString()
				+ (bracketed ? "[" + entry.percent() + "]" : ":" + entry.percent()))
			.reduce((left, right) -> left + "," + right)
			.orElse("");
	}

    public static Palette parse(final String input, final boolean allowDirtPath, final boolean stairsOnly) {
        return parse(input, allowDirtPath, stairsOnly, false);
    }

    /** Blocktone's legacy percentage palette also allows inventory-free clearing. */
    public static Palette parseTone(final String input) {
        return parse(input, false, false, true);
    }

    private static Palette parse(final String input, final boolean allowDirtPath, final boolean stairsOnly, final boolean allowAir) {
		if (input == null || input.isBlank()) throw new IllegalArgumentException("The material palette is empty.");
		Map<Block, Integer> weights = new LinkedHashMap<>();
		for (String rawEntry : input.split(",", -1)) {
			String token = rawEntry.trim();
			int separator = token.lastIndexOf(':');
			if (separator <= 0 || separator == token.length() - 1) {
				throw new IllegalArgumentException("Use material:percent entries separated by commas (for example stone:100).");
			}
			String name = token.substring(0, separator).trim().toLowerCase(Locale.ROOT);
			int percent;
			try {
				percent = Integer.parseInt(token.substring(separator + 1).trim());
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException("'" + token + "' does not have a whole-number percentage.");
			}
			if (percent <= 0 || percent > 100) {
				throw new IllegalArgumentException("Every percentage must be between 1 and 100.");
			}

			Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
			Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
			if (block == null || (block == Blocks.AIR && !allowAir)) {
				throw new IllegalArgumentException("Unknown block '" + name + "'. Try a full id such as minecraft:stone.");
			}
			if (block == Blocks.DIRT_PATH && !allowDirtPath) {
				throw new IllegalArgumentException("dirt_path is only available in the normal path palette.");
			}
			BlockMaterials.requireSingleBlock(block);
			if (stairsOnly && !(block instanceof StairBlock)) {
				throw new IllegalArgumentException("'" + name + "' is not a stair block.");
			}
			if (block != Blocks.DIRT_PATH && block != Blocks.AIR && block.asItem() == Items.AIR) {
				throw new IllegalArgumentException("'" + name + "' has no survival inventory item.");
			}
			if (weights.putIfAbsent(block, percent) != null) {
				throw new IllegalArgumentException("Block '" + name + "' appears more than once.");
			}
		}

		int total = weights.values().stream().mapToInt(Integer::intValue).sum();
		if (total != 100) throw new IllegalArgumentException("Palette percentages must total 100; this palette totals " + total + ".");
		return new Palette(weights.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue())).toList(), false);
	}

	public static Palette parseBracketed(final String input, final boolean allowDirtPath, final boolean stairsOnly) {
		if (input == null || input.isBlank()) throw new IllegalArgumentException("The material palette is empty.");
		List<String> translated = new ArrayList<>();
		for (String rawEntry : input.split(",", -1)) {
			String token = rawEntry.trim();
			Matcher matcher = BRACKET_ENTRY.matcher(token);
			if (!matcher.matches()) {
				throw new IllegalArgumentException("Use material[percent] entries separated by commas (for example stone[100]).");
			}
			translated.add(matcher.group(1).trim() + ":" + matcher.group(2));
		}
		Palette parsed = parse(String.join(",", translated), allowDirtPath, stairsOnly);
		return new Palette(parsed.entries, true);
	}

	public List<Entry> exactAssignments(final int size, final long seed) {
		if (size <= 0) return List.of();
		List<Quota> quotas = new ArrayList<>();
		int allocated = 0;
		for (int index = 0; index < entries.size(); index++) {
			Entry entry = entries.get(index);
			long product = (long) size * entry.percent();
			int count = (int) (product / 100);
			allocated += count;
			quotas.add(new Quota(index, entry, count, (int) (product % 100)));
		}
		quotas.stream()
			.sorted(Comparator.comparingInt(Quota::remainder).reversed().thenComparingInt(Quota::index))
			.limit(size - allocated)
			.forEach(Quota::addOne);

		List<Entry> result = new ArrayList<>(size);
		quotas.stream().sorted(Comparator.comparingInt(Quota::index)).forEach(quota -> {
			for (int i = 0; i < quota.count; i++) result.add(quota.entry);
		});
		Collections.shuffle(result, new Random(seed));
		return result;
	}

	public Entry pick(final long hash) {
		return pickFrom(entries, hash);
	}

	public Entry pickWithoutDirtPath(final long hash) {
		return pickFrom(entries.stream().filter(entry -> entry.block() != Blocks.DIRT_PATH).toList(), hash);
	}

	private static Entry pickFrom(final List<Entry> candidates, final long hash) {
		if (candidates.isEmpty()) return null;
		int total = candidates.stream().mapToInt(Entry::percent).sum();
		int target = Math.floorMod(hash, total);
		for (Entry entry : candidates) {
			target -= entry.percent();
			if (target < 0) return entry;
		}
		return candidates.getLast();
	}

	public Entry entryFor(final Block block) {
		for (Entry entry : entries) if (entry.block() == block) return entry;
		return null;
	}

	public List<Entry> entries() {
		return entries;
	}

	public String canonical() {
		return canonical;
	}

	public record Entry(Block block, int percent) {
	}

	private static final class Quota {
		private final int index;
		private final Entry entry;
		private int count;
		private final int remainder;

		private Quota(final int index, final Entry entry, final int count, final int remainder) {
			this.index = index;
			this.entry = entry;
			this.count = count;
			this.remainder = remainder;
		}

		private int index() {
			return index;
		}

		private int remainder() {
			return remainder;
		}

		private void addOne() {
			count++;
		}
	}
}
