package dev.handy.mods.handytrader.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import dev.handy.mods.handytrader.HandyTrader;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Per-villager trade favorites storage.
 * Persisted as JSON at config/handytrader-favorites.json.
 *
 * Structure: { "<villager-uuid>": { "favorites": ["<trade-hash>", ...] } }
 *
 * Global favorites (shared by every villager) live in a separate file,
 * config/handytrader-global-favorites.json, as a flat array of trade hashes.
 */
@Environment(EnvType.CLIENT)
public final class TradeFavorites {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FAVORITES_PATH = FabricLoader.getInstance()
			.getConfigDir().resolve("handytrader-favorites.json");
	// Legacy path from before the v2.1 mod-id rename (handytraders → handytrader).
	// Read once on first load so user favorites carry over; safe to remove after a few releases.
	private static final Path LEGACY_FAVORITES_PATH = FabricLoader.getInstance()
			.getConfigDir().resolve("handytraders-favorites.json");
	private static final Path GLOBAL_FAVORITES_PATH = FabricLoader.getInstance()
			.getConfigDir().resolve("handytrader-global-favorites.json");
	private static final Type DATA_TYPE = new TypeToken<Map<String, VillagerData>>() {}.getType();
	private static final Type GLOBAL_TYPE = new TypeToken<Set<String>>() {}.getType();
	private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "HandyTrader-favorites-save");
		t.setDaemon(true);
		return t;
	});

	private static Map<String, VillagerData> data = new HashMap<>();
	private static Set<String> globalFavorites = new HashSet<>();

	private TradeFavorites() {}

	public static boolean isFavorite(UUID villagerUUID, String tradeHash) {
		VillagerData vd = data.get(villagerUUID.toString());
		return vd != null && vd.favorites.contains(tradeHash);
	}

	/**
	 * One-shot migration: if {@code legacyHash} exists in this villager's favorites,
	 * replace it with {@code newHash} and return true. Used to upgrade favorites
	 * stored under the pre-2.1.0-beta.3 hashCode-based format to the new SHA-1
	 * format on first encounter, instead of silently orphaning them. The save is
	 * dispatched off-thread by save() so the swap costs nothing at the call site.
	 */
	public static boolean migrateLegacyHash(UUID villagerUUID, String legacyHash, String newHash) {
		VillagerData vd = data.get(villagerUUID.toString());
		if (vd == null || !vd.favorites.remove(legacyHash)) return false;
		vd.favorites.add(newHash);
		save();
		return true;
	}

	public static void toggleFavorite(UUID villagerUUID, String tradeHash) {
		String key = villagerUUID.toString();
		VillagerData vd = data.computeIfAbsent(key, k -> new VillagerData());
		if (!vd.favorites.remove(tradeHash)) {
			vd.favorites.add(tradeHash);
		}
		// Clean up empty entries
		if (vd.favorites.isEmpty()) {
			data.remove(key);
		}
	}

	// -- Global favorites (apply to every villager) --

	public static boolean isGlobalFavorite(String tradeHash) {
		return globalFavorites.contains(tradeHash);
	}

	public static void toggleGlobalFavorite(String tradeHash) {
		if (!globalFavorites.remove(tradeHash)) {
			globalFavorites.add(tradeHash);
		}
	}

	public static void load() {
		loadGlobal();

		if (!Files.exists(FAVORITES_PATH) && Files.exists(LEGACY_FAVORITES_PATH)) {
			try {
				Files.copy(LEGACY_FAVORITES_PATH, FAVORITES_PATH);
				HandyTrader.LOGGER.info("Migrated favorites from {} to {}",
						LEGACY_FAVORITES_PATH.getFileName(), FAVORITES_PATH.getFileName());
			} catch (IOException e) {
				HandyTrader.LOGGER.warn("Failed to migrate legacy favorites from {}",
						LEGACY_FAVORITES_PATH.getFileName(), e);
			}
		}

		if (Files.exists(FAVORITES_PATH)) {
			try (Reader reader = Files.newBufferedReader(FAVORITES_PATH)) {
				Map<String, VillagerData> loaded = GSON.fromJson(reader, DATA_TYPE);
				if (loaded != null) {
					data = loaded;
					int totalFavs = loaded.values().stream()
							.mapToInt(vd -> vd.favorites.size()).sum();
					HandyTrader.LOGGER.info("Loaded {} favorites for {} villagers from {}",
							totalFavs, loaded.size(), FAVORITES_PATH);
				}
			} catch (IOException | JsonParseException e) {
				HandyTrader.LOGGER.warn("Failed to load trade favorites", e);
			}
		} else {
			HandyTrader.LOGGER.info("No favorites file at {}", FAVORITES_PATH);
		}
	}

	private static void loadGlobal() {
		if (!Files.exists(GLOBAL_FAVORITES_PATH)) return;
		try (Reader reader = Files.newBufferedReader(GLOBAL_FAVORITES_PATH)) {
			Set<String> loaded = GSON.fromJson(reader, GLOBAL_TYPE);
			if (loaded != null) {
				globalFavorites = new HashSet<>(loaded);
				HandyTrader.LOGGER.info("Loaded {} global favorites from {}",
						globalFavorites.size(), GLOBAL_FAVORITES_PATH);
			}
		} catch (IOException | JsonParseException e) {
			HandyTrader.LOGGER.warn("Failed to load global trade favorites", e);
		}
	}

	/** Same threading contract as {@link #save()}: call from the render thread only. */
	public static void saveGlobal() {
		String json = GSON.toJson(globalFavorites, GLOBAL_TYPE);
		SAVE_EXECUTOR.execute(() -> {
			try {
				Files.createDirectories(GLOBAL_FAVORITES_PATH.getParent());
				Files.writeString(GLOBAL_FAVORITES_PATH, json);
			} catch (IOException e) {
				HandyTrader.LOGGER.warn("Failed to save global trade favorites", e);
			}
		});
	}

	public static void save() {
		// Serialize on the caller thread before queuing the I/O. This is *not* a
		// snapshot — Gson iterates the live HashMap. Safety relies on save() only
		// being invoked from the render thread, which is the sole mutator of `data`.
		// If you ever call save() from another thread (e.g. a non-render config
		// callback), wrap a defensive copy of `data` first.
		String json = GSON.toJson(data, DATA_TYPE);
		SAVE_EXECUTOR.execute(() -> {
			try {
				Files.createDirectories(FAVORITES_PATH.getParent());
				Files.writeString(FAVORITES_PATH, json);
			} catch (IOException e) {
				HandyTrader.LOGGER.warn("Failed to save trade favorites", e);
			}
		});
	}

	private static final class VillagerData {
		private Set<String> favorites = new HashSet<>();
	}
}
