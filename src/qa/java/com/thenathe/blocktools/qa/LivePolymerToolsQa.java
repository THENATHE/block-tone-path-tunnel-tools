package com.thenathe.blocktools.qa;

import com.google.gson.JsonParser;
import com.thenathe.blocktools.BlockToolsMod;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import com.thenathe.blocktools.sprite.SpritePreferences;
import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Only loaded in the Polymer-installed QA run. */
public final class LivePolymerToolsQa {
    private static int checks;
    public static int creativeAvailability(ServerPlayer player, boolean enabled) {
        int count = 0;
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.withDefaultNamespace("tools_and_utilities"));
        var entries = PolymerCreativeModeTabUtils.getContentsFor(player, tab);
        for (String name : new String[] {"mortar_trowel", "garden_trowel", "prospecting_pick"}) {
            Item item = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, name)).orElseThrow();
            if (entries.main().stream().anyMatch(stack -> stack.is(item)) != enabled)
                throw new AssertionError(name + " Creative Tools & Utilities availability");
            if (entries.search().stream().anyMatch(stack -> stack.is(item)) != enabled)
                throw new AssertionError(name + " Creative search availability");
            count += 2;
        }
        return count;
    }
    public static int run(ServerPlayer player, Item[] tools) throws Exception {
        checks = 0;
        var context = ((PacketContextProvider) player.connection).getPacketContext();
        var registry = player.level().getServer().registryAccess();
        for (Item item : tools) {
            var stack = new ItemStack(item);
            PolymerCommonUtils.setHasResourcePack(context, PolymerResourcePackUtils.getMainUuid(), false);
            var withoutPack = PolymerItemUtils.getPolymerItemStack(stack, context, registry);
            check(withoutPack.is(Items.IRON_NUGGET), "no-pack client base is iron nugget");
            check(Identifier.withDefaultNamespace("iron_nugget").equals(withoutPack.get(DataComponents.ITEM_MODEL)), "no-pack vanilla model fallback");
            PolymerCommonUtils.setHasResourcePack(context, PolymerResourcePackUtils.getMainUuid(), true);
            var withPack = PolymerItemUtils.getPolymerItemStack(stack, context, registry);
            check(withPack.is(Items.IRON_NUGGET), "pack client base is iron nugget");
            check(BuiltInRegistries.ITEM.getKey(item).equals(withPack.get(DataComponents.ITEM_MODEL)), "accepted pack custom model");
            check(PolymerItemUtils.getRealItemStack(withPack, registry).is(item), "custom stack identity roundtrip");
            SpritePreferences.select(player, SpritePreferences.Mode.HIGH);
            var highStack = PolymerItemUtils.getPolymerItemStack(stack, context, registry);
            var id32 = Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, BuiltInRegistries.ITEM.getKey(item).getPath() + "_32");
            check(id32.equals(highStack.get(DataComponents.ITEM_MODEL)), "manual32 selects per-player server model");
            check(PolymerItemUtils.getRealItemStack(highStack, registry).is(item), "32 model preserves custom stack identity");
            PolymerCommonUtils.setHasResourcePack(context, PolymerResourcePackUtils.getMainUuid(), false);
            check(Identifier.withDefaultNamespace("iron_nugget").equals(PolymerItemUtils.getPolymerItemStack(stack, context, registry).get(DataComponents.ITEM_MODEL)),
                "manual32 without client or accepted pack keeps vanilla fallback");
            PolymerCommonUtils.setHasResourcePack(context, PolymerResourcePackUtils.getMainUuid(), true);
            SpritePreferences.select(player, SpritePreferences.Mode.AUTO);
        }
        Item mortar = tools[0];
        var stack = new ItemStack(mortar);
        var pos = new BlockPos(256, 99, 256);
        var state = player.level().getBlockState(pos);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        check(((PolymerItem) mortar).handleMiningOnServer(stack, state, pos, player), "vanilla mining routed to server selection");
        check(!mortar.canDestroyBlock(stack, state, player.level(), pos, player), "mortar cannot break selected block");
        check(((PolymerItem) mortar).isPolymerBlockInteraction(state, player, InteractionHand.MAIN_HAND,
            stack, player.level(), hit, InteractionResult.SUCCESS), "mortar acknowledges action and suppresses vanilla offhand retry");
        var built = PolymerResourcePackUtils.getInstance().build(Path.of("qa-blocktools-resource-pack.zip").toAbsolutePath());
        check(!built.hadIssues(), "Polymer pack generates without issues");
        try (ZipFile zip = new ZipFile(built.path().toFile())) {
            check(zip.getEntry("pack.mcmeta") != null, "generated pack metadata");
            for (Item item : tools) {
                String name = BuiltInRegistries.ITEM.getKey(item).getPath();
                String base = "assets/" + BlockToolsMod.MOD_ID + "/";
                check(zip.getEntry(base + "items/" + name + ".json") != null, "pack includes item definition " + name);
                check(zip.getEntry(base + "items/" + name + "_32.json") != null, "pack includes32 item definition " + name);
                check(zip.getEntry(base + "models/item/" + name + "_32.json") != null, "pack includes32 model " + name);
                try (var input = zip.getInputStream(zip.getEntry(base + "textures/item/" + name + "_32.png"))) {
                    var png = ImageIO.read(input);
                    check(png != null && png.getWidth() == 32 && png.getHeight() == 32, "32x32 alternate sprite " + name);
                }
                try (var reader = new InputStreamReader(zip.getInputStream(zip.getEntry(base + "models/item/" + name + ".json")), StandardCharsets.UTF_8)) {
                    var model = JsonParser.parseReader(reader).getAsJsonObject();
                    check("minecraft:item/handheld".equals(model.get("parent").getAsString()), "handheld transforms " + name);
                    check((BlockToolsMod.MOD_ID + ":item/" + name).equals(model.getAsJsonObject("textures").get("layer0").getAsString()), "correct sprite reference " + name);
                }
                try (var input = zip.getInputStream(zip.getEntry(base + "textures/item/" + name + ".png"))) {
                    var png = ImageIO.read(input);
                    check(png != null && png.getWidth() == 16 && png.getHeight() == 16, "16x16 sprite " + name);
                    boolean pixelLocked = true;
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
                        int alpha = png.getRGB(x, y) >>> 24;
                        pixelLocked &= alpha == 0 || alpha == 255;
                    }
                    check(pixelLocked, "no semitransparent pixels " + name);
                }
            }
        }
        return checks;
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
}
