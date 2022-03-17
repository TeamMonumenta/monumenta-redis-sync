package com.playmonumenta.redissync.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerTransferFailEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Player mPlayer;

	public PlayerTransferFailEvent(Player player) {
		mPlayer = player;
	}

	public Player getPlayer() {
		return mPlayer;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
