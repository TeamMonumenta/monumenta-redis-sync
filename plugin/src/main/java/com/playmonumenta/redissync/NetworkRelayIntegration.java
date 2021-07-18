package com.playmonumenta.redissync;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.GatherHeartbeatDataEvent;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class NetworkRelayIntegration implements Listener {
	private static NetworkRelayIntegration INSTANCE = null;
	private static final String LOGIN_EVENT_CHANNEL = "com.playmonumenta.redissync.loginEvent";
	private static final String PLUGIN_IDENTIFIER = "com.playmonumenta.redissync";
	private final Logger mLogger;
	private final String mShardName;

	protected NetworkRelayIntegration(Logger logger) throws Exception {
		INSTANCE = this;
		mLogger = logger;
		mShardName = NetworkRelayAPI.getShardName();
	}

	@EventHandler(priority = EventPriority.LOWEST)
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

	@EventHandler(priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) throws Exception {
		switch (event.getChannel()) {
		case LOGIN_EVENT_CHANNEL:
			JsonObject data = event.getData();
			if (data == null) {
				mLogger.severe("Got " + LOGIN_EVENT_CHANNEL + " channel with null data");
				return;
			}
			remoteLoginEvent(data);
			break;
		default:
			break;
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void gatherHeartbeatDataEvent(GatherHeartbeatDataEvent event) throws Exception {
		mLogger.finer("Got relay request for heartbeat data");
		/* Don't actually need to set any data - just being present is sufficient */
		event.setPluginData(PLUGIN_IDENTIFIER, new JsonObject());
	}

	public static String[] getOnlineTransferTargets() {
		if (INSTANCE != null) {
			try {
				Set<String> shards = NetworkRelayAPI.getOnlineShardNames();

				Iterator<String> iter = shards.iterator();
				while (iter.hasNext()) {
					String shardName = iter.next();
					if (NetworkRelayAPI.getHeartbeatPluginData(shardName, PLUGIN_IDENTIFIER) == null ||
						shardName.equals(INSTANCE.mShardName)) {
						iter.remove();
					}
				}
				return shards.toArray(new String[shards.size()]);
			} catch (Exception ex) {
				INSTANCE.mLogger.warning("NetworkRelayAPI.getOnlineShardNames failed: " + ex.getMessage());
				ex.printStackTrace();
			}
		}
		return new String[0];
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
}
