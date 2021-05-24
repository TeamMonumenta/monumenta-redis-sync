package com.playmonumenta.redissync;

import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import com.playmonumenta.redissync.MonumentaRedisSync.CustomLogger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class NetworkRelayListener implements Listener {
	private static final String LOGIN_EVENT_CHANNEL = "com.playmonumenta.redissync.DataEventListener.loginEvent";
	private static CustomLogger mLogger;
	private static String mShardName;

	protected NetworkRelayListener(CustomLogger logger) {
		mLogger = logger;
		try {
			mShardName = NetworkRelayAPI.getShardName();
		} catch (Exception e) {
			mLogger.severe("Could not determine shard name");
			mShardName = null;
		}
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
				eventData.addProperty("name", nameStr);
				eventData.addProperty("uuid", uuidStr);
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

		if (mShardName.equals(remoteShardName)) {
			return;
		}

		MonumentaRedisSyncAPI.updatePlayerName(playerUuid, playerName);
	}
}
