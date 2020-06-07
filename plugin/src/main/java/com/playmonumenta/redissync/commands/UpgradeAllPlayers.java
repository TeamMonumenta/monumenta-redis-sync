package com.playmonumenta.redissync.commands;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;

import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI.RedisPlayerData;

import io.github.jorelali.commandapi.api.CommandAPI;
import io.github.jorelali.commandapi.api.CommandPermission;
import io.github.jorelali.commandapi.api.arguments.Argument;

public class UpgradeAllPlayers {
	public static void register(MonumentaRedisSync plugin) {
		String command = "upgradeallplayers";
		CommandPermission perms = CommandPermission.fromString("monumenta.command.upgradeallplayers");
		LinkedHashMap<String, Argument> arguments;

		arguments = new LinkedHashMap<>();
		CommandAPI.getInstance().register(command,
		                                  perms,
		                                  arguments,
		                                  (sender, args) -> {
											  try {
												  run(plugin);
											  } catch (Exception ex) {
												  CommandAPI.fail(ex.getMessage());
											  }
		                                  }
		);
	}

	private static void updatePlayer(MonumentaRedisSync mrs, UUID uuid) {
		Bukkit.broadcastMessage(ChatColor.GOLD +  "Upgrading: " + uuid.toString());
		try {
			RedisPlayerData data = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();

			if (data == null) {
				Bukkit.broadcastMessage(ChatColor.GOLD +  "Failed to fetch player data: " + uuid.toString());
				return;
			}

			Object newData = mrs.getVersionAdapter().upgradePlayerData(data.getNbtTagCompoundData());
			if (newData == null) {
				Bukkit.broadcastMessage(ChatColor.GOLD +  "Failed to upgrade player data: " + uuid.toString());
				return;
			}
			data.setNbtTagCompoundData(newData);

			String newAdvancements = mrs.getVersionAdapter().upgradePlayerAdvancements(data.getAdvancements());
			if (newAdvancements == null) {
				Bukkit.broadcastMessage(ChatColor.GOLD +  "Failed to upgrade player advancements: " + uuid.toString());
				return;
			}
			data.setAdvancements(newAdvancements);

			data.setHistory("VERSION_UPGRADE|" + Long.toString(System.currentTimeMillis()) + "|" + uuid.toString());

			/* Save and then wait for save to complete and check results */
			if (!MonumentaRedisSyncAPI.saveOfflinePlayerData(data).get()) {
				Bukkit.broadcastMessage(ChatColor.GOLD +  "Failed to save upgraded player: " + uuid.toString());
			}
		} catch (Exception ex) {
			Bukkit.broadcastMessage(ChatColor.GOLD +  "Failed to upgrade player: " + uuid.toString() + " : " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	private static void run(MonumentaRedisSync mrs) {
		Bukkit.broadcastMessage(ChatColor.GOLD +  "WARNING: Player data upgrade has started for offline players");
		Bukkit.broadcastMessage(ChatColor.GOLD +  "The server will lag significantly until this is complete");

		try {
			Set<UUID> players = MonumentaRedisSyncAPI.getAllPlayerUUIDs().get();
			Iterator<UUID> iter = players.iterator();

			new BukkitRunnable() {
				@Override
				public void run() {
					long startTime = System.currentTimeMillis();

					Bukkit.broadcastMessage(ChatColor.GOLD +  "  Players left to process: " + Integer.toString(players.size()));

					/* Only block here for up to 1 second at a time */
					while (System.currentTimeMillis() < startTime + 1000) {
						if (!iter.hasNext()) {
							Bukkit.broadcastMessage(ChatColor.GOLD +  "Upgrade complete");
							this.cancel();
							return;
						}

						UUID uuid = iter.next();
						iter.remove();

						updatePlayer(mrs, uuid);
					}
				}
			}.runTaskTimer(mrs, 0, 1);
		} catch (Exception ex) {
			Bukkit.broadcastMessage(ChatColor.GOLD +  "Upgrade failed: " + ex.getMessage());
			ex.printStackTrace();
		}
	}
}
