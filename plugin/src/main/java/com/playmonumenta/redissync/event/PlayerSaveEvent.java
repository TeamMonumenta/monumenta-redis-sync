package com.playmonumenta.redissync.event;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class PlayerSaveEvent extends PlayerEvent {

	private static final HandlerList handlers = new HandlerList();

	private @Nullable Map<String, JsonObject> mPluginData = null;

	public PlayerSaveEvent(Player player) {
		super(player);
	}

	/**
	 * Sets the plugin data that should be saved for this player
	 *
	 * @param pluginIdentifier  A unique string key identifying which plugin data to get for this player
	 * @param pluginData        The data to save.
	 */
	public void setPluginData(String pluginIdentifier, JsonObject pluginData) {
		if (mPluginData == null) {
			mPluginData = new LinkedHashMap<>();
		}
		mPluginData.put(pluginIdentifier, pluginData);
	}

	/**
	 * Gets the plugin data that has been set by other plugins
	 */
	public @Nullable Map<String, JsonObject> getPluginData() {
		return mPluginData;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
