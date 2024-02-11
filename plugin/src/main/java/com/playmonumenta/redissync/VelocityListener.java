package com.playmonumenta.redissync;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.UUID;

public class VelocityListener {
	public static final String uuidToNamePath = "uuid2name";
	public static final String nameToUUIDPath = "name2uuid";

	@Subscribe
	public void postLoginEvent(PostLoginEvent event) {
		Player player = event.getPlayer();

		String name = player.getUsername();
		UUID uuid = player.getUniqueId();

		RedisAsyncCommands<String, String> async = RedisAPI.getInstance().async();
		async.multi();
		async.hset("uuid2name", uuid.toString(), name);
		async.hset("name2uuid", name, uuid.toString());
		async.exec();
	}
}
