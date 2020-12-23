package com.playmonumenta.redissync.commands;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.utils.ScoreboardUtils;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument.EntitySelector;
import dev.jorel.commandapi.arguments.FunctionArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.ObjectiveArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandExecutor;
import dev.jorel.commandapi.wrappers.FunctionWrapper;

public class RboardCommand {
	static final String COMMAND = "rboard";
	static final CommandPermission PERMS = CommandPermission.fromString("monumenta.command.rboard");

	@FunctionalInterface
	public interface RboardAction {
		void run(CommandSender sender, Object[] args, String rboardName, String scoreboardName) throws Exception;
	}

	@SuppressWarnings("unchecked")
	private static void regWrapper(LinkedHashMap<String, Argument> arguments, RboardAction exec) {
		CommandExecutor cmdExec = (sender, args) -> {
			try {
				if (args[0] instanceof Collection<?>) {
					for (Player player : (Collection<Player>)args[0]) {
						exec.run(sender, args, player.getUniqueId().toString(), player.getName());
					}
				} else {
					if (!((String)args[0]).startsWith("$")) {
						CommandAPI.fail("Fakeplayer names must start with a $");
					} else {
						exec.run(sender, args, (String)args[0], (String)args[0]);
					}
				}
			} catch (WrapperCommandSyntaxException ex) {
				throw ex;
			} catch (Exception ex) {
				CommandAPI.fail(ex.getMessage());
			}
		};

		/* Replace the players argument with a simple string for fakeplayers */
		LinkedHashMap<String, Argument> fakePlayerArguments = new LinkedHashMap<>();
		for (Map.Entry<String, Argument> entry : arguments.entrySet()) {
			if (entry.getKey().equals("players")) {
				fakePlayerArguments.put("name", new TextArgument());
			} else {
				fakePlayerArguments.put(entry.getKey(), entry.getValue());
			}
		}

		/* First register a variant with fakeplayers as a string argument, replacing the "players" arg
		 * This ordering is apparently important
		 */
		new CommandAPICommand(COMMAND)
			.withArguments(fakePlayerArguments)
			.withPermission(PERMS)
			.executes(cmdExec)
			.register();

		/* Second one of these registers as-is, with 'players' being a collection of players */
		new CommandAPICommand(COMMAND)
			.withArguments(arguments)
			.withPermission(PERMS)
			.executes(cmdExec)
			.register();
	}

