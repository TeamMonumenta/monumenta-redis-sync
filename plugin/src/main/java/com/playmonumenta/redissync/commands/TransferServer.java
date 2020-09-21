package com.playmonumenta.redissync.commands;

import java.util.Collection;
import java.util.LinkedHashMap;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.FloatArgument;
import dev.jorel.commandapi.arguments.LocationArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument.EntitySelector;

public class TransferServer {
	@SuppressWarnings("unchecked")
	public static void register(Plugin plugin) {
		String command = "transferserver";
		CommandPermission perms = CommandPermission.fromString("monumenta.command.transferserver");
		LinkedHashMap<String, Argument> arguments;

		arguments = new LinkedHashMap<>();
		arguments.put("players", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
		arguments.put("server", new StringArgument());
		new CommandAPICommand(command)
			.withArguments(arguments)
			.withPermission(perms)
			.executes((sender, args) -> {
				for (Player player : (Collection<Player>)args[0]) {
					try {
					 MonumentaRedisSyncAPI.sendPlayer(plugin, player, (String)args[1]);
					} catch (Exception ex) {
					 CommandAPI.fail(ex.getMessage());
					}
				}
			}
		).register();

		arguments.put("location", new LocationArgument());
		new CommandAPICommand(command)
			.withArguments(arguments)
			.withPermission(perms)
			.executes((sender, args) -> {
				for (Player player : (Collection<Player>)args[0]) {
					try {
					  MonumentaRedisSyncAPI.sendPlayer(plugin, player, (String)args[1], (Location)args[2]);
					} catch (Exception ex) {
					  CommandAPI.fail(ex.getMessage());
					}
				}
			}
		).register();

		arguments.put("yaw", new FloatArgument());
		arguments.put("pitch", new FloatArgument());
		new CommandAPICommand(command)
			.withArguments(arguments)
			.withPermission(perms)
			.executes((sender, args) -> {
				for (Player player : (Collection<Player>)args[0]) {
					try {
						MonumentaRedisSyncAPI.sendPlayer(plugin, player, (String)args[1], (Location)args[2], (Float)args[3], (Float)args[4]);
					} catch (Exception ex) {
						CommandAPI.fail(ex.getMessage());
					}
				}
			}
		).register();

		/* Single player alias */
		arguments = new LinkedHashMap<>();
		arguments.put("server", new StringArgument());
		new CommandAPICommand(command)
			.withArguments(arguments)
			.withPermission(perms)
			.withAliases("s")
			.executes((sender, args) -> {
				if (sender instanceof Player) {
					try {
						MonumentaRedisSyncAPI.sendPlayer(plugin, (Player)sender, (String)args[0]);
					} catch (Exception ex) {
						CommandAPI.fail(ex.getMessage());
					}
				} else {
					CommandAPI.fail("This command can only be run by players");
				}
			}
		).register();
	}
}
