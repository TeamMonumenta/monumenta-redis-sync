package com.playmonumenta.redissync.commands;

import java.util.LinkedHashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.RedisAPI;

import io.github.jorelali.commandapi.api.CommandAPI;
import io.github.jorelali.commandapi.api.CommandPermission;
import io.github.jorelali.commandapi.api.arguments.Argument;
import io.github.jorelali.commandapi.api.arguments.EntitySelectorArgument;
import io.github.jorelali.commandapi.api.arguments.EntitySelectorArgument.EntitySelector;

public class PlayerHistory {
	public static void register(Plugin plugin) {
		String command = "playerhistory";
		CommandPermission perms = CommandPermission.fromString("monumenta.command.playerhistory");
		LinkedHashMap<String, Argument> arguments;

		arguments = new LinkedHashMap<>();
		arguments.put("player", new EntitySelectorArgument(EntitySelector.ONE_PLAYER));
		CommandAPI.getInstance().register(command,
		                                  perms,
		                                  arguments,
		                                  (sender, args) -> {
											  if (!(sender instanceof Player)) {
												  CommandAPI.fail("This command can only be run by players");
											  }
											  try {
												  playerHistory(plugin, sender, (Player)args[0]);
											  } catch (Exception ex) {
												  CommandAPI.fail(ex.getMessage());
											  }
		                                  }
		);
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
