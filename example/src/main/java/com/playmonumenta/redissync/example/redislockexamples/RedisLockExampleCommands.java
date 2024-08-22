package com.playmonumenta.redissync.example.redislockexamples;

import dev.jorel.commandapi.CommandAPICommand;
import org.bukkit.plugin.Plugin;

public class RedisLockExampleCommands {
	public static void register(Plugin plugin) {
		new CommandAPICommand("redislockexamples")
			.withPermission("monumenta.redislockexamples")
			.withSubcommand(ToyExampleCommand.command(plugin))
			.withSubcommand(ExceptionsToyExampleCommand.command(plugin))
			.register();
	}
}
