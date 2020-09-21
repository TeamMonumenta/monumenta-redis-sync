package com.playmonumenta.redissync.commands;

import java.util.LinkedHashMap;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument.EntitySelector;

public class PlayerLoadFromPlayer {
	public static void register() {
		String command = "playerloadfromplayer";
		CommandPermission perms = CommandPermission.fromString("monumenta.command.playerloadfromplayer");
		LinkedHashMap<String, Argument> arguments;

		arguments = new LinkedHashMap<>();
		arguments.put("player", new EntitySelectorArgument(EntitySelector.ONE_PLAYER));
		arguments.put("index", new IntegerArgument(0));
		new CommandAPICommand(command)
			.withArguments(arguments)
			.withPermission(perms)
			.executes((sender, args) -> {
				if (!(sender instanceof Player)) {
					CommandAPI.fail("This command can only be run by players");
				}
				try {
					MonumentaRedisSyncAPI.playerLoadFromPlayer((Player)sender, (Player)args[0], (Integer)args[1]);
				} catch (Exception ex) {
					CommandAPI.fail(ex.getMessage());
				}
			}
		).register();
	}
}
