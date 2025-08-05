package com.playmonumenta.redissync.config;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ProxyConfig extends CommonConfig {
	protected static @Nullable ProxyConfig PROXY_INSTANCE = null;

	public ProxyConfig(Logger logger, String redisHost, int redisPort, String serverDomain, String shardName) {
		super(redisHost, redisPort, serverDomain, shardName);

		logger.info("Configuration:");
		logger.info("  redis_host = {}", (mRedisHost == null ? "null" : mRedisHost));
		logger.info("  redis_port = {}", mRedisPort);
		logger.info("  server_domain = {}", (mServerDomain == null ? "null" : mServerDomain));
		logger.info("  shard_name = {}", (mShardName == null ? "null" : mShardName));

		COMMON_INSTANCE = this;
		PROXY_INSTANCE = this;
	}

	public static ProxyConfig getProxyInstance() {
		ProxyConfig proxyConfig = PROXY_INSTANCE;
		if (proxyConfig == null) {
			throw new RuntimeException("ProxyConfig not initialized");
		}
		return proxyConfig;
	}
}
