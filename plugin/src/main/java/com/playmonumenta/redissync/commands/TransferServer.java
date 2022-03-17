package com.playmonumenta.redissync.commands;

import java.util.Collection;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.NetworkRelayIntegration;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument.EntitySelector;
import dev.jorel.commandapi.arguments.FloatArgument;
import dev.jorel.commandapi.arguments.LocationArgument;
import dev.jorel.commandapi.arguments.StringArgument;

public class TransferServer {
	@SuppressWarnings("unchecked")
	public static void register() {
		String command = "transferserver";
		CommandPermission perms = CommandPermission.fromString("monumenta.command.transferserver");

		new CommandAPICommand(command)
			.withArguments(new EntitySelectorArgument("players", EntitySelector.MANY_PLAYERS))
			.withArguments(new StringArgument("server").replaceSuggestions((sender) -> NetworkRelayIntegration.getOnlineTransferTargets()))
			.withPermission(perms)
			.executes((sender, args) -> {
				for (Player player : (Collection<Player>)args[0]) {
					try {
						MonumentaRedisSyncAPI.sendPlayer(player, (String)args[1]);
					} catch (Exception ex) {
						CommandAPI.fail(ex.getMessage());
					}
				}
			}
		).register();

		new CommandAPICommand(command)
			.withArguments(new EntitySelectorArgument("players", EntitySelector.MANY_PLAYERS))
			.withArguments(new StringArgument("server").replaceSuggestions((sender) -> NetworkRelayIntegration.getOnlineTransferTargets()))
			.withArguments(new LocationArgument("location"))
			.withPermission(perms)
			.executes((sender, args) -> {
				for (Player player : (Collection<Player>)args[0]) {
					try {
						MonumentaRedisSyncAPI.sendPlayer(player, (String)args[1], (Location)args[2]);
					} catch (Exception ex) {
						CommandAPI.fail(ex.getMessage());
					}
				}
			}
		).register();

		new CommandAPICommand(command)
			.withArguments(new EntitySelectorArgument("players", EntitySelector.MANY_PLAYERS))
			.withArguments(new StringArgument("server").replaceSuggestions((sender) -> NetworkRelayIntegration.getOnlineTransferTargets()))
			.withArguments(new LocationArgument("location"))
			.withArguments(new FloatArgument("yaw"))
			.withArguments(new FloatArgument("pitch"))
			.withPermission(perms)
			.executes((sender, args) -> {
				for (Player player : (Collection<Player>)args[0]) {
					try {
						MonumentaRedisSyncAPI.sendPlayer(player, (String)args[1], (Location)args[2], (Float)args[3], (Float)args[4]);
					} catch (Exception ex) {
						CommandAPI.fail(ex.getMessage());
					}
				}
			}
		).register();

		/* Single player alias */
		new CommandAPICommand(command)
			.withArguments(new StringArgument("server").replaceSuggestions((sender) -> NetworkRelayIntegration.getOnlineTransferTargets()))
			.withPermission(perms)
			.withAliases("s")
			.executes((sender, args) -> {
				if (sender instanceof Player) {
					try {
						MonumentaRedisSyncAPI.sendPlayer((Player)sender, (String)args[0]);
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
