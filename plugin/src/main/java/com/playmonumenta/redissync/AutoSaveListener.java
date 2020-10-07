package com.playmonumenta.redissync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerDataLoadEvent;
import com.playmonumenta.redissync.adapters.VersionAdapter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class AutoSaveListener implements Listener {
	private final Map<UUID, BukkitRunnable> mPendingSaves = new HashMap<>();

	protected AutoSaveListener(Plugin plugin, VersionAdapter adapter) {
		Logger logger = plugin.getLogger();

		new BukkitRunnable() {
			@Override
			public void run() {
				// Create a local copy of the online players list
				List<? extends Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

				if (players.size() <= 0) {
					// Nothing to do this iteration
					return;
				}

				// Sort the players list by name
				// This helps ensure that a player will be saved consistently about the same time apart
				players.sort((a, b) -> a.getName().compareTo(b.getName()));

				// Distribute saves evenly in the autosave interval.
				// This means that at most a player will take two autosave intervals to save
				int delayBetweenSaves = Conf.getTicksPerPlayerAutosave() / players.size();

				// Remove all the previous iteration saves
				Iterator<Map.Entry<UUID, BukkitRunnable>> iter = mPendingSaves.entrySet().iterator();
				while (iter.hasNext()) {
					Map.Entry<UUID, BukkitRunnable> entry = iter.next();

					if (!entry.getValue().isCancelled()) {
						logger.severe("Player autosave for " + entry.getKey() + " did not complete before next autosave!");
						entry.getValue().cancel();
					}

					iter.remove();
				}

				// Schedule an individual save task one-time runnable for each player
				for (int i = 0; i < players.size(); i++) {
					Player player = players.get(i);
					UUID uuid = player.getUniqueId();

					BukkitRunnable saveRunnable = new BukkitRunnable() {
						@Override
						public void run() {
							try {
								if (player.isOnline() && !DataEventListener.isPlayerTransferring(player)) {
									adapter.savePlayer(player);
								}
							} catch (Exception ex) {
								logger.severe("Failed to autosave player " + player.getName() + ":" + ex.getMessage());
								ex.printStackTrace();
							}

							// Needed to mark this task as complete
							this.cancel();
						}
					};

					// Schedule this player's save for that later time slot
					saveRunnable.runTaskLater(plugin, i * delayBetweenSaves);

					mPendingSaves.put(uuid, saveRunnable);
				}
			}
		}.runTaskTimer(plugin, Conf.getTicksPerPlayerAutosave(), Conf.getTicksPerPlayerAutosave());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerAdvancementDataLoadEvent(PlayerAdvancementDataLoadEvent event) {
		cancelSaveTask(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerDataLoadEvent(PlayerDataLoadEvent event) {
		cancelSaveTask(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerJoinEvent(PlayerJoinEvent event) {
		cancelSaveTask(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerQuitEvent(PlayerQuitEvent event) {
		cancelSaveTask(event.getPlayer());
	}

	private void cancelSaveTask(Player player) {
		BukkitRunnable cleanupTask = mPendingSaves.remove(player.getUniqueId());
		if (cleanupTask != null && !cleanupTask.isCancelled()) {
			cleanupTask.cancel();
		}
	}
}
