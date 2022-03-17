package com.playmonumenta.redissync.commands;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI.RedisPlayerData;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class UpgradeAllPlayers {
	public static void register(MonumentaRedisSync plugin) {
		new CommandAPICommand("monumenta")
			.withSubcommand(new CommandAPICommand("redissync")
				.withSubcommand(new CommandAPICommand("upgradeallplayers"))
					.withPermission(CommandPermission.fromString("monumenta.redissync.upgradeallplayers"))
					.executes((sender, args) -> {
						try {
							run(plugin);
						} catch (Exception ex) {
							CommandAPI.fail(ex.getMessage());
						}
			})).register();
	}

	private static void updatePlayer(MonumentaRedisSync mrs, UUID uuid) {
		Bukkit.broadcast(Component.text("Upgrading: " + uuid.toString()));
		try {
			RedisPlayerData data = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();

			if (data == null) {
				Bukkit.broadcast(Component.text("Failed to fetch player data: " + uuid.toString()).color(NamedTextColor.RED));
				return;
			}

			Object newData = mrs.getVersionAdapter().upgradePlayerData(data.getNbtTagCompoundData());
			if (newData == null) {
				Bukkit.broadcast(Component.text("Failed to upgrade player data: " + uuid.toString()).color(NamedTextColor.RED));
				return;
			}
			data.setNbtTagCompoundData(newData);

			String newAdvancements = mrs.getVersionAdapter().upgradePlayerAdvancements(data.getAdvancements());
			if (newAdvancements == null) {
				Bukkit.broadcast(Component.text("Failed to upgrade player advancements: " + uuid.toString()).color(NamedTextColor.RED));
				return;
			}
			data.setAdvancements(newAdvancements);

			data.setHistory("VERSION_UPGRADE|" + Long.toString(System.currentTimeMillis()) + "|" + uuid.toString());

			/* Save and then wait for save to complete and check results */
			if (!MonumentaRedisSyncAPI.saveOfflinePlayerData(data).get()) {
				Bukkit.broadcast(Component.text("Failed to save upgraded player: " + uuid.toString()).color(NamedTextColor.RED));
			}
		} catch (Exception ex) {
			Bukkit.broadcast(Component.text("Failed to upgrade player: " + uuid.toString() + " : " + ex.getMessage()).color(NamedTextColor.RED));
			ex.printStackTrace();
		}
	}

	private static void run(MonumentaRedisSync mrs) {
		Bukkit.broadcast(Component.text("WARNING: Player data upgrade has started for offline players"));
		Bukkit.broadcast(Component.text("The server will lag significantly until this is complete"));

		try {
			Set<UUID> players = MonumentaRedisSyncAPI.getAllPlayerUUIDs().get();
			Iterator<UUID> iter = players.iterator();

			new BukkitRunnable() {
				@Override
				public void run() {
					long startTime = System.currentTimeMillis();

					Bukkit.broadcast(Component.text("  Players left to process: " + Integer.toString(players.size())));

					/* Only block here for up to 1 second at a time */
					while (System.currentTimeMillis() < startTime + 1000) {
						if (!iter.hasNext()) {
							Bukkit.broadcast(Component.text("Upgrade complete"));
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
			Bukkit.broadcast(Component.text("Upgrade failed: " + ex.getMessage()).color(NamedTextColor.RED));
			ex.printStackTrace();
		}
	}
}
