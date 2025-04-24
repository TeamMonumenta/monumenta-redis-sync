package com.playmonumenta.redissync;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.redissync.event.PlayerAccountTransferEvent;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.Nullable;

public class AccountTransferManager implements Listener {
	protected static final String PLUGIN_KEY = "MonumentaRedisSync";
	protected static final String REDIS_KEY = "account_transfer_log";
	protected static final LocalDateTime EPOCH = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);

	private static @Nullable AccountTransferManager INSTANCE = null;

	private AccountTransferManager() {
	}

	public static AccountTransferManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new AccountTransferManager();
		}
		return INSTANCE;
	}

	public static void onDisable() {
		INSTANCE = null;
	}

	@EventHandler
	public void playerSaveEvent(PlayerSaveEvent event) {
		Player player = event.getPlayer();

		JsonObject redisSyncData = event.getPluginData().computeIfAbsent(PLUGIN_KEY, k -> new JsonObject());
		redisSyncData.addProperty("last_account_uuid", player.getUniqueId().toString());
		redisSyncData.addProperty("last_account_name", player.getName());

		event.setPluginData(PLUGIN_KEY, redisSyncData);
	}

	@EventHandler
	public void playerJoinEvent(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		UUID currentPlayerId = player.getUniqueId();
		JsonObject data = MonumentaRedisSyncAPI.getPlayerPluginData(currentPlayerId, PLUGIN_KEY);

		if (
			data == null
				|| !(data.get("last_account_uuid") instanceof JsonPrimitive lastAccountUuidPrimitive)
				|| !lastAccountUuidPrimitive.isString()
		) {
			return;
		}

		UUID lastAccountId;
		try {
			lastAccountId = UUID.fromString(lastAccountUuidPrimitive.getAsString());
		} catch (Throwable throwable) {
			MonumentaRedisSync.getInstance().getLogger().log(Level.WARNING, "Unable to get previous player account ID for " + player.getName() + "!", throwable);
			return;
		}

		if (currentPlayerId.equals(lastAccountId)) {
			return;
		}

		// Account transfer detected! Log and tell other plugins to fix up their data!

		String currentPlayerName = player.getName();
		String lastAccountName;

		if (
			data.get("last_account_name") instanceof JsonPrimitive lastAccountNamePrimitive
				&& lastAccountNamePrimitive.isString()
		) {
			lastAccountName = lastAccountNamePrimitive.getAsString();
		} else {
			lastAccountName = MonumentaRedisSyncAPI.cachedUuidToName(lastAccountId);
			if (lastAccountName == null) {
				lastAccountName = lastAccountId.toString();
			}
		}

		MonumentaRedisSync plugin = MonumentaRedisSync.getInstance();
		plugin.getLogger().info("Detected account transfer for " + lastAccountName + " (" + lastAccountId +") -> " + currentPlayerName + " (" + currentPlayerId + ")");

		// Alert plugins

		PlayerAccountTransferEvent newEvent = new PlayerAccountTransferEvent(player, lastAccountId, lastAccountName);
		Bukkit.getPluginManager().callEvent(newEvent);

		// Log to Redis

		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		long timestampMillis = EPOCH.until(now, ChronoUnit.MILLIS);

		JsonObject transferDetails = new JsonObject();
		transferDetails.addProperty("timestamp_millis", timestampMillis);
		transferDetails.addProperty("old_id", lastAccountId.toString());
		transferDetails.addProperty("old_name", lastAccountName);
		transferDetails.addProperty("new_id", currentPlayerId.toString());
		transferDetails.addProperty("new_name", currentPlayerName);

		RedisAPI.getInstance().async().zadd(REDIS_KEY, (double) timestampMillis, transferDetails.toString());
	}
}
