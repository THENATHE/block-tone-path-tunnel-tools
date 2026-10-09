package com.thenathe.blocktools.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.thenathe.blocktools.BlockToolsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private final Path path = FabricLoader.getInstance().getConfigDir().resolve("block-tone-path-tools.json");
	private volatile ModConfig config = new ModConfig();
	private Item selectionItem = Items.BREEZE_ROD;

	public synchronized void load() {
		try {
			Files.createDirectories(path.getParent());
			if (Files.notExists(path)) {
				config = new ModConfig();
				config.normalize();
				save();
			} else {
				try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
					if (loaded != null && loaded.configVersion != null && loaded.configVersion > 4) {
                        BlockToolsMod.LOGGER.error("{} uses a future config version {}; retaining defaults without rewriting it.", path, loaded.configVersion);
                        config = new ModConfig();
                        config.normalize();
                        selectionItem = resolveItem(config.selectionItem, Items.BREEZE_ROD, "selectionItem");
                        return;
                    }
                    config = loaded == null ? new ModConfig() : loaded;
				}
				Integer previousVersion = config.configVersion;
				config.normalize();
				if (!Objects.equals(previousVersion, config.configVersion)) save();
			}
		} catch (IOException | JsonParseException exception) {
			BlockToolsMod.LOGGER.error("Could not load {}; retaining safe defaults.", path, exception);
			config = new ModConfig();
			config.normalize();
		}

		selectionItem = resolveItem(config.selectionItem, Items.BREEZE_ROD, "selectionItem");
		BlockToolsMod.LOGGER.info("Loaded Block Tone, Path & Tunnel Tools configuration from {}.", path);
	}

	private Item resolveItem(final String value, final Item fallback, final String field) {
		Identifier id = Identifier.tryParse(value);
		Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		if (item == null || item == Items.AIR) {
			BlockToolsMod.LOGGER.warn("Invalid {} '{}'; using {}.", field, value, BuiltInRegistries.ITEM.getKey(fallback));
			return fallback;
		}
		return item;
	}

	private void save() throws IOException {
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
		}
		try {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public ModConfig config() {
		return config;
	}

	public Item selectionItem() {
		return com.thenathe.blocktools.compat.PolymerTools.selectionItem(selectionItem);
    }

    /** The configured legacy item remains available for fallback without rewriting it. */
    public Item legacySelectionItem() {
        return selectionItem;
    }

    public Item pathItem() {
        return com.thenathe.blocktools.compat.PolymerTools.pathItem(selectionItem);
	}

	public Path path() {
		return path;
	}
}
