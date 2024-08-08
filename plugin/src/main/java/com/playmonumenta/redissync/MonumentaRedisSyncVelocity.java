package com.playmonumenta.redissync;

import com.google.inject.Inject;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

@Plugin(id = "monumenta-redisapi", name = "Monumenta-RedisAPI", version = "", url = "", description = "", authors = {""})
public class MonumentaRedisSyncVelocity {
	private @Nullable RedisAPI mRedisAPI = null;
	private final ProxyServer mServer;
	private final Logger mLogger;

	private final YamlConfigurationLoader mLoader; // Config reader & writer
	private @Nullable CommentedConfigurationNode mBaseConfig;
	public RedisConfiguration mConfig = new RedisConfiguration();

	@Inject
	public MonumentaRedisSyncVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
		mServer = server;
		mLogger = logger;

		this.mLoader = YamlConfigurationLoader.builder()
			.path(dataDirectory.resolve(Path.of("config.yaml"))) // Set where we will load and save to
			.nodeStyle(NodeStyle.BLOCK)
			.build();

		/* Needed to tell Netty where it moved to */
		System.setProperty("com.playmonumenta.redissync.internal.netty", "com.playmonumenta.redissync.internal");

		loadConfig();
		mRedisAPI = new RedisAPI(ConfigAPI.getRedisHost(), ConfigAPI.getRedisPort());
	}

	@Subscribe
	public void onEnable(ProxyInitializeEvent event) {
		mServer.getEventManager().register(this, new VelocityListener());
	}

	// we use ProxyShutdownEvent because ListenerClosEvent might fire too early
	@Subscribe(order = PostOrder.LATE)
	public void onDisable(ProxyShutdownEvent event) {
		if (mRedisAPI != null) {
			mRedisAPI.shutdown();
		}
		mRedisAPI = null;
	}

	private void loadConfig() {
		try {
			// load config
			mBaseConfig = mLoader.load();
			mConfig = mBaseConfig.get(RedisConfiguration.class);
		} catch (ConfigurateException ex) {
			// TODO: may want to shut down the proxy if configuration fails to load
			mLogger.warn("Failed to load config file, using defaults: " + ex.getMessage());
		}
		// save config
		saveConfig();

		String redisHost = mConfig.mRedisHost;
		int redisPort = mConfig.mRedisPort;
		String serverDomain = mConfig.mServerDomain;
		String shardName = mConfig.mShardName;
		int historyAmount = -1;
		int ticksPerPlayerAutosave = -1;
		boolean savingDisabled = true;
		boolean scoreboardCleanupEnabled = false;

		new ConfigAPI(mLogger, redisHost, redisPort, serverDomain, shardName, historyAmount, ticksPerPlayerAutosave, savingDisabled, scoreboardCleanupEnabled);
	}

	private void saveConfig() {
		if (mBaseConfig == null || mConfig == null) {
			mLogger.warn("Tried to save current config but config is null!");
			return;
		}
		try {
			mBaseConfig.set(RedisConfiguration.class, mConfig); // Update the backing node
			mLoader.save(mBaseConfig); // Write to the original file
		} catch (ConfigurateException ex) {
			mLogger.warn("Could not save config.yaml", ex);
		}
	}

	@ConfigSerializable
	public static class RedisConfiguration {
		@Setting(value = "redis_host")
		public String mRedisHost = "redis";

		@Setting(value = "redis_port")
		public int mRedisPort = 6379;

		@Setting(value = "server_domain")
		public String mServerDomain = "bungee";

		@Setting(value = "shard_name")
		public String mShardName = "bungee";
	}
}
