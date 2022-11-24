package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI.RedisPlayerData;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitRunnable;

public class UpgradeAllPlayers {
	public static void register(MonumentaRedisSync plugin) {
		new CommandAPICommand("monumenta")
			.withSubcommand(new CommandAPICommand("redissync")
				.withSubcommand(new CommandAPICommand("upgradeallplayers")
					.executesPlayer((player, args) -> {
						player.sendMessage("This command is only available from the console");
					})
					.executesConsole((console, args) -> {
						try {
							run(plugin);
						} catch (Exception ex) {
							CommandAPI.fail(ex.getMessage());
						}
					})
			)).register();
	}

	private static void updatePlayer(MonumentaRedisSync mrs, UUID uuid) {
		Bukkit.broadcast(Component.text("Upgrading: " + uuid.toString()), Server.BROADCAST_CHANNEL_USERS);
		try {
			RedisPlayerData data = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();

			if (data == null) {
				return;
			}

			Object newData = mrs.getVersionAdapter().upgradePlayerData(data.getNbtTagCompoundData());
			if (newData == null) {
				Bukkit.broadcast(Component.text("Failed to upgrade player data: " + uuid.toString()).color(NamedTextColor.RED), Server.BROADCAST_CHANNEL_USERS);
				return;
			}
			data.setNbtTagCompoundData(newData);

			String newAdvancements = mrs.getVersionAdapter().upgradePlayerAdvancements(data.getAdvancements());
			if (newAdvancements == null) {
				Bukkit.broadcast(Component.text("Failed to upgrade player advancements: " + uuid.toString()).color(NamedTextColor.RED), Server.BROADCAST_CHANNEL_USERS);
				return;
			}
			data.setAdvancements(newAdvancements);

			data.setHistory("VERSION_UPGRADE|" + Long.toString(System.currentTimeMillis()) + "|" + uuid.toString());

			/* Save and then wait for save to complete and check results */
			if (!MonumentaRedisSyncAPI.saveOfflinePlayerData(data).get()) {
				Bukkit.broadcast(Component.text("Failed to save upgraded player: " + uuid.toString()).color(NamedTextColor.RED), Server.BROADCAST_CHANNEL_USERS);
			}
		} catch (Exception ex) {
			Bukkit.broadcast(Component.text("Failed to upgrade player: " + uuid.toString() + " : " + ex.getMessage()).color(NamedTextColor.RED), Server.BROADCAST_CHANNEL_USERS);
			ex.printStackTrace();
		}
	}

	private static void run(MonumentaRedisSync mrs) {
		Bukkit.broadcast(Component.text("WARNING: Player data upgrade has started for offline players"), Server.BROADCAST_CHANNEL_USERS);
		Bukkit.broadcast(Component.text("The server will lag significantly until this is complete"), Server.BROADCAST_CHANNEL_USERS);

		try {
			Set<UUID> players = MonumentaRedisSyncAPI.getAllPlayerUUIDs().get();
			Iterator<UUID> iter = players.iterator();

			new BukkitRunnable() {
				@Override
				public void run() {
					long startTime = System.currentTimeMillis();

					Bukkit.broadcast(Component.text("  Players left to process: " + Integer.toString(players.size())), Server.BROADCAST_CHANNEL_USERS);

					/* Only block here for up to 1 second at a time */
					while (System.currentTimeMillis() < startTime + 1000) {
						if (!iter.hasNext()) {
							Bukkit.broadcast(Component.text("Upgrade complete"), Server.BROADCAST_CHANNEL_USERS);
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
			Bukkit.broadcast(Component.text("Upgrade failed: " + ex.getMessage()).color(NamedTextColor.RED), Server.BROADCAST_CHANNEL_USERS);
			ex.printStackTrace();
		}
	}
}
