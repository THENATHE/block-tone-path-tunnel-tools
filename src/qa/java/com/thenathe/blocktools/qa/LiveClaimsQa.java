package com.thenathe.blocktools.qa;

import com.thenathe.blocktools.compat.BuildPermissions;
import com.thenathe.blocktools.inventory.InventoryMaterials;
import com.thenathe.blocktools.tunnel.TunnelService;
import com.thenathe.blocktools.tunnel.TunnelSpec;
import com.thenathe.blocktools.tunnel.TunnelStateManager.TunnelState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real optional-mod integration checks, compiled without a Precious Stones dependency. */
public final class LiveClaimsQa {
    private static final BlockPos CENTER = new BlockPos(320, 80, 320);
    private int assertions;

    private LiveClaimsQa() { }

    public static int run(final ServerPlayer player, final ServerLevel level) throws Exception {
        if (!FabricLoader.getInstance().isModLoaded("preciousstones")) return 0;
        return new LiveClaimsQa().execute(player, level);
    }

    private int execute(final ServerPlayer player, final ServerLevel level) throws Exception {
        Class<?> modClass = Class.forName("com.thenathe.preciousstones.PreciousStonesMod");
        Class<?> definitionClass = Class.forName("com.thenathe.preciousstones.config.FieldDefinition");
        Class<?> fieldClass = Class.forName("com.thenathe.preciousstones.field.ProtectionField");
        Class<?> ownedClass = Class.forName("com.thenathe.preciousstones.field.OwnedBlock");
        Object store = modClass.getField("FIELDS").get(null);
        Object config = modClass.getField("CONFIG").get(null);
        Object definition = config.getClass().getMethod("byId", String.class).invoke(config, "city_protection");
        check(definition != null, "live Precious Stones default protection definition exists");
        check(definitionClass.getField("preventBreak").getBoolean(definition)
            && definitionClass.getField("preventPlace").getBoolean(definition), "test field protects both replacement operations");
        check((boolean) store.getClass().getMethod("writable").invoke(store), "live claim store accepts disposable fixtures");

        for (int x = 19; x <= 21; x++) for (int z = 19; z <= 21; z++) level.getChunk(x, z);
        BlockPos normalCenter = CENTER.south();
        BlockPos anchor = CENTER.east();
        BlockPos ownedPos = normalCenter.east().above(2);
        String dimension = level.dimension().identifier().toString();
        UUID outsider = UUID.fromString("549a6a80-1ba4-4fd7-b5b7-a90be837c871");
        Object field = fieldClass.getMethod("create", ServerLevel.class, BlockPos.class, definitionClass, UUID.class, String.class)
            .invoke(null, level, anchor, definition, outsider, "DisposableClaimsOwner");
        UUID fieldId = (UUID) fieldClass.getField("id").get(field);
        Method add = store.getClass().getMethod("add", fieldClass);
        Method remove = store.getClass().getMethod("remove", UUID.class);
        Method addOwned = store.getClass().getMethod("addOwnedBlock", ownedClass);
        Method removeOwned = store.getClass().getMethod("removeOwnedBlock", String.class, BlockPos.class);
        Method atStone = store.getClass().getMethod("atStone", String.class, BlockPos.class);
        Method ownedAt = store.getClass().getMethod("ownedBlockAt", String.class, BlockPos.class);
        check(atStone.invoke(store, dimension, anchor) == null && ownedAt.invoke(store, dimension, ownedPos) == null,
            "disposable fixture coordinates have no existing ownership records");
        Method placeSlice = TunnelService.class.getDeclaredMethod("placeSlice", ServerPlayer.class, TunnelState.class, BlockPos.class, Direction.class);
        placeSlice.setAccessible(true);
        Constructor<TunnelState> stateConstructor = TunnelState.class.getDeclaredConstructor(int.class, int.class);
        stateConstructor.setAccessible(true);
        TunnelState tunnel = stateConstructor.newInstance(2, 3);
        tunnel.configure(TunnelSpec.parse("width:2 height:3 to:bricks[100]", 2, 3));

        Map<BlockPos, BlockState> originalWorld = snapshot(level);
        List<ItemStack> originalInventory = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            originalInventory.add(player.getInventory().getItem(slot).copy());
        }
        ItemStack originalOffhand = player.getItemInHand(InteractionHand.OFF_HAND).copy();
        GameType originalMode = player.gameMode.getGameModeForPlayer();
        boolean fieldAdded = false;
        boolean ownedAdded = false;
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 64));
            prepare(level, CENTER);
            prepare(level, normalCenter);
            put(level, anchor, Blocks.DIAMOND_BLOCK);
            add.invoke(store, field);
            fieldAdded = true;
            check(atStone.invoke(store, dimension, anchor) == field, "real claim store indexes its structural stone");
            check(!BuildPermissions.canChange(player, normalCenter.below()), "untrusted survival player cannot replace inside a real claim");
            assertRejectedUnchanged(player, level, placeSlice, tunnel, normalCenter,
                "unauthorized claimed tunnel slice");

            fieldClass.getField("ownerId").set(field, player.getUUID());
            fieldClass.getField("ownerName").set(field, player.getGameProfile().name());
            check(BuildPermissions.canChange(player, normalCenter.below()), "claim owner may replace ordinary blocks inside the real claim");
            check(!BuildPermissions.canChange(player, anchor), "even claim owner cannot directly replace the structural claim stone");
            assertRejectedUnchanged(player, level, placeSlice, tunnel, CENTER,
                "owner tunnel slice containing a claim stone");
            check(atStone.invoke(store, dimension, anchor) == field && level.getBlockState(anchor).is(Blocks.DIAMOND_BLOCK),
                "rejected owner slice preserves claim indexing and physical stone");
            check((boolean) placeSlice.invoke(null, player, tunnel, normalCenter, Direction.SOUTH),
                "claim owner can execute a normal tunnel slice through the integration");
            check(materialCount(player) == 54, "authorized claimed slice charges exactly ten shell blocks");
            check(level.getBlockState(normalCenter.below()).is(Blocks.BRICKS)
                && level.getBlockState(normalCenter.east().above(2)).is(Blocks.BRICKS), "authorized claimed slice completes both early and late shell writes");

            prepare(level, normalCenter);
            player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 64));
            Object owned = ownedClass.getConstructor(String.class, BlockPos.class, String.class, UUID.class, String.class)
                .newInstance(dimension, ownedPos, "minecraft:stone", player.getUUID(), player.getGameProfile().name());
            addOwned.invoke(store, owned);
            ownedAdded = true;
            check(ownedAt.invoke(store, dimension, ownedPos) == owned, "real claim store indexes the owned block fixture");
            check(!BuildPermissions.canChange(player, ownedPos), "owned unbreakable block cannot be directly replaced even by its owner");
            assertRejectedUnchanged(player, level, placeSlice, tunnel, normalCenter,
                "owner tunnel slice containing an owned unbreakable block");
            check(ownedAt.invoke(store, dimension, ownedPos) == owned, "rejected owned-block slice preserves ownership indexing");
            ownedClass.getField("ownerId").set(owned, outsider);
            check(!BuildPermissions.canChange(player, ownedPos), "another player's owned unbreakable block is also refused");
            return assertions;
        } finally {
            if (ownedAdded) removeOwned.invoke(store, dimension, ownedPos);
            if (fieldAdded) remove.invoke(store, fieldId);
            for (Map.Entry<BlockPos, BlockState> entry : originalWorld.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
            }
            player.getInventory().clearContent();
            for (int slot = 0; slot < originalInventory.size(); slot++) player.getInventory().setItem(slot, originalInventory.get(slot));
            player.setItemInHand(InteractionHand.OFF_HAND, originalOffhand);
            player.setGameMode(originalMode);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    private void assertRejectedUnchanged(final ServerPlayer player, final ServerLevel level, final Method placeSlice,
        final TunnelState tunnel, final BlockPos center, final String description) throws Exception {
        Map<BlockPos, BlockState> before = snapshot(level);
        int materials = materialCount(player);
        check(!(boolean) placeSlice.invoke(null, player, tunnel, center, Direction.SOUTH), description + " is refused");
        check(before.equals(snapshot(level)), description + " leaves all surrounding blocks unchanged");
        check(materialCount(player) == materials, description + " charges no materials");
    }

    private static int materialCount(final ServerPlayer player) {
        return InventoryMaterials.count(player).getOrDefault(Items.BRICKS, 0);
    }

    private static Map<BlockPos, BlockState> snapshot(final ServerLevel level) {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (int x = -3; x <= 3; x++) for (int y = -1; y <= 3; y++) for (int z = 0; z <= 1; z++) {
            BlockPos pos = CENTER.offset(x, y, z);
            result.put(pos, level.getBlockState(pos));
        }
        return result;
    }

    private static void prepare(final ServerLevel level, final BlockPos center) {
        for (int x = -3; x <= 3; x++) for (int y = -1; y <= 3; y++) put(level, center.offset(x, y, 0), Blocks.AIR);
        for (int x = -1; x <= 0; x++) {
            put(level, center.offset(x, -1, 0), Blocks.STONE);
            put(level, center.offset(x, 3, 0), Blocks.STONE);
        }
        for (int y = 0; y < 3; y++) {
            put(level, center.offset(1, y, 0), Blocks.STONE);
            put(level, center.offset(-2, y, 0), Blocks.STONE);
        }
    }

    private static void put(final ServerLevel level, final BlockPos pos, final Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
    }

    private void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
