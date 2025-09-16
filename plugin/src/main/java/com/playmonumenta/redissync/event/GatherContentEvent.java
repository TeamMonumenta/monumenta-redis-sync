package com.playmonumenta.redissync.event;

import com.playmonumenta.redissync.NetworkRelayIntegration;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class GatherContentEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Map<String, Double> mLocalContent = new TreeMap<>();

	public GatherContentEvent() {
	}

	/**
	 * Allows plugins to provide contentId for players to join
	 * @param contentId The contentId ID to join
	 * @param contentHealth The contentId's health on a scale of 0.0 (unhealthy/dead) to 1.0 (ideal state to join)
	 *                      This might be based on how many instances are available to play, for instance.
	 *                      Do not include the ShardHealth from the current shard's NetworkRelay plugin;
	 *                      this handles that for you.
	 */
	public void addContent(String contentId, double contentHealth) {
		mLocalContent.put(contentId, contentHealth * NetworkRelayIntegration.getLocalShardHealth());
	}

	/**
	 * Returns the local content provided by plugins listening for this event
	 * @return A map structured as localContent[shardId] = contentHealth
	 */
	public Map<String, Double> localContent() {
		return new TreeMap<>(mLocalContent);
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
