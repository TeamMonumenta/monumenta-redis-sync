package com.playmonumenta.redissync.event;

import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class PlayerSaveEvent extends PlayerEvent {

	private static final HandlerList handlers = new HandlerList();

	private final Map<String, JsonObject> mPluginData;

	public PlayerSaveEvent(Player player, JsonObject pluginData) {
		super(player);
		mPluginData = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : pluginData.entrySet()) {
			JsonElement valueElement = entry.getValue();
			if (valueElement instanceof JsonObject value) {
				mPluginData.put(entry.getKey(), value);
			}
		}
	}

	/**
	 * Sets the plugin data that should be saved for this player
	 *
	 * @param pluginIdentifier  A unique string key identifying which plugin data to get for this player
	 * @param pluginData        The data to save.
	 */
	public void setPluginData(String pluginIdentifier, @Nullable JsonObject pluginData) {
		if (pluginData == null) {
			mPluginData.remove(pluginIdentifier);
		} else {
			mPluginData.put(pluginIdentifier, pluginData);
		}
	}

	/**
	 * Gets the plugin data that has been set by other plugins
	 */
	public Map<String, JsonObject> getPluginData() {
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
