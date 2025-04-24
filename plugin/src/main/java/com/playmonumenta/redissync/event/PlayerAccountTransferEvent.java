package com.playmonumenta.redissync.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerAccountTransferEvent extends PlayerEvent {

	private static final HandlerList handlers = new HandlerList();

	private final UUID mOldId;
	private final String mOldName;

	public PlayerAccountTransferEvent(@NotNull Player player, UUID oldId, String oldName) {
		super(player);
		mOldId = oldId;
		mOldName = oldName;
	}

	/**
	 * Gets the player's previous UUID before they transferred their account
	 * @return The player's previous UUID
	 */
	public UUID getOldId() {
		return mOldId;
	}

	/**
	 * Gets the player's previous name before they transferred their account
	 * @return The player's previous name
	 */
	public String getOldName() {
		return mOldName;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
