package com.thenathe.blocktools.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A partial palette with optional automatic entries whose remainder is shared equally. */
public record OverlayPalette(List<Entry> entries, int explicitPercent, int automaticCount) {
	private static final Pattern ENTRY = Pattern.compile("^([^\\[\\]]+?)(?:\\[(\\d*)])?$", Pattern.CASE_INSENSITIVE);

	public OverlayPalette {
		entries = List.copyOf(entries);
		if (entries.isEmpty()) throw new IllegalArgumentException("The destination needs at least one material.");
		if (explicitPercent < 0 || explicitPercent > 100) {
			throw new IllegalArgumentException("The explicit percentages must total between 0 and 100.");
		}
		if (automaticCount < 0 || automaticCount > entries.size()) {
			throw new IllegalArgumentException("The automatic material count is invalid.");
		}
		if (automaticCount > 0 && explicitPercent >= 100) {
			throw new IllegalArgumentException("Automatic [] materials need some percentage remaining below 100.");
		}
		if (automaticCount == 0 && explicitPercent == 0) {
			throw new IllegalArgumentException("The destination needs a percentage or an automatic material.");
		}
	}

	public static OverlayPalette parse(final String input) {
		if (input == null || input.isBlank()) throw new IllegalArgumentException("The destination needs a material list.");
		List<Entry> entries = new ArrayList<>();
		Set<Block> unique = new LinkedHashSet<>();
		int explicitTotal = 0;
		int automatic = 0;
		for (String raw : input.split(",", -1)) {
			String part = raw.trim();
			Matcher matcher = ENTRY.matcher(part);
			if (!matcher.matches()) {
				throw new IllegalArgumentException("Destination '" + part
					+ "' must be block, block[], or block[percent].");
			}
			String name = matcher.group(1).trim().toLowerCase(Locale.ROOT);
			Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
			Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
			if (block == null) throw new IllegalArgumentException("Unknown destination block '" + name + "'.");
			if (block != Blocks.AIR && block.asItem() == Items.AIR) throw new IllegalArgumentException("Destination block '" + name + "' has no survival inventory item.");
			BlockMaterials.requireSingleBlock(block);
			if (!unique.add(block)) throw new IllegalArgumentException("Destination block '" + name + "' appears more than once.");

			String percentage = matcher.group(2);
			Integer explicit = null;
			if (percentage != null && !percentage.isBlank()) {
				try {
					explicit = Integer.parseInt(percentage);
				} catch (NumberFormatException exception) {
					throw new IllegalArgumentException("Invalid percentage for destination block '" + name + "'.");
				}
				if (explicit <= 0 || explicit > 100) {
					throw new IllegalArgumentException("Destination percentages must be whole numbers from 1 to 100.");
				}
				explicitTotal = Math.addExact(explicitTotal, explicit);
				if (explicitTotal > 100) {
					throw new IllegalArgumentException("The explicit percentages total " + explicitTotal + "; the maximum is 100.");
				}
			} else {
				automatic++;
			}
			entries.add(new Entry(block, explicit));
		}
		return new OverlayPalette(entries, explicitTotal, automatic);
	}

	/** Splits automatic entries equally, as used by regular Blocktone mode. */
	public Distribution exactDistribution(final int targetCount) {
		Allocation allocation = allocation(targetCount);
		Map<Block, Integer> materialCounts = new LinkedHashMap<>(allocation.explicitCounts);
		if (allocation.automaticSlots > 0) {
			int base = allocation.automaticSlots / automaticCount;
			int extra = allocation.automaticSlots % automaticCount;
			int index = 0;
			for (Entry entry : entries) {
				if (!entry.automatic()) continue;
				materialCounts.put(entry.block, base + (index++ < extra ? 1 : 0));
			}
		}
		return new Distribution(
			Collections.unmodifiableMap(new LinkedHashMap<>(materialCounts)), allocation.unchangedCount
		);
	}

	/** Resolves fixed quotas and leaves automatic entries as one shared capacity bucket. */
	public Allocation allocation(final int targetCount) {
		if (targetCount < 0) throw new IllegalArgumentException("Target count cannot be negative.");
		List<Quota> quotas = new ArrayList<>();
		int allocated = 0;
		int index = 0;
		for (Entry entry : entries) {
			if (entry.automatic()) continue;
			long product = (long) targetCount * entry.percent;
			int count = (int) (product / 100L);
			allocated += count;
			quotas.add(new Quota(index++, entry.block, count, product % 100L));
		}
		int remainderPercent = 100 - explicitPercent;
		long remainderProduct = (long) targetCount * remainderPercent;
		Quota remainder = new Quota(index, null, (int) (remainderProduct / 100L), remainderProduct % 100L);
		allocated += remainder.count;
		quotas.add(remainder);
		quotas.stream()
			.sorted(Comparator.comparingLong(Quota::remainder).reversed().thenComparingInt(Quota::index))
			.limit(targetCount - allocated)
			.forEach(Quota::addOne);

		Map<Block, Integer> fixed = new LinkedHashMap<>();
		for (Quota quota : quotas) if (quota.block != null) fixed.put(quota.block, quota.count);
		Map<Block, Integer> orderedFixed = Collections.unmodifiableMap(new LinkedHashMap<>(fixed));
		return automaticCount > 0
			? new Allocation(orderedFixed, remainder.count, 0)
			: new Allocation(orderedFixed, 0, remainder.count);
	}

	public List<Block> automaticBlocks() {
		return entries.stream().filter(Entry::automatic).map(Entry::block).toList();
	}

	public String canonical() {
		return entries.stream()
			.map(entry -> BuiltInRegistries.BLOCK.getKey(entry.block).toShortString()
				+ (entry.automatic() ? "[]" : "[" + entry.percent + "]"))
			.reduce((left, right) -> left + "," + right)
			.orElse("");
	}

	public record Entry(Block block, Integer percent) {
		public boolean automatic() {
			return percent == null;
		}
	}

	public record Allocation(Map<Block, Integer> explicitCounts, int automaticSlots, int unchangedCount) {
	}

	public record Distribution(Map<Block, Integer> materialCounts, int unchangedCount) {
	}

	private static final class Quota {
		private final int index;
		private final Block block;
		private int count;
		private final long remainder;

		private Quota(final int index, final Block block, final int count, final long remainder) {
			this.index = index;
			this.block = block;
			this.count = count;
			this.remainder = remainder;
		}

		private int index() {
			return index;
		}

		private long remainder() {
			return remainder;
		}

		private void addOne() {
			count++;
		}
	}
}
