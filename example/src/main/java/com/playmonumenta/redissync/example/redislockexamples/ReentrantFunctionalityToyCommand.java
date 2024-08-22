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

public class ReentrantFunctionalityToyCommand {
	public static CommandAPICommand command(Plugin plugin) {
		return new CommandAPICommand("reentrant")
		.executes((sender, args) -> {
			final String KEY = ConfigAPI.getServerDomain() + ":tests:lock:reentrant";
			RedisReentrantLock lock = new RedisReentrantLock("reentrantLock");

			new BukkitRunnable() {

				@Override
				public void run() {
					lock.lock();
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
						logExact(plugin, "-1");
						lock.unlock();
						logExact(plugin, "+1");
						lock.lock();
						logExact(plugin, "Pausing...");
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						logExact(plugin, "-1");
						lock.unlock();
					} finally {
						logExact(plugin, "Releasing lock");
						lock.unlock();
					}
				}

			}.runTaskAsynchronously(plugin);

			new BukkitRunnable() {

				@Override
				public void run() {
					RedisReentrantLock lock = new RedisReentrantLock("reentrantLock");
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
		});
	}

	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnn");

	private static void logExact(Plugin plugin, String message) {
		plugin.getLogger().info("[" + LocalDateTime.now().format(formatter) + "] <" + ConfigAPI.getShardName() + "> " + message);
	}
}
