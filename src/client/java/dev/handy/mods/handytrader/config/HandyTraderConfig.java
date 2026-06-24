package dev.handy.mods.handytrader.config;

import dev.handy.mods.handytrader.HandyTrader;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HandyTraderConfig {

	private static final Path CONFIG_PATH = FabricLoader.getInstance()
			.getConfigDir().resolve(HandyTrader.MOD_ID + ".json");
	// Legacy path from before the v2.1 mod-id rename (handytraders → handytrader).
	// Migrated once on first load so user settings carry over; safe to remove after a few releases.
	private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance()
			.getConfigDir().resolve("handytraders.json");

	private static HandyTraderConfig INSTANCE;

	@SerialEntry public boolean enableFavorites = true;
	/** Shift-click a favorited trade to repeat it until inputs run out or the trade locks. */
	@SerialEntry public boolean enableBulkTrade = true;
	/** Extend Shift-click bulk-trading to every trade, not just favorited ones. */
	@SerialEntry public boolean bulkTradeAllTrades = false;
	/** Safety ceiling on how many trades a single bulk action performs. */
	@SerialEntry public int bulkTradeMax = 256;

	public HandyTraderConfig() {}

	public static HandyTraderConfig get() {
		if (INSTANCE == null) {
			load();
		}
		return INSTANCE;
	}

	public static void load() {
		migrateLegacyConfigIfNeeded();
		if (yaclLoaded()) {
			YaclStorage.HANDLER.load();
			INSTANCE = YaclStorage.HANDLER.instance();
		} else {
			// Without YACL, the config screen can't be opened anyway — run with defaults.
			// Any previously persisted config sits on disk and gets picked up the moment YACL is installed.
			INSTANCE = new HandyTraderConfig();
		}
	}

	public static void save() {
		if (yaclLoaded()) {
			YaclStorage.HANDLER.save();
		}
	}

	private static boolean yaclLoaded() {
		return FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3");
	}

	private static void migrateLegacyConfigIfNeeded() {
		if (!Files.exists(CONFIG_PATH) && Files.exists(LEGACY_CONFIG_PATH)) {
			try {
				Files.copy(LEGACY_CONFIG_PATH, CONFIG_PATH);
				HandyTrader.LOGGER.info("Migrated config from {} to {}",
						LEGACY_CONFIG_PATH.getFileName(), CONFIG_PATH.getFileName());
			} catch (IOException e) {
				HandyTrader.LOGGER.warn("Failed to migrate legacy config from {}",
						LEGACY_CONFIG_PATH.getFileName(), e);
			}
		}
	}

	/**
	 * Holds the YACL handler. Inner-class loading is lazy in the JVM, so this class is only
	 * resolved when {@link #yaclLoaded()} is true and we actually reference it. That keeps
	 * YACL classes off the always-executed code path and avoids a {@code NoClassDefFoundError}
	 * for users who run without YACL installed.
	 */
	private static final class YaclStorage {
		static final ConfigClassHandler<HandyTraderConfig> HANDLER =
				ConfigClassHandler.createBuilder(HandyTraderConfig.class)
						.id(Identifier.fromNamespaceAndPath(HandyTrader.MOD_ID, "config"))
						.serializer(cfg -> GsonConfigSerializerBuilder.create(cfg)
								.setPath(CONFIG_PATH)
								.build())
						.build();
	}
}
