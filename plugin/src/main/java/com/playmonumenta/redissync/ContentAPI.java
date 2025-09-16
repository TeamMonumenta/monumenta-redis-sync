package com.playmonumenta.redissync;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class ContentAPI {
	public static Map<String, Double> localContent() {
		return ContentManager.localContent();
	}

	public static Map<String, Map<String, Double>> remoteContent() {
		return ContentManager.remoteContent();
	}

	public static @Nullable String healthiestShardWithContent(String contentId) {
		return ContentManager.healthiestShardWithContent(contentId);
	}
}
