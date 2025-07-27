package com.playmonumenta.redissync;

import java.util.logging.Logger;

public class ConfigAPI {
	@SuppressWarnings("NullAway") // Required to avoid many null checks, this class will always be instantiated if this plugin is loaded
	private static ConfigAPI INSTANCE = null;

	private final String mRedisHost;
	private final int mRedisPort;
	private final String mServerDomain;
	private final String mShardName;
	private final int mHistoryAmount;
	private final int mTicksPerPlayerAutosave;
	private final boolean mSavingDisabled;
	private final boolean mScoreboardCleanupEnabled;

	public static String getRedisHost() {
		return INSTANCE.mRedisHost;
	}

	public static int getRedisPort() {
		return INSTANCE.mRedisPort;
	}

	/**
	 * Returns the current server domain as set in the config file for this plugin.
	 * <p>
	 * This domain info is useful as a prefix for redis keys so that multiple different types of
	 * servers can share the same redis database without intermingling data
	 */
	public static String getServerDomain() {
		return INSTANCE.mServerDomain;
	}

	public static String getShardName() {
		return INSTANCE.mShardName;
	}

	public static int getHistoryAmount() {
		return INSTANCE.mHistoryAmount;
	}

	public static int getTicksPerPlayerAutosave() {
		return INSTANCE.mTicksPerPlayerAutosave;
	}

	public static boolean getSavingDisabled() {
		return INSTANCE.mSavingDisabled;
	}

	public static boolean getScoreboardCleanupEnabled() {
		return INSTANCE.mScoreboardCleanupEnabled;
	}

	protected ConfigAPI(Logger logger, String redisHost, int redisPort, String serverDomain, String shardName, int historyAmount, int ticksPerPlayerAutosave, boolean savingDisabled, boolean scoreboardCleanupEnabled) {
		mRedisHost = redisHost;
		mRedisPort = redisPort;
		mServerDomain = serverDomain;
		mShardName = shardName;
		mHistoryAmount = historyAmount;
		mTicksPerPlayerAutosave = ticksPerPlayerAutosave;
		mSavingDisabled = savingDisabled;
		mScoreboardCleanupEnabled = scoreboardCleanupEnabled;
		INSTANCE = this;

		logger.info("Configuration:");
		logger.info("  redis_host = " + (mRedisHost == null ? "null" : mRedisHost));
		logger.info("  redis_port = " + mRedisPort);
		logger.info("  server_domain = " + (mServerDomain == null ? "null" : mServerDomain));
		logger.info("  shard_name = " + (mShardName == null ? "null" : mShardName));
		logger.info("  history_amount = " + mHistoryAmount);
		logger.info("  ticks_per_player_autosave = " + mTicksPerPlayerAutosave);
		logger.info("  saving_disabled = " + mSavingDisabled);
		logger.info("  scoreboard_cleanup_enabled = " + mScoreboardCleanupEnabled);
	}

	// Probably a better way to do this
	protected ConfigAPI(org.slf4j.Logger logger, String redisHost, int redisPort, String serverDomain, String shardName, int historyAmount, int ticksPerPlayerAutosave, boolean savingDisabled, boolean scoreboardCleanupEnabled) {
		mRedisHost = redisHost;
		mRedisPort = redisPort;
		mServerDomain = serverDomain;
		mShardName = shardName;
		mHistoryAmount = historyAmount;
		mTicksPerPlayerAutosave = ticksPerPlayerAutosave;
		mSavingDisabled = savingDisabled;
		mScoreboardCleanupEnabled = scoreboardCleanupEnabled;
		INSTANCE = this;

		logger.info("Configuration:");
		logger.info("  redis_host = {}", (mRedisHost == null ? "null" : mRedisHost));
		logger.info("  redis_port = {}", mRedisPort);
		logger.info("  server_domain = {}", (mServerDomain == null ? "null" : mServerDomain));
		logger.info("  shard_name = {}", (mShardName == null ? "null" : mShardName));
		logger.info("  history_amount = {}", mHistoryAmount);
		logger.info("  ticks_per_player_autosave = {}", mTicksPerPlayerAutosave);
		logger.info("  saving_disabled = {}", mSavingDisabled);
		logger.info("  scoreboard_cleanup_enabled = {}", mScoreboardCleanupEnabled);
	}
}
