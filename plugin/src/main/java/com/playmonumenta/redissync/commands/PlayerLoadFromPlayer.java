package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument.EntitySelector;
import dev.jorel.commandapi.arguments.IntegerArgument;

public class PlayerLoadFromPlayer {
	public static void register() {
		new CommandAPICommand("playerloadfromplayer")
			.withPermission(CommandPermission.fromString("monumenta.command.playerloadfromplayer"))
			.withArguments(new EntitySelectorArgument("player", EntitySelector.ONE_PLAYER))
			.withArguments(new IntegerArgument("index", 0))
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
