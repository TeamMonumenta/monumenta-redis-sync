package com.playmonumenta.redissync;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.lettuce.core.api.async.RedisAsyncCommands;

public class RBoardAPI {

	public static String getRedisPath(String name) throws Exception {
		if (!name.matches("^[-_0-9A-Za-z$]+$")) {
			throw new Exception("Name '" + name + "' contains illegal characters, must match '^[-_$0-9A-Za-z$]+'");
		}
		return String.format("%s:rboard:%s", ConfigAPI.getServerDomain(), name);
	}

	/********************* Set *********************/
	public static CompletableFuture<Long> set(String name, Map<String, String> data) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hset(getRedisPath(name), data).toCompletableFuture();
	}

	public static CompletableFuture<Long> set(String name, String key, long amount) throws Exception {
		Map<String, String> data = new HashMap<>();
		data.put(key, Long.toString(amount));
		return set(name, data);
	}

	/********************* Add *********************/
	public static CompletableFuture<Long> add(String name, String key, long amount) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hincrby(getRedisPath(name), key, amount).toCompletableFuture();
	}

	/********************* Get *********************/
	public static CompletableFuture<Map<String, String>> get(String name, String... keys) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		commands.multi();
		for (String key : keys) {
			commands.hincrby(getRedisPath(name), key, 0);
		}
		CompletableFuture<Map<String, String>> retval = commands.hmget(getRedisPath(name), keys).toCompletableFuture().thenApply(list -> {
			Map<String, String> transformed = new LinkedHashMap<>();
			list.forEach(item -> transformed.put(item.getKey(), item.getValue()));
			return transformed;
		});
		commands.exec();
		return retval;
	}

	public static long getAsLong(String name, String key, long def) throws Exception {
		Map<String, String> data = get(name, key).get();
		String value = data.get(key);
		if (value != null) {
			try {
				return Long.parseLong(value);
			} catch (NumberFormatException nfe) {
				nfe.printStackTrace();
			}
		}
		return def;
	}

	public static long getAsLong(String name, String key) throws Exception {
		return getAsLong(name, key, 0);
	}

	/********************* GetAndReset *********************/
	public static CompletableFuture<Map<String, String>> getAndReset(String name, String... keys) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		commands.multi();
		CompletableFuture<Map<String, String>> retval = commands.hmget(getRedisPath(name), keys).toCompletableFuture().thenApply(list -> {
			Map<String, String> transformed = new LinkedHashMap<>();
			list.forEach(item -> transformed.put(item.getKey(), item.getValue()));
			return transformed;
		});
		commands.hdel(getRedisPath(name), keys).toCompletableFuture();
		commands.exec();
		return retval;
	}

	/********************* GetKeys *********************/
	public static CompletableFuture<List<String>> getKeys(String name) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hkeys(getRedisPath(name)).toCompletableFuture();
	}

	/********************* GetAll *********************/
	public static CompletableFuture<Map<String, String>> getAll(String name) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hgetall(getRedisPath(name)).toCompletableFuture();
	}

	/********************* Reset *********************/
	public static CompletableFuture<Long> reset(String name, String... keys) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hdel(getRedisPath(name), keys).toCompletableFuture();
	}

	/********************* ResetAll *********************/
	public static CompletableFuture<Long> resetAll(String name) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.del(getRedisPath(name)).toCompletableFuture();
	}
}
