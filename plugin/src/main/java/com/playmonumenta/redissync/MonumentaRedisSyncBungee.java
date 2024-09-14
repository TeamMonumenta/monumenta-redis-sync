package com.playmonumenta.redissync;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class MonumentaRedisSyncBungee extends Plugin {
	private @Nullable RedisAPI mRedisAPI = null;
	private @Nullable CustomLogger mLogger = null;

	@Override
	public void onEnable() {
		/* Needed to tell Netty where it moved to */
		System.setProperty("com.playmonumenta.redissync.internal.netty", "com.playmonumenta.redissync.internal");

		loadConfig();
		mRedisAPI = new RedisAPI(ConfigAPI.getRedisHost(), ConfigAPI.getRedisPort());
		getProxy().getPluginManager().registerListener(this, new BungeeListener());

		mRedisAPI.sync().configSet("notify-keyspace-events", "Kgx");
	}

	@Override
	public void onDisable() {
		if (mRedisAPI != null) {
			mRedisAPI.shutdown();
		}
		mRedisAPI = null;
		RedisReentrantLock.shutdownExecutors();
	}

	private void loadConfig() {
		File configFile = new File(getDataFolder(), "config.yml");
		/* TODO: Default file if not exist */
		Configuration config;
		try {
			config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
		} catch (IOException ex) {
			getLogger().warning("Failed to load config file, using defaults: " + ex.getMessage());
			config = new Configuration();
		}
		String redisHost = config.getString("redis_host", "redis");
		int redisPort = config.getInt("redis_port", 6379);
		String serverDomain = config.getString("server_domain", "bungee");
		String shardName = config.getString("shard_name", "bungee");
		int historyAmount = -1;
		int ticksPerPlayerAutosave = -1;
		boolean savingDisabled = true;
		boolean scoreboardCleanupEnabled = false;

		String level = config.getString("log_level", "INFO").toLowerCase();
		switch (level) {
			case "finest":
				setLogLevel(Level.FINEST);
				break;
			case "finer":
				setLogLevel(Level.FINER);
				break;
			case "fine":
				setLogLevel(Level.FINE);
				break;
			default:
				setLogLevel(Level.INFO);
		}

		new ConfigAPI(getLogger(), redisHost, redisPort, serverDomain, shardName, historyAmount, ticksPerPlayerAutosave, savingDisabled, scoreboardCleanupEnabled);
	}

	public void setLogLevel(Level level) {
		super.getLogger().info("Changing log level to: " + level.toString());
		getLogger().setLevel(level);
	}

	@Override
	public Logger getLogger() {
		if (mLogger == null) {
			mLogger = new CustomLogger(super.getLogger(), Level.INFO);
		}
		return mLogger;
	}
}
