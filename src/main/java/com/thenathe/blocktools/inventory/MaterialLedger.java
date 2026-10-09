package com.thenathe.blocktools.inventory;

import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reduces the inputs and recoverable outputs of one atomic world edit to the
 * actual inventory deficit and surplus. A removed block can therefore fund a
 * placement of the same item within the same transaction.
 */
public final class MaterialLedger {
	private MaterialLedger() {
	}

	public static Delta net(final Map<Item, Integer> grossRequired, final Map<Item, Integer> grossReturned) {
		Map<Item, Integer> required = new LinkedHashMap<>();
		Map<Item, Integer> returned = new LinkedHashMap<>();
		Set<Item> items = new LinkedHashSet<>();
		items.addAll(grossRequired.keySet());
		items.addAll(grossReturned.keySet());
		for (Item item : items) {
			int difference = positive(grossRequired.getOrDefault(item, 0), "required")
				- positive(grossReturned.getOrDefault(item, 0), "returned");
			if (difference > 0) required.put(item, difference);
			if (difference < 0) returned.put(item, -difference);
		}
		return new Delta(Map.copyOf(required), Map.copyOf(returned));
	}

	private static int positive(final int value, final String side) {
		if (value < 0) throw new IllegalArgumentException("Material " + side + " count cannot be negative.");
		return value;
	}

	public record Delta(Map<Item, Integer> required, Map<Item, Integer> returned) {
	}
}
