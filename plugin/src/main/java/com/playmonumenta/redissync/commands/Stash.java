package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;

public class Stash {
	public static void register() {
		String command = "stash";
		CommandPermission perms = CommandPermission.fromString("monumenta.command.stash");

		/********************* stash put *********************/

		new CommandAPICommand(command)
			.withArguments(new MultiLiteralArgument("put"))
			.withPermission(perms)
			.executes((sender, args) -> {
				if (sender instanceof ProxiedCommandSender) {
					sender = ((ProxiedCommandSender)sender).getCallee();
				}
				if (!(sender instanceof Player)) {
					CommandAPI.fail("This command can only be run by/as players");
				}
				try {
					MonumentaRedisSyncAPI.stashPut((Player)sender, null);
				} catch (Exception ex) {
					CommandAPI.fail(ex.getMessage());
				}
			}
		).register();

		/* Optional argument version */
		new CommandAPICommand(command)
			.withArguments(new MultiLiteralArgument("put"))
			.withArguments(new StringArgument("name"))
			.withPermission(perms)
			.executes((sender, args) -> {
				if (sender instanceof ProxiedCommandSender) {
					sender = ((ProxiedCommandSender)sender).getCallee();
				}
				if (!(sender instanceof Player)) {
					CommandAPI.fail("This command can only be run by/as players");
				}
				try {
					MonumentaRedisSyncAPI.stashPut((Player)sender, (String)args[1]);
				} catch (Exception ex) {
					CommandAPI.fail(ex.getMessage());
				}
			}
		).register();

		/********************* stash get *********************/

		new CommandAPICommand(command)
			.withArguments(new MultiLiteralArgument("get"))
			.withPermission(perms)
			.executes((sender, args) -> {
				if (sender instanceof ProxiedCommandSender) {
					sender = ((ProxiedCommandSender)sender).getCallee();
				}
				if (!(sender instanceof Player)) {
					CommandAPI.fail("This command can only be run by/as players");
				}
				try {
					MonumentaRedisSyncAPI.stashGet((Player)sender, null);
				} catch (Exception ex) {
					CommandAPI.fail(ex.getMessage());
				}
			}
		).register();

		/* Optional argument version */
		new CommandAPICommand(command)
			.withArguments(new MultiLiteralArgument("get"))
			.withArguments(new StringArgument("name"))
			.withPermission(perms)
			.executes((sender, args) -> {
				if (sender instanceof ProxiedCommandSender) {
					sender = ((ProxiedCommandSender)sender).getCallee();
				}
				if (!(sender instanceof Player)) {
					CommandAPI.fail("This command can only be run by/as players");
				}
				try {
					MonumentaRedisSyncAPI.stashGet((Player)sender, (String)args[1]);
				} catch (Exception ex) {
					CommandAPI.fail(ex.getMessage());
				}
			}
		).register();

		/********************* stash info *********************/

		new CommandAPICommand(command)
			.withArguments(new MultiLiteralArgument("info"))
			.withPermission(perms)
			.executes((sender, args) -> {
				if (!(sender instanceof Player)) {
					CommandAPI.fail("This command can only be run by players");
				}
				try {
					MonumentaRedisSyncAPI.stashInfo((Player)sender, null);
				} catch (Exception ex) {
					CommandAPI.fail(ex.getMessage());
				}
			}
		).register();

		/* Optional argument version */
		new CommandAPICommand(command)
			.withArguments(new MultiLiteralArgument("info"))
			.withArguments(new StringArgument("name"))
			.withPermission(perms)
			.executes((sender, args) -> {
				if (!(sender instanceof Player)) {
					CommandAPI.fail("This command can only be run by players");
				}
				try {
					MonumentaRedisSyncAPI.stashInfo((Player)sender, (String)args[1]);
				} catch (Exception ex) {
					CommandAPI.fail(ex.getMessage());
				}
			}
		).register();
	}
}