	public static void register(Plugin plugin) {
		LinkedHashMap<String, Argument> arguments;

		arguments = new LinkedHashMap<>();

		/********************* Set *********************/
		RboardAction action = (sender, args, rboardName, scoreboardName) -> {
			Map<String, String> vals = new LinkedHashMap<>();
			for (int i = 1; i < args.length; i += 2) {
				vals.put((String)args[i], Integer.toString((Integer)args[i + 1]));
			}
			MonumentaRedisSyncAPI.rboardSet(rboardName, vals);
		};

		arguments.clear();
		arguments.put("set", new LiteralArgument("set"));
		arguments.put("players", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
		for (int i = 0; i < 5; i++) {
			arguments.put("objective" + i, new ObjectiveArgument());
			arguments.put("value" + i, new IntegerArgument());
			regWrapper(arguments, action);
		}

		/********************* Store *********************/
		action = (sender, args, rboardName, scoreboardName) -> {
			Map<String, String> vals = new LinkedHashMap<>();
			for (int i = 1; i < args.length; i += 1) {
				vals.put((String)args[i], Integer.toString(ScoreboardUtils.getScoreboardValue(scoreboardName, (String)args[i])));
			}
			MonumentaRedisSyncAPI.rboardSet(rboardName, vals);
		};

		arguments.clear();
		arguments.put("store", new LiteralArgument("store"));
		arguments.put("players", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
		for (int i = 0; i < 5; i++) {
			arguments.put("objective" + i, new ObjectiveArgument());
			regWrapper(arguments, action);
		}

		/********************* Add *********************/
		arguments.clear();
		arguments.put("add", new LiteralArgument("add"));
		arguments.put("players", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
		arguments.put("objective", new ObjectiveArgument());
		arguments.put("value", new IntegerArgument());
		regWrapper(arguments, (sender, args, rboardName, scoreboardName) -> {
			MonumentaRedisSyncAPI.rboardAdd(rboardName, (String)args[1], (Integer)args[2]);
		});

		/********************* AddScore *********************/
		arguments.clear();
		arguments.put("addscore", new LiteralArgument("addscore"));
		arguments.put("players", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
		arguments.put("objective", new ObjectiveArgument());
		arguments.put("objectiveToAdd", new ObjectiveArgument());
		regWrapper(arguments, (sender, args, rboardName, scoreboardName) -> {
			MonumentaRedisSyncAPI.rboardAdd(rboardName, (String)args[1], ScoreboardUtils.getScoreboardValue(scoreboardName, (String)args[2]));
		});

		/********************* Reset *********************/
		action = (sender, args, rboardName, scoreboardName) -> {
			String[] vals = new String[args.length - 1];
			for (int i = 1; i < args.length; i += 1) {
				vals[i - 1] = (String)args[i];
			}
			MonumentaRedisSyncAPI.rboardReset(rboardName, vals);
		};

		arguments.clear();
		arguments.put("reset", new LiteralArgument("reset"));
		arguments.put("players", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
		for (int i = 0; i < 5; i++) {
			arguments.put("objective" + i, new ObjectiveArgument());
			regWrapper(arguments, action);
		}

		/********************* ResetAll *********************/
		action = (sender, args, rboardName, scoreboardName) -> {
			MonumentaRedisSyncAPI.rboardResetAll(rboardName);
		};

		arguments.clear();
		arguments.put("resetall", new LiteralArgument("resetall"));
		arguments.put("players", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
		regWrapper(arguments, action);

		/********************* GetAll *********************/
		action = (sender, args, rboardName, scoreboardName) -> {
			MonumentaRedisSyncAPI.runWhenAvailable(plugin,
					MonumentaRedisSyncAPI.rboardGetAll(rboardName),
					(Map<String, String> data, Exception except) -> {
				if (except != null) {
					plugin.getLogger().severe("rboard getall failed:" + except.getMessage());
					except.printStackTrace();
				} else {
					String output = "[";
					boolean first = true;
					for (Map.Entry<String, String> entry : data.entrySet()) {
						if (!first) {
							output += " ";
						}
						output += ChatColor.GOLD + entry.getKey() + ChatColor.WHITE + "=" + ChatColor.GREEN + entry.getValue();
						first = false;
					}
					output += ChatColor.WHITE + "]";
					sender.sendMessage(output);
				}
			});
		};

		arguments.clear();
		arguments.put("getall", new LiteralArgument("getall"));
		arguments.put("players", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
		regWrapper(arguments, action);

		/********************* Get *********************/
		action = (sender, args, rboardName, scoreboardName) -> {
			String[] objs = new String[args.length - 2];
			for (int j = 2; j < args.length; j += 1) {
				objs[j - 2] = (String)args[j];
			}
			MonumentaRedisSyncAPI.runWhenAvailable(plugin,
					MonumentaRedisSyncAPI.rboardGet(rboardName, objs),
					(Map<String, String> data, Exception except) -> {
				if (except != null) {
					plugin.getLogger().severe("rboard get failed:" + except.getMessage());
					except.printStackTrace();
				} else {
					for (Map.Entry<String, String> entry : data.entrySet()) {
						ScoreboardUtils.setScoreboardValue(scoreboardName, entry.getKey(), Integer.parseInt(entry.getValue()));
					}
					for (FunctionWrapper func : (FunctionWrapper[]) args[1]) {
						func.run();
					}
				}
			});
		};

		arguments.clear();
		arguments.put("get", new LiteralArgument("get"));
		arguments.put("players", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
		arguments.put("function", new FunctionArgument());
		for (int i = 0; i < 5; i++) {
			arguments.put("objective" + i, new ObjectiveArgument());
			regWrapper(arguments, action);
		}

		/********************* AddAndGet *********************/
		action = (sender, args, rboardName, scoreboardName) -> {
			MonumentaRedisSyncAPI.runWhenAvailable(plugin,
					MonumentaRedisSyncAPI.rboardAdd(rboardName, (String)args[2], (Integer)args[3]),
					(Long data, Exception except) -> {
				if (except != null) {
					plugin.getLogger().severe("rboard addandget failed:" + except.getMessage());
					except.printStackTrace();
				} else {
					ScoreboardUtils.setScoreboardValue(scoreboardName, (String)args[2], data.intValue());
					for (FunctionWrapper func : (FunctionWrapper[]) args[1]) {
						func.run();
					}
				}
			});
		};

		arguments.clear();
		arguments.put("addandget", new LiteralArgument("addandget"));
		arguments.put("players", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
		arguments.put("function", new FunctionArgument());
		arguments.put("objective", new ObjectiveArgument());
		arguments.put("value", new IntegerArgument());
		regWrapper(arguments, action);

		/********************* GetAndReset *********************/
		action = (sender, args, rboardName, scoreboardName) -> {
			String[] objs = new String[args.length - 2];
			for (int j = 2; j < args.length; j += 1) {
				objs[j - 2] = (String)args[j];
			}
			MonumentaRedisSyncAPI.runWhenAvailable(plugin,
					MonumentaRedisSyncAPI.rboardGetAndReset(rboardName, objs),
					(Map<String, String> data, Exception except) -> {
				if (except != null) {
					plugin.getLogger().severe("rboard getandreset failed:" + except.getMessage());
					except.printStackTrace();
				} else {
					for (Map.Entry<String, String> entry : data.entrySet()) {
						ScoreboardUtils.setScoreboardValue(scoreboardName, entry.getKey(), Integer.parseInt(entry.getValue()));
					}
					for (FunctionWrapper func : (FunctionWrapper[]) args[1]) {
						func.run();
					}
				}
			});
		};

		arguments.clear();
		arguments.put("getandreset", new LiteralArgument("getandreset"));
		arguments.put("players", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
		arguments.put("function", new FunctionArgument());
		for (int i = 0; i < 5; i++) {
			arguments.put("objective" + i, new ObjectiveArgument());
			regWrapper(arguments, action);
		}
	}
}
