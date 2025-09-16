package com.playmonumenta.redissync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkrelay.GatherHeartbeatDataEvent;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import com.playmonumenta.networkrelay.shardhealth.ShardHealth;
import com.playmonumenta.redissync.event.PlayerAccountTransferEvent;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class NetworkRelayIntegration implements Listener {
	private static @Nullable NetworkRelayIntegration INSTANCE = null;
	private static final String LOGIN_EVENT_CHANNEL = "com.playmonumenta.redissync.loginEvent";
	private static final String ACCOUNT_TRANSFER_EVENT_CHANNEL = "com.playmonumenta.redissync.AccountTransferEvent";
	private static final String PLUGIN_IDENTIFIER = "com.playmonumenta.redissync";
	private final Logger mLogger;
	private final String mShardName;

	protected NetworkRelayIntegration(Logger logger) throws Exception {
		INSTANCE = this;
		mLogger = logger;
		mShardName = NetworkRelayAPI.getShardName();
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void playerLoginEvent(PlayerLoginEvent event) {
		if (mShardName == null) {
			return;
		}

		/* NOTE: This runs very early in the login process! */
		Player player = event.getPlayer();

		String nameStr = player.getName();
		String uuidStr = player.getUniqueId().toString();

		Bukkit.getServer().getScheduler().runTaskAsynchronously(MonumentaRedisSync.getInstance(), () -> {
			try {
				JsonObject eventData = new JsonObject();
				eventData.addProperty("shard", mShardName);
				eventData.addProperty("playerName", nameStr);
				eventData.addProperty("playerUuid", uuidStr);
				NetworkRelayAPI.sendBroadcastMessage(LOGIN_EVENT_CHANNEL, eventData);
			} catch (Exception e) {
				mLogger.warning("Failed to broadcast login event for " + nameStr);
			}
		});
	}

	public static void broadcastPlayerAccountTransferEvent(
		long timestampMillis,
		UUID oldId,
		String oldName,
		UUID currentId,
		String currentName
	) {
		NetworkRelayIntegration instance = INSTANCE;
		if (instance == null) {
			return;
		}

		Bukkit.getServer().getScheduler().runTaskAsynchronously(MonumentaRedisSync.getInstance(), () -> {
			try {
				JsonObject eventData = new JsonObject();

				eventData.addProperty("timestamp_millis", timestampMillis);
				eventData.addProperty("old_id", oldId.toString());
				eventData.addProperty("old_name", oldName);
				eventData.addProperty("new_id", currentId.toString());
				eventData.addProperty("new_name", currentName);

				NetworkRelayAPI.sendBroadcastMessage(ACCOUNT_TRANSFER_EVENT_CHANNEL, eventData);
			} catch (Exception e) {
				instance.mLogger.warning("Failed to broadcast account transfer event for " + currentName);
			}
		});
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) throws Exception {
		switch (event.getChannel()) {
			case LOGIN_EVENT_CHANNEL -> {
				JsonObject data = event.getData();
				if (data == null) {
					mLogger.severe("Got " + LOGIN_EVENT_CHANNEL + " channel with null data");
					return;
				}
				remoteLoginEvent(data);
			}
			case ACCOUNT_TRANSFER_EVENT_CHANNEL -> {
				if (event.getSource().equals(mShardName)) {
					// Ignore local events
					return;
				}

				JsonObject data = event.getData();
				if (data == null) {
					mLogger.severe("Got " + ACCOUNT_TRANSFER_EVENT_CHANNEL + " channel with null data");
					return;
				}

				remoteAccountTransferEvent(data);
			}
			default -> {
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
	public void gatherHeartbeatDataEvent(GatherHeartbeatDataEvent event) throws Exception {
		mLogger.finest("Got relay request for heartbeat data");

		JsonObject contentData = new JsonObject();
		for (Map.Entry<String, Double> contentEntry : ContentAPI.localContent().entrySet()) {
			String contentId = contentEntry.getKey();
			Double contentHealth = contentEntry.getValue();
			contentData.addProperty(contentId, contentHealth);
		}

		JsonObject pluginData = new JsonObject();
		pluginData.add("content", contentData);

		event.setPluginData(PLUGIN_IDENTIFIER, pluginData);
	}

	public static String[] getOnlineTransferTargets() {
		NetworkRelayIntegration instance = INSTANCE;
		if (instance != null) {
			try {
				Set<String> shards = NetworkRelayAPI.getOnlineShardNames();

				shards.removeIf(shardName -> (
					NetworkRelayAPI.getHeartbeatPluginData(shardName, PLUGIN_IDENTIFIER) == null
						|| shardName.equals(instance.mShardName)
				));

				return shards.toArray(new String[0]);
			} catch (Exception ex) {
				instance.mLogger.warning("NetworkRelayAPI.getOnlineShardNames failed: " + ex.getMessage());
				ex.printStackTrace();
			}
		}
		return new String[0];
	}

	public static Double getLocalShardHealth() {
		NetworkRelayIntegration instance = INSTANCE;
		if (instance == null) {
			return 0.0;
		}

		ShardHealth shardHealth = ShardHealth.averageHealth();
		return shardHealth.healthScore();
	}

	public static Map<String, Map<String, Double>> getRemoteContent() {
		Map<String, Map<String, Double>> result = new TreeMap<>();

		NetworkRelayIntegration instance = INSTANCE;
		if (instance == null) {
			return result;
		}

		Set<String> shards = NetworkRelayAPI.getOnlineShardNames();

		for (String shardName :  shards) {
			JsonObject shardHeartbeat = NetworkRelayAPI.getHeartbeatPluginData(shardName, PLUGIN_IDENTIFIER);
			if (shardHeartbeat == null) {
				continue;
			}

			if (!(shardHeartbeat.get("content") instanceof JsonObject remoteContentJson)) {
				continue;
			}

			for (Map.Entry<String, JsonElement> contentEntry : remoteContentJson.entrySet()) {
				String contentId = contentEntry.getKey();
				JsonElement contentElement = contentEntry.getValue();

				if (contentElement instanceof JsonPrimitive contentPrimitive && contentPrimitive.isNumber()) {
					double shardHealth = contentPrimitive.getAsDouble();

					result.computeIfAbsent(contentId, k -> new TreeMap<>())
						.put(shardName, shardHealth);
				}
			}
		}

		return result;
	}

	private void remoteLoginEvent(JsonObject data) {
		if (mShardName == null) {
			return;
		}

		String remoteShardName;
		String playerName;
		UUID playerUuid;

		try {
			remoteShardName = data.get("shard").getAsString();
			playerName = data.get("playerName").getAsString();
			playerUuid = UUID.fromString(data.get("playerUuid").getAsString());
		} catch (Exception e) {
			mLogger.severe("Got " + LOGIN_EVENT_CHANNEL + " channel with invalid data");
			return;
		}

		mLogger.fine("Got relay remoteLoginEvent for " + playerName);

		if (mShardName.equals(remoteShardName)) {
			return;
		}

		MonumentaRedisSyncAPI.updateUuidToName(playerUuid, playerName);
		MonumentaRedisSyncAPI.updateNameToUuid(playerName, playerUuid);
	}

	private void remoteAccountTransferEvent(JsonObject data) {
		AccountTransferDetails transferDetails = new  AccountTransferDetails(data);
		MonumentaRedisSync.getInstance().getLogger()
			.info("[AccountTransferManager] Detected remote account transfer for " + transferDetails.oldName() + " (" + transferDetails.oldId() +") -> " + transferDetails.newName() + " (" + transferDetails.newId() + ")");
		AccountTransferManager.registerRemoteTransfer(transferDetails);

		PlayerAccountTransferEvent event = new PlayerAccountTransferEvent(transferDetails);
		Bukkit.getPluginManager().callEvent(event);
	}

	public static @Nullable String getShardName() {
		NetworkRelayIntegration instance = INSTANCE;
		if (instance != null) {
			try {
				return NetworkRelayAPI.getShardName();
			} catch (Exception ex) {
				instance.mLogger.warning("NetworkRelayAPI.getShardName failed: " + ex.getMessage());
				ex.printStackTrace();
			}
		}
		return null;
	}
}
