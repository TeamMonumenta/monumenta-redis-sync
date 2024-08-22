package com.playmonumenta.redissync.example.redislockexamples;

import com.playmonumenta.redissync.ConfigAPI;
import com.playmonumenta.redissync.RedisAPI;
import com.playmonumenta.redissync.RedisReentrantLock;
import dev.jorel.commandapi.CommandAPICommand;
import org.bukkit.plugin.Plugin;

public class GlobalCounterCommand {
	public static CommandAPICommand command(Plugin plugin) {
		return new CommandAPICommand("globalCounter")
			.withSubcommand(
				new CommandAPICommand("increment")
					.executes((sender, args) -> {
						final String KEY = ConfigAPI.getServerDomain() + ":tests:globalCounter";
						RedisReentrantLock lock = new RedisReentrantLock("globalCounter");
						lock.lock();
						try {
							String result = RedisAPI.getInstance().sync().get(KEY);
							int counterValue = result == null ? 0 : Integer.parseInt(result);
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							RedisAPI.getInstance().sync().set(KEY, Integer.toString(counterValue + 1));
						} finally {
							lock.unlock();
						}
					})
			)
			.withSubcommand(
				new CommandAPICommand("decrement")
					.executes((sender, args) -> {
						final String KEY = ConfigAPI.getServerDomain() + ":tests:globalCounter";
						RedisReentrantLock lock = new RedisReentrantLock("globalCounter");
						lock.lock();
						try {
							String result = RedisAPI.getInstance().sync().get(KEY);
							int counterValue = result == null ? 0 : Integer.parseInt(result);
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							RedisAPI.getInstance().sync().set(KEY, Integer.toString(counterValue - 1));
						} finally {
							lock.unlock();
						}
					})
			)
			.withSubcommand(
				new CommandAPICommand("get")
					.executes((sender, args) -> {
						final String KEY = ConfigAPI.getServerDomain() + ":tests:globalCounter";
						RedisReentrantLock lock = new RedisReentrantLock("globalCounter");
						lock.lock();
						try {
							String result = RedisAPI.getInstance().sync().get(KEY);
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							sender.sendMessage(result == null ? "0" : result);
						} finally {
							lock.unlock();
						}
					})
			);
	}
}
