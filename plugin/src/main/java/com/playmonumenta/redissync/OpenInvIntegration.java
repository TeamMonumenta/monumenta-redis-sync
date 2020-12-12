package com.playmonumenta.redissync;

import com.lishid.openinv.OpenInv;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/*
 * This prevents OpenInv from overwriting the player's inventory when they log back in
 * if a moderator just happened to have that player's inventory open when they logged out.
 */
public class OpenInvIntegration implements Listener {
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerQuit(PlayerQuitEvent event) {
		event.getPlayer().getInventory().getViewers().forEach((viewer) -> viewer.closeInventory());
		event.getPlayer().getEnderChest().getViewers().forEach((viewer) -> viewer.closeInventory());
		OpenInv.getPlugin(OpenInv.class).unload(event.getPlayer());
	}
}
