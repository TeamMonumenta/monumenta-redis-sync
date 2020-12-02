package com.playmonumenta.redissync.example;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class ExampleServerListener implements Listener {
	/*################################################################################
	 * Edit at least this section!
	 * You can change the rest too, but you only need to adapt this section to make something usable
	 */

	/* Change this to something that uniquely identifies the data you want to save for this plugin */
	private static final String IDENTIFIER = "ExampleRedisDataPlugin";

	/*
	 * This is useful to save data periodically, not only when players leave
	 * Set to 0 to disable
	 */
	private static final int SAVE_PERIOD = 20 * 60 * 6; // 6 minutes

	/* You probably want to change the name of this data class, or make your own */
	public static class CustomData {
		private final Map<String, Integer> mData = new HashMap<>();

		/* Some example functions to work with.
		 * You should replace these with something you actually want to store/manipulate
		 */
		public void setPoints(final String key, final int value) {
			mData.put(key, value);
		}

		public Integer getPoints(final String key) {
			return mData.get(key);
		}

		/*
		 * In this example, read the database string first to JSON, then unpack the JSON to the data structure
		 * You can store anything in here, as long as you can pack it to a String and unpack it again
		 */
		private static CustomData fromJsonString(String data) {
			CustomData newObject = new CustomData();

			final JsonObject obj = new Gson().fromJson(data, JsonObject.class);
			for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
				newObject.mData.put(entry.getKey(), entry.getValue().getAsInt());
			}

			return newObject;
		}

		/*
		 * Store this data structure to a string suitable for storing in the database.
		 * Unicode characters or even arbitrary bytes can be stored in this string
		 */
		private String toJsonString() {
			final JsonObject obj = new JsonObject();
			for (Map.Entry<String, Integer> entry : mData.entrySet()) {
				obj.addProperty(entry.getKey(), entry.getValue());
			}

			return new Gson().toJson(obj);
		}
	}

	/*
	 * Edit at least this section!
	 *################################################################################*/

	private final Map<UUID, CustomData> mAllPlayerData = new HashMap<>();
	private final Plugin mPlugin;

	public ExampleServerListener(final Plugin plugin) {
		mPlugin = plugin;
	}

	/*
	 * When player joins, load their data and store it locally in a map
	 *
	 * It's important to work with a local copy of the data - it takes too long
	 * to go to the database everytime you want to access the data.
	 * Store it locally, manipulate it, and save it afterwards.
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void playerJoinEvent(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		MonumentaRedisSyncAPI.loadPlayerPluginData(player.getUniqueId(), IDENTIFIER, mPlugin, (String data, Exception exception) -> {
			if (exception != null) {
				mPlugin.getLogger().severe("Failed to get data for player " + player.getName() + ": " + exception.getMessage());
				exception.printStackTrace();
			} else if (data == null || data.isEmpty()) {
				mPlugin.getLogger().info("No data for for player " + player.getName());
			} else {
				mAllPlayerData.put(player.getUniqueId(), CustomData.fromJsonString(data));
				mPlugin.getLogger().info("Loaded data for player " + player.getName());
			}

			if (SAVE_PERIOD > 0) {
				/* Start an autosave task for this player */
				new BukkitRunnable() {
					@Override
					public void run() {
						if (player.isOnline()) {
							save(player);
						} else {
							/* Stop autosaving if the player logs out */
							this.cancel();
						}
					}
				}.runTaskTimer(mPlugin, SAVE_PERIOD, SAVE_PERIOD);
			}
		});
	}

	/* When player leaves, save the data and remove it from the local storage */
	@EventHandler(priority = EventPriority.MONITOR)
	public void playerQuitEvent(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		save(player);
		mAllPlayerData.remove(player.getUniqueId());
	}

	private void save(final Player player) {
		final CustomData playerData = mAllPlayerData.get(player.getUniqueId());
		if (playerData == null) {
			/* No data to save */
			return;
		}

		MonumentaRedisSyncAPI.savePlayerPluginData(player.getUniqueId(), IDENTIFIER, playerData.toJsonString(), mPlugin, (Exception exception) -> {
			if (exception != null) {
				mPlugin.getLogger().severe("Failed to save data for player " + player.getName() + ": " + exception.getMessage());
				exception.printStackTrace();
			} else {
				mPlugin.getLogger().info("Saved data for player " + player.getName());
			}
		});
	}

	/* Returns null if player hasn't finished loading yet or is not logged in!
	 * Your plugin needs to handle this situation. It might be a few seconds before data is available after joining...
	 * This function will not access the database - it will give you the local copy.
	 * This is fast/suitable for use on the main thread.
	 */
	public CustomData getCustomData(final Player player) {
		return mAllPlayerData.get(player.getUniqueId());
	}


	/*
	 * You need something when the server shuts down to save all the current player data.
	 * This works differently than normal save - you need to wait for all the data to save to the database.
	 * Otherwise the server might shut down before the save requests are actually sent, and they won't make it.
	 */
	public void saveAllAndWaitForCompletion() {
		Map<UUID, CompletableFuture<Boolean>> futures = new HashMap<>();

		/* Go through all players, get their save data, and start saving them to the database */
		for (Map.Entry<UUID, CustomData> entries : mAllPlayerData.entrySet()) {
			futures.put(entries.getKey(), MonumentaRedisSyncAPI.savePlayerPluginData(entries.getKey(), IDENTIFIER, entries.getValue().toJsonString()));
		}

		/* Wait on each save and make sure it completed */
		for (Map.Entry<UUID, CompletableFuture<Boolean>> future : futures.entrySet()) {
			try {
				boolean success = future.getValue().get();
				if (!success) {
					mPlugin.getLogger().severe("Failed to save data for player " + future.getKey() + ": redis received and declined write");
				}
			} catch (Exception ex) {
				mPlugin.getLogger().severe("Failed to save data for player " + future.getKey() + ": " + ex.getMessage());
			}
		}
	}
}
