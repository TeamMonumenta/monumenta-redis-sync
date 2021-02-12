package com.playmonumenta.redissync.commands;

import java.util.logging.Level;

import com.playmonumenta.redissync.MonumentaRedisSync;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;

public class ChangeLogLevel {
	public static void register(MonumentaRedisSync plugin) {
		String command = "mrsChangeLogLevel";
		CommandPermission perms = CommandPermission.fromString("mrs.command.changeloglevel");

		new CommandAPICommand(command)
			.withArguments(new MultiLiteralArgument("INFO"))
			.withPermission(perms)
			.executes((sender, args) -> {
				plugin.setLogLevel(Level.INFO);
			}
		).register();

		new CommandAPICommand(command)
			.withArguments(new MultiLiteralArgument("FINE"))
			.withPermission(perms)
			.executes((sender, args) -> {
				plugin.setLogLevel(Level.FINE);
			}
		).register();

		new CommandAPICommand(command)
			.withArguments(new MultiLiteralArgument("FINER"))
			.withPermission(perms)
			.executes((sender, args) -> {
				plugin.setLogLevel(Level.FINER);
			}
		).register();

		new CommandAPICommand(command)
			.withArguments(new MultiLiteralArgument("FINEST"))
			.withPermission(perms)
			.executes((sender, args) -> {
				plugin.setLogLevel(Level.FINEST);
			}
		).register();
	}
}
