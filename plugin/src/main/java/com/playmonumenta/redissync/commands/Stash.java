package com.playmonumenta.redissync.commands;

import java.util.LinkedHashMap;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;

public class Stash {
	public static void register() {
		String command = "stash";
		CommandPermission perms = CommandPermission.fromString("monumenta.command.stash");
		LinkedHashMap<String, Argument> arguments;

		/********************* stash put *********************/

		arguments = new LinkedHashMap<>();
		arguments.put("put", new LiteralArgument("put"));
		new CommandAPICommand(command)
			.withArguments(arguments)
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
		arguments.put("name", new StringArgument());
		new CommandAPICommand(command)
			.withArguments(arguments)
			.withPermission(perms)
			.executes((sender, args) -> {
				if (sender instanceof ProxiedCommandSender) {
					sender = ((ProxiedCommandSender)sender).getCallee();
				}
				if (!(sender instanceof Player)) {
					CommandAPI.fail("This command can only be run by/as players");
				}
				try {
					MonumentaRedisSyncAPI.stashPut((Player)sender, (String)args[0]);
				} catch (Exception ex) {
					CommandAPI.fail(ex.getMessage());
				}
			}
		).register();

		/********************* stash get *********************/

		arguments = new LinkedHashMap<>();
		arguments.put("get", new LiteralArgument("get"));
		new CommandAPICommand(command)
			.withArguments(arguments)
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
		arguments.put("name", new StringArgument());
		new CommandAPICommand(command)
			.withArguments(arguments)
			.withPermission(perms)
			.executes((sender, args) -> {
				if (sender instanceof ProxiedCommandSender) {
					sender = ((ProxiedCommandSender)sender).getCallee();
				}
				if (!(sender instanceof Player)) {
					CommandAPI.fail("This command can only be run by/as players");
				}
				try {
					MonumentaRedisSyncAPI.stashGet((Player)sender, (String)args[0]);
				} catch (Exception ex) {
					CommandAPI.fail(ex.getMessage());
				}
			}
		).register();

		/********************* stash info *********************/

		arguments = new LinkedHashMap<>();
		arguments.put("info", new LiteralArgument("info"));
		new CommandAPICommand(command)
			.withArguments(arguments)
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
		arguments.put("name", new StringArgument());
		new CommandAPICommand(command)
			.withArguments(arguments)
			.withPermission(perms)
			.executes((sender, args) -> {
				if (!(sender instanceof Player)) {
					CommandAPI.fail("This command can only be run by players");
				}
				try {
					MonumentaRedisSyncAPI.stashInfo((Player)sender, (String)args[0]);
				} catch (Exception ex) {
					CommandAPI.fail(ex.getMessage());
				}
			}
		).register();
	}
}
