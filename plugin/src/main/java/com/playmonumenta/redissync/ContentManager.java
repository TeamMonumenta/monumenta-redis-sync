package com.playmonumenta.redissync;

import com.playmonumenta.redissync.event.GatherContentEvent;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.annotations.Nullable;

public class ContentManager {
	/**
	 * Gets a map of locally available content (content name to its health)
	 * @return A map of local content to its health (unhealthiest possible is 0.0, healthiest possible is 1.0)
	 */
	public static Map<String, Double> localContent() {
		GatherContentEvent event = new GatherContentEvent();
		event.callEvent();
		return event.localContent();
	}

	/**
	 * Returns a map of content, which shard hosts it, and what its health is
	 * @return A map structured as remoteContent[contentId][shard] = contentHealth
	 */
	public static Map<String, Map<String, Double>> remoteContent() {
		return NetworkRelayIntegration.getRemoteContent();
	}

	public static @Nullable String healthiestShardWithContent(String contentId) {
		Map<String, Double> contentShards = remoteContent().get(contentId);
		if (contentShards == null) {
			return null;
		}

		Map.Entry<String, Double> bestEntry = null;
		for (Map.Entry<String, Double> entry : contentShards.entrySet()) {
			if (bestEntry == null) {
				bestEntry = entry;
				continue;
			}

			if (entry.getValue() > bestEntry.getValue()) {
				bestEntry = entry;
			}
		}

		return bestEntry == null ? null : bestEntry.getKey();
	}
}
