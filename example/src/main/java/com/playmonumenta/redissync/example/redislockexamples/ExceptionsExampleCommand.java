package com.playmonumenta.redissync.example.redislockexamples;

import com.playmonumenta.redissync.RedisReentrantLock;
import com.playmonumenta.redissync.RedisReentrantLock.RedisLockException;
import dev.jorel.commandapi.CommandAPICommand;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class ExceptionsExampleCommand {
	public static CommandAPICommand command(Plugin plugin) {
		return new CommandAPICommand("exception")
			.executes((sender, args) -> {
				RedisReentrantLock lock = new RedisReentrantLock("exceptionLock");
				try {
					lock.unlock();
				} catch (RedisLockException e) {
					plugin.getLogger().log(Level.INFO, "Successfully caught unlock with no thread owner:", e);
				}

				// This test is mostly reliable, but not entirely reliable
				// because it assumes the next try-catch statement runs within
				// a second.
				new BukkitRunnable() {

					@Override
					public void run() {
						lock.lock();
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						finally {
							lock.unlock();
						}
					}

				}.runTaskAsynchronously(plugin);

				try {
					lock.unlock();
				} catch (RedisLockException e) {
					plugin.getLogger().log(Level.INFO, "Successfully caught unlock with different thread owner:", e);
				}
			});
	}
}
