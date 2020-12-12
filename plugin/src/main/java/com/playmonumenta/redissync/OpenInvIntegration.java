package com.playmonumenta.redissync;

import java.util.Iterator;
import java.util.logging.Logger;

import com.lishid.openinv.OpenInv;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/*
 * This prevents OpenInv from overwriting the player's inventory when they log back in
 * if a moderator just happened to have that player's inventory open when they logged out.
 */
public class OpenInvIntegration implements Listener {
	private final Logger mLogger;

	public OpenInvIntegration(Logger logger) {
		mLogger = logger;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerQuit(PlayerQuitEvent event) {
		mLogger.info("Closing openinv viewers for player " + event.getPlayer().getName());
		closeAll(event.getPlayer().getInventory().getViewers());
		closeAll(event.getPlayer().getEnderChest().getViewers());
		OpenInv.getPlugin(OpenInv.class).unload(event.getPlayer());
	}

	private void closeAll(Iterable<HumanEntity> viewers) {
		Iterator<HumanEntity> iterator = viewers.iterator();
		while (iterator.hasNext()) {
			HumanEntity viewer = iterator.next();
			viewer.closeInventory();
			mLogger.info("Closed viewer " + viewer.getName());
		}
	}
}
