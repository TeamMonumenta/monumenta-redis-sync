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

	public UUID getOldId() {
		return mOldId;
	}

	public String getOldName() {
		return mOldName;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}
}
