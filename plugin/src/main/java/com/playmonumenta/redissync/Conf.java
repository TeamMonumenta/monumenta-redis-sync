package com.playmonumenta.redissync;

public class Conf {
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

	protected Conf(String host, int port, String domain, String shard, int history, int ticksPerPlayerAutosave, boolean savingDisabled, boolean scoreboardCleanupEnabled) {
		mHost = host;
		mPort = port;
		mDomain = domain;
		mShard = shard;
		mHistory = history;
		mTicksPerPlayerAutosave = ticksPerPlayerAutosave;
		mSavingDisabled = savingDisabled;
		mScoreboardCleanupEnabled = scoreboardCleanupEnabled;
		INSTANCE = this;
	}
}
