package com.playmonumenta.redissync.example.redislockexamples;

import com.playmonumenta.redissync.ConfigAPI;
import com.playmonumenta.redissync.RedisAPI;
import com.playmonumenta.redissync.RedisReentrantLock;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
* Toy example to show basic concurrency control.
*/
public class ToyExampleCommand {
	public static CommandAPICommand command(Plugin plugin) {
		return new CommandAPICommand("toy")
			.executes((sender, args) -> {
				final String KEY = ConfigAPI.getServerDomain() + ":tests:lock:toy";
				RedisReentrantLock lock = new RedisReentrantLock("toyLock");

				new BukkitRunnable() {

					@Override
					public void run() {
						lock.lock();
						try {
							plugin.getLogger().info("Lock acquired");
							plugin.getLogger().info("Reading shard holding lock: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(ConfigAPI.getServerDomain() + ":locks:toyLock")).orElse("None"));
							plugin.getLogger().info("Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
							plugin.getLogger().info("Setting common entry...");
							RedisAPI.getInstance().sync().set(KEY, ConfigAPI.getShardName());
							try {
								Thread.sleep(3000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							plugin.getLogger().info("Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
						} finally {
							plugin.getLogger().info("Releasing lock");
							lock.unlock();
						}
					}

				}.runTaskAsynchronously(plugin);

				new BukkitRunnable() {

					@Override
					public void run() {
						RedisReentrantLock lock = new RedisReentrantLock("toyLock");
						lock.lock();
						try {
							plugin.getLogger().info("Lock acquired");
							plugin.getLogger().info("Reading shard holding lock: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(ConfigAPI.getServerDomain() + ":locks:toyLock")).orElse("None"));
							plugin.getLogger().info("Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
							plugin.getLogger().info("Setting common entry...");
							RedisAPI.getInstance().sync().set(KEY, ConfigAPI.getShardName());
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							plugin.getLogger().info("Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
						} finally {
							plugin.getLogger().info("Releasing lock");
							lock.unlock();
						}
					}

				}.runTaskLaterAsynchronously(plugin, 5);

				new BukkitRunnable() {

					@Override
					public void run() {
						List<String> logs = Collections.synchronizedList(new ArrayList<>());
						lock.lock();
						try {
							plugin.getLogger().info("Lock acquired");
							plugin.getLogger().info("Reading shard holding lock: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(ConfigAPI.getServerDomain() + ":locks:toyLock")).orElse("None"));
							plugin.getLogger().info("Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
							plugin.getLogger().info("Setting common entry...");
							RedisAPI.getInstance().sync().set(KEY, ConfigAPI.getShardName());
							try {
								Thread.sleep(15000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							plugin.getLogger().info("Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
						} finally {
							plugin.getLogger().info("Releasing lock");
							lock.unlock();
						}
					}

				}.runTaskLaterAsynchronously(plugin, 40);
			});
	}
}