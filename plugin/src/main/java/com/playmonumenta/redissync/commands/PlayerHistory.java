package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.RedisAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PlayerHistory {
	public static void register(Plugin plugin) {
		EntitySelectorArgument.OnePlayer playerArg = new EntitySelectorArgument.OnePlayer("player");

		new CommandAPICommand("playerhistory")
			.withArguments(playerArg)
			.withPermission(CommandPermission.fromString("monumenta.command.playerhistory"))
			.executesPlayer((sender, args) -> {
				try {
					playerHistory(plugin, sender, args.getByArgument(playerArg));
				} catch (Exception ex) {
					throw CommandAPI.failWithString(ex.getMessage());
				}
			}
		).register();
	}

	private static void playerHistory(Plugin plugin, CommandSender sender, Player target) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			// ASYNC
			RedisAPI api = RedisAPI.getInstance();
			List<String> history = api.sync().lrange(MonumentaRedisSyncAPI.getRedisHistoryPath(target), 0, -1);

			Bukkit.getScheduler().runTask(plugin, () -> {
				// SYNC
				int idx = 0;
				for (String hist : history) {
					String[] split = hist.split("\\|");
					if (split.length != 3) {
						sender.sendMessage(ChatColor.RED + "Got corrupted history with " + Integer.toString(split.length) + " entries: " + hist);
						continue;
					}

					sender.sendMessage(String.format("%s%d %s%s %s%s ago", ChatColor.AQUA, idx, ChatColor.GOLD, split[0], ChatColor.WHITE, MonumentaRedisSyncAPI.getTimeDifferenceSince(Long.parseLong(split[1]))));
					idx += 1;
				}
			});
		});
	}
}
