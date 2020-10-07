package com.playmonumenta.redissync;

import java.io.File;
import java.io.IOException;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class MonumentaRedisSyncBungee extends Plugin {
	private RedisAPI mRedisAPI = null;

	@Override
	public void onEnable() {
		/* Needed to tell Netty where it moved to */
		System.setProperty("com.playmonumenta.redissync.internal.netty", "com.playmonumenta.redissync.internal");

		loadConfig();
		mRedisAPI = new RedisAPI(Conf.getHost(), Conf.getPort());
		getProxy().getPluginManager().registerListener(this, new BungeeListener());
	}

	@Override
	public void onDisable() {
		if (mRedisAPI != null) {
			mRedisAPI.shutdown();
		}
		mRedisAPI = null;
	}

	private void loadConfig() {
		File configFile = new File(this.getDataFolder(), "config.yml");
		/* TODO: Default file if not exist */
		String host = "redis";
		int port = 6379;
		try {
			Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
			host = config.getString("redis_host", host);
			port = config.getInt("redis_port", port);
		} catch (IOException ex) {
			this.getLogger().warning("Failed to load config file " + configFile.getPath() + " : " + ex.getMessage());
			return;
		}
		new Conf(host, port, null, "bungee", -1, -1, true, false);
	}
}
