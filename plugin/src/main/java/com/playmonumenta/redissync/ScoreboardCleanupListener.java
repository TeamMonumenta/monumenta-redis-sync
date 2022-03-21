package com.playmonumenta.redissync;

import java.util.HashMap;
import java.util.Iterator;
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
import org.bukkit.scheduler.BukkitTask;

public class ScoreboardCleanupListener implements Listener {
	private static final int CLEANUP_LOGOUT_DELAY = 20 * 60 * 1; // 1 minute

	private final Plugin mPlugin;
	private final Logger mLogger;
	private final Map<UUID, BukkitTask> mCleanupTasks = new HashMap<>();
	private final VersionAdapter mAdapter;

	protected ScoreboardCleanupListener(Plugin plugin, Logger logger, VersionAdapter adapter) {
		mPlugin = plugin;
		mLogger = logger;
		mAdapter = adapter;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataLoadEvent(PlayerAdvancementDataLoadEvent event) {
		cancelCleanupTask(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerDataLoadEvent(PlayerDataLoadEvent event) {
		cancelCleanupTask(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void playerJoinEvent(PlayerJoinEvent event) {
		cancelCleanupTask(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void playerQuitEvent(PlayerQuitEvent event) {
		cancelCleanupTask(event.getPlayer());

		if (!ConfigAPI.getScoreboardCleanupEnabled()) {
			return;
		}

		// Remove any completed runnables from the map to keep things clean
		Iterator<Map.Entry<UUID, BukkitTask>> iter = mCleanupTasks.entrySet().iterator();
		while (iter.hasNext()) {
			if (iter.next().getValue().isCancelled()) {
				iter.remove();
			}
		}

		String playerName = event.getPlayer().getName();
		mCleanupTasks.put(event.getPlayer().getUniqueId(), Bukkit.getScheduler().runTaskLater(mPlugin, () -> {
			mAdapter.resetPlayerScores(playerName, Bukkit.getScoreboardManager().getMainScoreboard());
			mLogger.info("Removed scores for player " + playerName + " from local scoreboard");
		}, CLEANUP_LOGOUT_DELAY));
	}

	private void cancelCleanupTask(Player player) {
		BukkitTask cleanupTask = mCleanupTasks.remove(player.getUniqueId());
		if (cleanupTask != null && !cleanupTask.isCancelled()) {
			cleanupTask.cancel();
		}
	}
}
