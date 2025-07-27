package com.destroystokyo.paper.event.player;

import java.io.File;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when the server saves the advancement data for a player
 */
public class PlayerAdvancementDataSaveEvent extends PlayerEvent implements Cancellable {
	private static final HandlerList handlers = new HandlerList();
	@NotNull private String jsonData;
	@NotNull private File path;
	private boolean cancel = false;

	public PlayerAdvancementDataSaveEvent(@NotNull Player who, @NotNull File path, @NotNull String jsonData) {
		super(who);
		this.jsonData = jsonData;
		this.path = path;
	}

	/**
	 * Get the file path where advancement data will be saved to.
	 *
	 * @return advancement data File to save to
	 */
	@NotNull
	public File getPath() {
		return path;
	}

	/**
	 * Set the file path where advancement data will be saved to.
	 */
	public void setPath(@NotNull File path) {
		this.path = path;
	}

	/**
	 * Get the JSON advancements data that will be saved.
	 *
	 * @return JSON data of the player's advancements
	 */
	@NotNull
	public String getJsonData() {
		return jsonData;
	}

	/**
	 * Set the JSON advancements data that will be saved.
	 *
	 * @param jsonData advancement data JSON string to save instead
	 */
	public void setJsonData(@NotNull String jsonData) {
		this.jsonData = jsonData;
	}

	@Override
	public boolean isCancelled() {
		return cancel;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancel = cancel;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	@NotNull
	public static HandlerList getHandlerList() {
		return handlers;
	}
}
