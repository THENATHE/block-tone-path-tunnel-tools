package com.thenathe.blocktools.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;

public final class InventoryMaterialsTest {
	private InventoryMaterialsTest() {
	}

	public static void run() {
		bindTestComponents(Blocks.GRANITE.asItem(), 64);
		bindTestComponents(Blocks.POLISHED_GRANITE.asItem(), 64);
		bindTestComponents(Blocks.SHULKER_BOX.asItem(), 1);
		require(InventoryMaterials.isUsableShulkerSlot(0), "first shulker slot was excluded");
		require(InventoryMaterials.isUsableShulkerSlot(26), "last shulker slot was excluded");
		require(!InventoryMaterials.isUsableShulkerSlot(27),
			"generic container data beyond the 27-slot shulker boundary was treated as usable inventory");
		require(!InventoryMaterials.isUsableShulkerSlot(-1), "negative container slot was treated as usable inventory");
		testTwoMaterialsInOneShulker();
		testMixedLooseAndShulker();
		testFailedReservationIsAtomic();
		testCustomMaterialsArePreserved();
		testMixedShortageAndReuse();
		testContainerTailPreserved();
	}

	private static void testTwoMaterialsInOneShulker() {
		ItemStack shulker = shulkerWith(
			new ItemStack(Blocks.GRANITE, 64),
			new ItemStack(Blocks.GRANITE, 64),
			new ItemStack(Blocks.GRANITE, 64),
			new ItemStack(Blocks.GRANITE, 8),
			new ItemStack(Blocks.POLISHED_GRANITE, 64),
			new ItemStack(Blocks.POLISHED_GRANITE, 64),
			new ItemStack(Blocks.POLISHED_GRANITE, 27)
		);
		InventoryMaterials.InventorySnapshot snapshot = InventoryMaterials.snapshot(List.of(shulker));
		require(snapshot.counts().get(Blocks.GRANITE.asItem()) == 200,
			"snapshot did not count all 200 granite in the shulker");
		require(snapshot.counts().get(Blocks.POLISHED_GRANITE.asItem()) == 155,
			"snapshot did not count all 155 polished granite in the shulker");
		Map<net.minecraft.world.item.Item, InventoryMaterials.Shortage> shortages = snapshot.consume(Map.of(
			Blocks.GRANITE.asItem(), 200,
			Blocks.POLISHED_GRANITE.asItem(), 155
		));
		require(shortages.isEmpty(), "snapshot counted two shulker materials but could not consume them: " + shortages);
		require(InventoryMaterials.countShulkerContents(shulker.get(DataComponents.CONTAINER)).isEmpty(),
			"successful shulker reservation did not commit both removals");
	}

	private static void testMixedLooseAndShulker() {
		ItemStack loose = new ItemStack(Blocks.GRANITE, 5);
		ItemStack shulker = shulkerWith(new ItemStack(Blocks.GRANITE, 10));
		InventoryMaterials.InventorySnapshot snapshot = InventoryMaterials.snapshot(List.of(loose, shulker));
		require(snapshot.counts().get(Blocks.GRANITE.asItem()) == 15,
			"mixed loose and shulker granite was not combined");
		require(snapshot.consume(Map.of(Blocks.GRANITE.asItem(), 12)).isEmpty(),
			"mixed loose and shulker granite could not be consumed");
		require(loose.isEmpty(), "mixed reservation did not consume loose inventory first");
		require(InventoryMaterials.countShulkerContents(shulker.get(DataComponents.CONTAINER))
			.get(Blocks.GRANITE.asItem()) == 3, "mixed reservation consumed the wrong shulker amount");
	}

	private static void testFailedReservationIsAtomic() {
		ItemStack shulker = shulkerWith(new ItemStack(Blocks.GRANITE, 10));
		InventoryMaterials.InventorySnapshot snapshot = InventoryMaterials.snapshot(List.of(shulker));
		Map<net.minecraft.world.item.Item, InventoryMaterials.Shortage> shortages = snapshot.consume(
			Map.of(Blocks.GRANITE.asItem(), 11)
		);
		require(shortages.get(Blocks.GRANITE.asItem()).available() == 10,
			"failed shulker reservation reported the wrong availability");
		require(InventoryMaterials.countShulkerContents(shulker.get(DataComponents.CONTAINER))
			.get(Blocks.GRANITE.asItem()) == 10, "failed shulker reservation partially changed the box");
	}

