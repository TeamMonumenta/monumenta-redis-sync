package com.playmonumenta.redissync;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TransferFailEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Player mPlayer;

	public TransferFailEvent(Player player) {
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
