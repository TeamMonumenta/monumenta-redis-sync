package com.playmonumenta.redissync.example.redislockexamples;

import com.playmonumenta.redissync.ConfigAPI;
import com.playmonumenta.redissync.RedisAPI;
import com.playmonumenta.redissync.RedisReentrantLock;
import dev.jorel.commandapi.CommandAPICommand;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
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
							logExact(plugin, "Lock acquired");
							logExact(plugin, "Reading shard holding lock: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(ConfigAPI.getServerDomain() + ":locks:toyLock")).orElse("None"));
							logExact(plugin, "Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
							logExact(plugin, "Setting common entry...");
							RedisAPI.getInstance().sync().set(KEY, ConfigAPI.getShardName());
							try {
								Thread.sleep(3000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							logExact(plugin, "Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
						} finally {
							logExact(plugin, "Releasing lock");
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
							logExact(plugin, "Lock acquired");
							logExact(plugin, "Reading shard holding lock: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(ConfigAPI.getServerDomain() + ":locks:toyLock")).orElse("None"));
							logExact(plugin, "Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
							logExact(plugin, "Setting common entry...");
							RedisAPI.getInstance().sync().set(KEY, ConfigAPI.getShardName());
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							logExact(plugin, "Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
						} finally {
							logExact(plugin, "Releasing lock");
							lock.unlock();
						}
					}

				}.runTaskLaterAsynchronously(plugin, 5);

				new BukkitRunnable() {

					@Override
					public void run() {
						lock.lock();
						try {
							logExact(plugin, "Lock acquired");
							logExact(plugin, "Reading shard holding lock: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(ConfigAPI.getServerDomain() + ":locks:toyLock")).orElse("None"));
							logExact(plugin, "Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
							logExact(plugin, "Setting common entry...");
							RedisAPI.getInstance().sync().set(KEY, ConfigAPI.getShardName());
							try {
								Thread.sleep(15000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							logExact(plugin, "Reading common entry: " + Optional.ofNullable(RedisAPI.getInstance().sync().get(KEY)).orElse("None"));
						} finally {
							logExact(plugin, "Releasing lock");
							lock.unlock();
						}
					}

				}.runTaskLaterAsynchronously(plugin, 40);
			});
	}

	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnn");

	private static void logExact(Plugin plugin, String message) {
		plugin.getLogger().info("[" + LocalDateTime.now().format(formatter) + "] <" + ConfigAPI.getShardName() + "> " + message);
	}
}