    private static void testCustomMaterialsArePreserved() {
        ItemStack named = new ItemStack(Blocks.GRANITE, 10);
        named.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keepsake"));
        ItemStack plain = new ItemStack(Blocks.GRANITE, 3);
        ItemStack shulker = shulkerWith(named, plain);
        InventoryMaterials.InventorySnapshot snapshot = InventoryMaterials.snapshot(List.of(named, shulker));
        require(snapshot.counts().get(Blocks.GRANITE.asItem()) == 3, "custom block stacks counted as disposable materials");
        require(snapshot.consume(Map.of(Blocks.GRANITE.asItem(), 3)).isEmpty(), "plain material unavailable beside custom stack");
        require(named.getCount() == 10, "custom loose stack was consumed");
        NonNullList<ItemStack> slots = InventoryMaterials.copyContainerSlots(shulker.get(DataComponents.CONTAINER));
        require(slots.get(0).getCount() == 10 && slots.get(0).has(DataComponents.CUSTOM_NAME), "custom shulker contents were consumed");
    }

    private static void testMixedShortageAndReuse() {
        ItemStack loose = new ItemStack(Blocks.GRANITE, 2);
        ItemStack shulker = shulkerWith(new ItemStack(Blocks.GRANITE, 5));
        InventoryMaterials.InventorySnapshot snapshot = InventoryMaterials.snapshot(List.of(loose, shulker));
        Map<Item, InventoryMaterials.Shortage> shortages = snapshot.consume(Map.of(
            Blocks.GRANITE.asItem(), 6, Blocks.POLISHED_GRANITE.asItem(), 1));
        require(shortages.size() == 1 && shortages.get(Blocks.POLISHED_GRANITE.asItem()).missing() == 1, "wrong multi-material shortage");
        require(loose.getCount() == 2, "mixed failure consumed loose material");
        require(InventoryMaterials.countShulkerContents(shulker.get(DataComponents.CONTAINER)).get(Blocks.GRANITE.asItem()) == 5, "mixed failure consumed box material");
        try { snapshot.consume(Map.of()); throw new AssertionError("snapshot reused after reservation"); }
        catch (IllegalStateException expected) { }
        try { InventoryMaterials.snapshot(List.of(loose)).consume(Map.of(Blocks.GRANITE.asItem(), -1)); throw new AssertionError("negative request accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void testContainerTailPreserved() {
        NonNullList<ItemStack> slots = NonNullList.withSize(40, ItemStack.EMPTY);
        slots.set(0, new ItemStack(Blocks.GRANITE, 5));
        slots.set(30, new ItemStack(Blocks.GRANITE, 64));
        ItemStack shulker = new ItemStack(Blocks.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
        InventoryMaterials.InventorySnapshot snapshot = InventoryMaterials.snapshot(List.of(shulker));
        require(snapshot.counts().get(Blocks.GRANITE.asItem()) == 5, "out-of-range slot inflated availability");
        require(snapshot.consume(Map.of(Blocks.GRANITE.asItem(), 5)).isEmpty(), "normal slots could not be reserved");
        NonNullList<ItemStack> after = InventoryMaterials.copyContainerSlots(shulker.get(DataComponents.CONTAINER));
        require(after.get(30).getCount() == 64, "out-of-range component data was lost during commit");
    }

	private static ItemStack shulkerWith(final ItemStack... contents) {
		NonNullList<ItemStack> slots = NonNullList.withSize(27, ItemStack.EMPTY);
		for (int index = 0; index < contents.length; index++) slots.set(index, contents[index]);
		ItemStack shulker = new ItemStack(Blocks.SHULKER_BOX);
		shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
		return shulker;
	}

	private static void bindTestComponents(final Item item, final int maximumStackSize) {
		Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(item);
		if (holder instanceof Holder.Reference<Item> reference && !reference.areComponentsBound()) {
			reference.bindComponents(DataComponentMap.builder()
				.set(DataComponents.MAX_STACK_SIZE, maximumStackSize)
				.build());
		}
	}

	private static void require(final boolean condition, final String message) {
		if (!condition) throw new AssertionError(message);
	}
}
