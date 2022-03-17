package com.playmonumenta.redissync;

import java.util.logging.Logger;

public class Conf {
	@SuppressWarnings("NullAway") // Required to avoid many null checks, this class will always be instantiated if this plugin is loaded
	private static Conf INSTANCE = null;

	private final String mHost;
	private final int mPort;
	private final String mDomain;
	private final String mShard;
	private final int mHistory;
	private final int mTicksPerPlayerAutosave;
	private final boolean mSavingDisabled;
	private final boolean mScoreboardCleanupEnabled;

	protected static String getHost() {
		return INSTANCE.mHost;
	}

	protected static int getPort() {
		return INSTANCE.mPort;
	}

	protected static String getDomain() {
		return INSTANCE.mDomain;
	}

	protected static String getShard() {
		return INSTANCE.mShard;
	}

	protected static int getHistory() {
		return INSTANCE.mHistory;
	}

	protected static int getTicksPerPlayerAutosave() {
		return INSTANCE.mTicksPerPlayerAutosave;
	}

	protected static boolean getSavingDisabled() {
		return INSTANCE.mSavingDisabled;
	}

	protected static boolean getScoreboardCleanupEnabled() {
		return INSTANCE.mScoreboardCleanupEnabled;
	}

	protected Conf(Logger logger, String host, int port, String domain, String shard, int history, int ticksPerPlayerAutosave, boolean savingDisabled, boolean scoreboardCleanupEnabled) {
		mHost = host;
		mPort = port;
		mDomain = domain;
		mShard = shard;
		mHistory = history;
		mTicksPerPlayerAutosave = ticksPerPlayerAutosave;
		mSavingDisabled = savingDisabled;
		mScoreboardCleanupEnabled = scoreboardCleanupEnabled;
		INSTANCE = this;

		logger.info("Configuration:");
		logger.info("  redis_host = " + (mHost == null ? "null" : mHost));
		logger.info("  redis_port = " + Integer.toString(mPort));
		logger.info("  server_domain = " + (mDomain == null ? "null" : mDomain));
		logger.info("  shard_name = " + (mShard == null ? "null" : mShard));
		logger.info("  history_amount = " + Integer.toString(mHistory));
		logger.info("  ticks_per_player_autosave = " + Integer.toString(mTicksPerPlayerAutosave));
		logger.info("  saving_disabled = " + Boolean.toString(mSavingDisabled));
		logger.info("  scoreboard_cleanup_enabled = " + Boolean.toString(mScoreboardCleanupEnabled));
	}
}
