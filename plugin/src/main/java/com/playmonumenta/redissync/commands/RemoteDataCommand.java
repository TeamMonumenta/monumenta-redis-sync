package com.playmonumenta.redissync.commands;

import java.util.Map;
import java.util.UUID;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

import org.bukkit.plugin.Plugin;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.TextArgument;

public class RemoteDataCommand {
	public static void register(Plugin plugin) {
		new CommandAPICommand("remotedata")
			.withPermission(CommandPermission.fromString("monumenta.command.remotedata"))
			.withSubcommand(new CommandAPICommand("getall")
				.withArguments(new TextArgument("player").replaceSuggestions((info) -> MonumentaRedisSyncAPI.getAllCachedPlayerNames().toArray(String[]::new)))
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = (String)args[0];
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(playerNameOrUUID);
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							CommandAPI.fail("Argument must be a player name with correct capitalization or a UUID");
							return; // Needed for proper nullaway exit detection
						}
					}

					String name = MonumentaRedisSyncAPI.cachedUuidToName(uuid);
					UUID finalUUID = uuid;
					if (name == null) {
						CommandAPI.fail("Got uuid '" + uuid + "' that matches no known player");
					}

					MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin, MonumentaRedisSyncAPI.remoteDataGetAll(finalUUID), (data, ex) -> {
						if (ex != null) {
							sender.sendMessage("remoteDataGetAll exception: " + ex.getMessage());
						} else {
							if (data.isEmpty()) {
								sender.sendMessage("Player '" + name + "' has no remote data");
							} else {
								sender.sendMessage("Remote data for player '" + name + "':");
								for (Map.Entry<String, String> entry : data.entrySet()) {
									sender.sendMessage("  '" + entry.getKey() + "':'" + entry.getValue() + "'");
								}
							}
						}
					});
				}))
			.withSubcommand(new CommandAPICommand("get")
				.withArguments(new TextArgument("player").replaceSuggestions((info) -> MonumentaRedisSyncAPI.getAllCachedPlayerNames().toArray(String[]::new)))
				.withArguments(new TextArgument("key"))
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = (String)args[0];
					String key = (String)args[1];
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(playerNameOrUUID);
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							CommandAPI.fail("Argument must be a player name with correct capitalization or a UUID");
							return; // Needed for proper nullaway exit detection
						}
					}

					String name = MonumentaRedisSyncAPI.cachedUuidToName(uuid);
					UUID finalUUID = uuid;
					if (name == null) {
						CommandAPI.fail("Got uuid '" + uuid + "' that matches no known player");
					}

					MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin, MonumentaRedisSyncAPI.remoteDataGet(finalUUID, key), (data, ex) -> {
						if (ex != null) {
							sender.sendMessage("remoteDataGet exception: " + ex.getMessage());
						} else {
							if (data == null) {
								sender.sendMessage("Key '" + key + "' not set for player '" + name + "'");
							} else {
								sender.sendMessage("Remote data for player '" + name + "'  '" + key + "':'" + data + "'");
							}
						}
					});
				}))
			.withSubcommand(new CommandAPICommand("set")
				.withArguments(new TextArgument("player").replaceSuggestions((info) -> MonumentaRedisSyncAPI.getAllCachedPlayerNames().toArray(String[]::new)))
				.withArguments(new TextArgument("key"))
				.withArguments(new TextArgument("value"))
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = (String)args[0];
					String key = (String)args[1];
					String value = (String)args[2];
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(playerNameOrUUID);
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							CommandAPI.fail("Argument must be a player name with correct capitalization or a UUID");
							return; // Needed for proper nullaway exit detection
						}
					}

					String name = MonumentaRedisSyncAPI.cachedUuidToName(uuid);
					UUID finalUUID = uuid;
					if (name == null) {
						CommandAPI.fail("Got uuid '" + uuid + "' that matches no known player");
					}

					MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin, MonumentaRedisSyncAPI.remoteDataSet(finalUUID, key, value), (data, ex) -> {
						if (ex != null) {
							sender.sendMessage("remoteDataSet exception: " + ex.getMessage());
						} else {
							sender.sendMessage("Set remote data for player '" + name + "':");
							sender.sendMessage("  '" + key + "':'" + data + "'");
						}
					});
				}))
			.withSubcommand(new CommandAPICommand("del")
				.withArguments(new TextArgument("player").replaceSuggestions((info) -> MonumentaRedisSyncAPI.getAllCachedPlayerNames().toArray(String[]::new)))
				.withArguments(new TextArgument("key"))
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = (String)args[0];
					String key = (String)args[1];
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(playerNameOrUUID);
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							CommandAPI.fail("Argument must be a player name with correct capitalization or a UUID");
							return; // Needed for proper nullaway exit detection
						}
					}

					String name = MonumentaRedisSyncAPI.cachedUuidToName(uuid);
					UUID finalUUID = uuid;
					if (name == null) {
						CommandAPI.fail("Got uuid '" + uuid + "' that matches no known player");
					}

					MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin, MonumentaRedisSyncAPI.remoteDataDel(finalUUID, key), (data, ex) -> {
						if (ex != null) {
							sender.sendMessage("remoteDataDel exception: " + ex.getMessage());
						} else {
							if (data == null || !data) {
								sender.sendMessage("Key '" + key + "' not set for player '" + name + "'");
							} else {
								sender.sendMessage("Key '" + key + "' deleted for player '" + name + "'");
							}
						}
					});
				}))
		.register();
	}
}
