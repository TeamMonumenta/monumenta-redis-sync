package com.playmonumenta.redissync;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.adapters.VersionAdapter.SaveData;
import com.playmonumenta.redissync.event.PlayerServerTransferEvent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.util.Vector;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.async.RedisAsyncCommands;

public class MonumentaRedisSyncAPI {
	public static class RedisPlayerData {
		private final UUID mUUID;
		private Object mNbtTagCompoundData;
		private String mAdvancements;
		private String mScores;
		private String mPluginData;
		private String mHistory;

		public RedisPlayerData(UUID uuid, Object nbtTagCompoundData, String advancements,
		                       String scores, String pluginData, String history) {
			mUUID = uuid;
			mNbtTagCompoundData = nbtTagCompoundData;
			mAdvancements = advancements;
			mScores = scores;
			mPluginData = pluginData;
			mHistory = history;
		}

		public UUID getUniqueId() {
			return mUUID;
		}

		public Object getNbtTagCompoundData() {
			return mNbtTagCompoundData;
		}

		public String getAdvancements() {
			return mAdvancements;
		}

		public String getScores() {
			return mScores;
		}

		public String getPluginData() {
			return mPluginData;
		}

		public String getHistory() {
			return mHistory;
		}

		public UUID getmUUID() {
			return mUUID;
		}

		public void setNbtTagCompoundData(Object nbtTagCompoundData) {
			this.mNbtTagCompoundData = nbtTagCompoundData;
		}

		public void setAdvancements(String advancements) {
			this.mAdvancements = advancements;
		}

		public void setScores(String scores) {
			this.mScores = scores;
		}

		public void setPluginData(String pluginData) {
			this.mPluginData = pluginData;
		}

		public void setHistory(String history) {
			this.mHistory = history;
		}
	}

	public static final int TIMEOUT_SECONDS = 10;

	private static Map<String, UUID> mNameToUuid = new ConcurrentHashMap<>();
	private static Map<UUID, String> mUuidToName = new ConcurrentHashMap<>();

	protected static void updateUuidToName(UUID uuid, String name) {
		mUuidToName.put(uuid, name);
	}

	protected static void updateNameToUuid(String name, UUID uuid) {
		mNameToUuid.put(name, uuid);
	}

	public static CompletableFuture<String> uuidToName(UUID uuid) {
		return RedisAPI.getInstance().async().hget("uuid2name", uuid.toString()).toCompletableFuture();
	}

	public static CompletableFuture<UUID> nameToUUID(String name) {
		return RedisAPI.getInstance().async().hget("name2uuid", name).thenApply((uuid) -> UUID.fromString(uuid)).toCompletableFuture();
	}

	public static CompletableFuture<Set<String>> getAllPlayerNames() {
		RedisFuture<Map<String, String>> future = RedisAPI.getInstance().async().hgetall("name2uuid");
		return future.thenApply((data) -> data.keySet()).toCompletableFuture();
	}

	public static CompletableFuture<Set<UUID>> getAllPlayerUUIDs() {
		RedisFuture<Map<String, String>> future = RedisAPI.getInstance().async().hgetall("uuid2name");
		return future.thenApply((data) -> data.keySet().stream().map((uuid) -> UUID.fromString(uuid)).collect(Collectors.toSet())).toCompletableFuture();
	}

	public static String cachedUuidToName(UUID uuid) {
		return mUuidToName.get(uuid);
	}

	public static UUID cachedNameToUuid(String name) {
		return mNameToUuid.get(name);
	}

	public static Set<String> getAllCachedPlayerNames() {
		return new ConcurrentSkipListSet<>(mNameToUuid.keySet());
	}

	public static Set<UUID> getAllCachedPlayerUuids() {
		return new ConcurrentSkipListSet<>(mUuidToName.keySet());
	}

	public static String getCachedCurrentName(String oldName) {
		UUID uuid = cachedNameToUuid(oldName);
		if (uuid == null) {
			return null;
		}
		return cachedUuidToName(uuid);
	}

	/* TODO In CommandAPI 6.0.0, use a Trie to handle IGN suggestions */


	/**
     * @deprecated
     * This method that includes a "plugin" argument will be removed in a future version. Simply remove the plugin argument.
     */
	@Deprecated
	public static void sendPlayer(Plugin plugin, Player player, String target) throws Exception {
		sendPlayer(plugin, player, target, null);
	}


	/**
     * @deprecated
     * This method that includes a "plugin" argument will be removed in a future version. Simply remove the plugin argument.
     */
	@Deprecated
	public static void sendPlayer(Plugin plugin, Player player, String target, Location returnLoc) throws Exception {
		sendPlayer(plugin, player, target, returnLoc, null, null);
	}

	/**
     * @deprecated
     * This method that includes a "plugin" argument will be removed in a future version. Simply remove the plugin argument.
     */
	@Deprecated
	public static void sendPlayer(Plugin plugin, Player player, String target, Location returnLoc, Float returnYaw, Float returnPitch) throws Exception {
		sendPlayer(player, target, returnLoc, returnYaw, returnPitch);
	}

	public static void sendPlayer(Player player, String target) throws Exception {
		sendPlayer(player, target, null);
	}

	public static void sendPlayer(Player player, String target, Location returnLoc) throws Exception {
		sendPlayer(player, target, returnLoc, null, null);
	}

	public static void sendPlayer(Player player, String target, Location returnLoc, Float returnYaw, Float returnPitch) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		if (mrs == null) {
			throw new Exception("MonumentaRedisSync is not loaded!");
		}

		/* Don't allow transferring while transferring */
		if (DataEventListener.isPlayerTransferring(player)) {
			return;
		}

		long startTime = System.currentTimeMillis();

		if (target.equalsIgnoreCase(Conf.getShard())) {
			player.sendMessage(ChatColor.RED + "Can not transfer to the same server you are already on");
			return;
		}

		/* If any return params were specified, mark them on the player */
		if (returnLoc != null || returnYaw != null || returnPitch != null) {
			DataEventListener.setPlayerReturnParams(player, returnLoc, returnYaw, returnPitch);
		}

		PlayerServerTransferEvent event = new PlayerServerTransferEvent(player, target);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			return;
		}

		player.sendMessage(ChatColor.GOLD + "Transferring you to " + target);

		savePlayer(mrs, player);

		/* Lock player during transfer and prevent data saving when they log out */
		DataEventListener.setPlayerAsTransferring(player);

		DataEventListener.waitForPlayerToSaveThenSync(player, () -> {
			/*
			 * Use plugin messages to tell bungee to transfer the player.
			 * This is nice because in the event of multiple bungeecord's,
			 * it'll use the one the player is connected to.
			 */
			ByteArrayDataOutput out = ByteStreams.newDataOutput();
			out.writeUTF("Connect");
			out.writeUTF(target);

			player.sendPluginMessage(mrs, "BungeeCord", out.toByteArray());
		});

		mrs.getLogger().fine(() -> "Transferring players took " + Long.toString(System.currentTimeMillis() - startTime) + " milliseconds on main thread");
	}

	public static void stashPut(Player player, String name) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		if (mrs == null) {
			throw new Exception("MonumentaRedisSync is not loaded!");
		}

		savePlayer(mrs, player);

		DataEventListener.waitForPlayerToSaveThenAsync(player, () -> {
			List<RedisFuture<?>> futures = new ArrayList<>();

			RedisAPI api = RedisAPI.getInstance();

			String saveName = name;
			if (saveName == null) {
				saveName = player.getUniqueId().toString();
			} else {
				futures.add(api.async().sadd(getStashListPath(), saveName));
			}

			try {
				/* Read the most-recent player data save, and copy it to the stash */
				RedisFuture<byte[]> dataFuture = api.asyncStringBytes().lindex(MonumentaRedisSyncAPI.getRedisDataPath(player), 0);
				RedisFuture<String> advanceFuture = api.async().lindex(MonumentaRedisSyncAPI.getRedisAdvancementsPath(player), 0);
				RedisFuture<String> scoreFuture = api.async().lindex(MonumentaRedisSyncAPI.getRedisScoresPath(player), 0);
				RedisFuture<String> pluginFuture = api.async().lindex(MonumentaRedisSyncAPI.getRedisPluginDataPath(player), 0);
				RedisFuture<String> historyFuture = api.async().lindex(MonumentaRedisSyncAPI.getRedisHistoryPath(player), 0);

				futures.add(api.asyncStringBytes().hset(getStashPath(), saveName.toString() + "-data", dataFuture.get()));
				futures.add(api.async().hset(getStashPath(), saveName.toString() + "-scores", scoreFuture.get()));
				futures.add(api.async().hset(getStashPath(), saveName.toString() + "-advancements", advanceFuture.get()));
				futures.add(api.async().hset(getStashPath(), saveName.toString() + "-plugins", pluginFuture.get()));
				futures.add(api.async().hset(getStashPath(), saveName.toString() + "-history", historyFuture.get()));

				if (!LettuceFutures.awaitAll(TIMEOUT_SECONDS, TimeUnit.SECONDS, futures.toArray(new RedisFuture[futures.size()]))) {
					MonumentaRedisSync.getInstance().getLogger().severe("Got timeout waiting to commit stash data for player '" + player.getName() + "'");
					player.sendMessage(ChatColor.RED + "Got timeout trying to commit stash data");
					return;
				}
			} catch (InterruptedException | ExecutionException ex) {
				MonumentaRedisSync.getInstance().getLogger().severe("Got exception while committing stash data for player '" + player.getName() + "'");
				ex.printStackTrace();
				player.sendMessage(ChatColor.RED + "Failed to save stash data: " + ex.getMessage());
				return;
			}

			player.sendMessage(ChatColor.GOLD + "Data, scores, advancements saved to stash successfully");
		});
	}

	public static void stashGet(Player player, String name) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		if (mrs == null) {
			throw new Exception("MonumentaRedisSync is not loaded!");
		}

		/*
		 * Save player in case this was a mistake so they can get back
		 * This also saves per-shard data like location
		 */
		savePlayer(mrs, player);

		/* Lock player during stash get */
		DataEventListener.setPlayerAsTransferring(player);

		/* Wait for save to complete */
		DataEventListener.waitForPlayerToSaveThenAsync(player, () -> {
			List<RedisFuture<?>> futures = new ArrayList<>();

			RedisAPI api = RedisAPI.getInstance();

			String saveName = name;
			if (saveName == null) {
				saveName = player.getUniqueId().toString();
			}

			try {
				/* Read from the stash, and push it to the player's data */

				RedisFuture<byte[]> dataFuture = api.asyncStringBytes().hget(getStashPath(), saveName.toString() + "-data");
				RedisFuture<String> advanceFuture = api.async().hget(getStashPath(), saveName.toString() + "-advancements");
				RedisFuture<String> scoreFuture = api.async().hget(getStashPath(), saveName.toString() + "-scores");
				RedisFuture<String> pluginFuture = api.async().hget(getStashPath(), saveName.toString() + "-plugins");
				RedisFuture<String> historyFuture = api.async().hget(getStashPath(), saveName.toString() + "-history");

				/* Make sure there's actually data */
				if (dataFuture.get() == null || advanceFuture.get() == null || scoreFuture.get() == null || pluginFuture.get() == null || historyFuture.get() == null) {
					if (name == null) {
						player.sendMessage(ChatColor.RED + "You don't have any stash data");
					} else {
						player.sendMessage(ChatColor.RED + "No stash data found for '" + name + "'");
					}
					return;
				}

				futures.add(api.asyncStringBytes().lpush(MonumentaRedisSyncAPI.getRedisDataPath(player), dataFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisAdvancementsPath(player), advanceFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisScoresPath(player), scoreFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisPluginDataPath(player), pluginFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisHistoryPath(player), "stash@" + historyFuture.get()));

				if (!LettuceFutures.awaitAll(TIMEOUT_SECONDS, TimeUnit.SECONDS, futures.toArray(new RedisFuture[futures.size()]))) {
					MonumentaRedisSync.getInstance().getLogger().severe("Got timeout loading stash data for player '" + player.getName() + "'");
					player.sendMessage(ChatColor.RED + "Got timeout loading stash data");
					return;
				}
			} catch (InterruptedException | ExecutionException ex) {
				MonumentaRedisSync.getInstance().getLogger().severe("Got exception while loading stash data for player '" + player.getName() + "'");
				ex.printStackTrace();
				player.sendMessage(ChatColor.RED + "Failed to load stash data: " + ex.getMessage());
				return;
			}

			/* Kick the player on the main thread to force rejoin */
			Bukkit.getServer().getScheduler().runTask(mrs, () -> player.kickPlayer("Stash data loaded successfully"));
		});
	}

	public static void stashInfo(Player player, String name) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		if (mrs == null) {
			throw new Exception("MonumentaRedisSync is not loaded!");
		}

		RedisAPI api = RedisAPI.getInstance();

		String saveName;
		if (name != null) {
			saveName = name;
		} else {
			saveName = player.getUniqueId().toString();
		}

		Bukkit.getScheduler().runTaskAsynchronously(mrs, () -> {
			String history = api.sync().hget(getStashPath(), saveName.toString() + "-history");
			Bukkit.getScheduler().runTask(mrs, () -> {
				if (history == null) {
					if (name == null) {
						player.sendMessage(ChatColor.RED + "You don't have any stash data");
					} else {
						player.sendMessage(ChatColor.RED + "No stash data found for '" + name + "'");
					}
					return;
				}

				String[] split = history.split("\\|");
				if (split.length != 3) {
					player.sendMessage(ChatColor.RED + "Got corrupted history with " + Integer.toString(split.length) + " entries: " + history);
					return;
				}

				if (name == null) {
					player.sendMessage(ChatColor.GOLD + "Stash last saved on " + split[0] + " " + getTimeDifferenceSince(Long.parseLong(split[1])) + " ago");
				} else {
					player.sendMessage(ChatColor.GOLD + "Stash '" + name + "' last saved on " + split[0] + " by " + split[2] + " " + getTimeDifferenceSince(Long.parseLong(split[1])) + " ago");
				}
			});
		});
	}

	public static void playerRollback(Player moderator, Player player, int index) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		if (mrs == null) {
			throw new Exception("MonumentaRedisSync is not loaded!");
		}

		/*
		 * Save player in case this was a mistake so they can get back
		 * This also saves per-shard data like location
		 */
		savePlayer(mrs, player);

		/* Now that data has saved, the index we want to roll back to is +1 older */
		final int rollbackIndex = index + 1;

		/* Lock player during rollback */
		DataEventListener.setPlayerAsTransferring(player);

		/* Wait for save to complete */
		DataEventListener.waitForPlayerToSaveThenAsync(player, () -> {
			List<RedisFuture<?>> futures = new ArrayList<>();

			RedisAPI api = RedisAPI.getInstance();

			try {
				/* Read the history element and push it to the player's data */

				RedisFuture<byte[]> dataFuture = api.asyncStringBytes().lindex(getRedisDataPath(player), rollbackIndex);
				RedisFuture<String> advanceFuture = api.async().lindex(getRedisAdvancementsPath(player), rollbackIndex);
				RedisFuture<String> scoreFuture = api.async().lindex(getRedisScoresPath(player), rollbackIndex);
				RedisFuture<String> pluginFuture = api.async().lindex(getRedisPluginDataPath(player), rollbackIndex);
				RedisFuture<String> historyFuture = api.async().lindex(getRedisHistoryPath(player), rollbackIndex);

				/* Make sure there's actually data */
				if (dataFuture.get() == null || advanceFuture.get() == null || scoreFuture.get() == null || pluginFuture.get() == null || historyFuture.get() == null) {
					moderator.sendMessage(ChatColor.RED + "Failed to retrieve player's rollback data");
					return;
				}

				futures.add(api.asyncStringBytes().lpush(MonumentaRedisSyncAPI.getRedisDataPath(player), dataFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisAdvancementsPath(player), advanceFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisScoresPath(player), scoreFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisPluginDataPath(player), pluginFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisHistoryPath(player), "rollback@" + historyFuture.get()));

				if (!LettuceFutures.awaitAll(TIMEOUT_SECONDS, TimeUnit.SECONDS, futures.toArray(new RedisFuture[futures.size()]))) {
					MonumentaRedisSync.getInstance().getLogger().severe("Got timeout loading rollback data for player '" + player.getName() + "'");
					moderator.sendMessage(ChatColor.RED + "Got timeout loading rollback data");
					return;
				}
			} catch (InterruptedException | ExecutionException ex) {
				MonumentaRedisSync.getInstance().getLogger().severe("Got exception while loading rollback data for player '" + player.getName() + "'");
				ex.printStackTrace();
				moderator.sendMessage(ChatColor.RED + "Failed to load rollback data: " + ex.getMessage());
				return;
			}

			moderator.sendMessage(ChatColor.GREEN + "Player " + player.getName() + " rolled back successfully");

			/* Kick the player on the main thread to force rejoin */
			Bukkit.getServer().getScheduler().runTask(mrs, () -> player.kickPlayer("Your player data has been rolled back, and you can now re-join the server"));
		});
	}

	public static void playerLoadFromPlayer(Player loadto, Player loadfrom, int index) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		if (mrs == null) {
			throw new Exception("MonumentaRedisSync is not loaded!");
		}

		/*
		 * Save player in case this was a mistake so they can get back
		 * This also saves per-shard data like location
		 */
		savePlayer(mrs, loadto);

		/* Lock player during load */
		DataEventListener.setPlayerAsTransferring(loadto);

		/* Wait for save to complete */
		DataEventListener.waitForPlayerToSaveThenAsync(loadto, () -> {
			List<RedisFuture<?>> futures = new ArrayList<>();

			RedisAPI api = RedisAPI.getInstance();

			try {
				/* Read the history element and push it to the player's data */

				RedisFuture<byte[]> dataFuture = api.asyncStringBytes().lindex(getRedisDataPath(loadfrom), index);
				RedisFuture<String> advanceFuture = api.async().lindex(getRedisAdvancementsPath(loadfrom), index);
				RedisFuture<String> scoreFuture = api.async().lindex(getRedisScoresPath(loadfrom), index);
				RedisFuture<String> pluginFuture = api.async().lindex(getRedisPluginDataPath(loadfrom), index);
				RedisFuture<String> historyFuture = api.async().lindex(getRedisHistoryPath(loadfrom), index);

				/* Make sure there's actually data */
				if (dataFuture.get() == null || advanceFuture.get() == null || scoreFuture.get() == null || pluginFuture.get() == null || historyFuture.get() == null) {
					loadto.sendMessage(ChatColor.RED + "Failed to retrieve player's data to load");
					return;
				}

				futures.add(api.asyncStringBytes().lpush(MonumentaRedisSyncAPI.getRedisDataPath(loadto), dataFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisAdvancementsPath(loadto), advanceFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisScoresPath(loadto), scoreFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisPluginDataPath(loadto), pluginFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisHistoryPath(loadto), "loadfrom@" + loadfrom.getName() + "@" + historyFuture.get()));

				if (!LettuceFutures.awaitAll(TIMEOUT_SECONDS, TimeUnit.SECONDS, futures.toArray(new RedisFuture[futures.size()]))) {
					MonumentaRedisSync.getInstance().getLogger().severe("Got timeout loading data for player '" + loadfrom.getName() + "'");
					loadto.sendMessage(ChatColor.RED + "Got timeout loading data");
					return;
				}
			} catch (InterruptedException | ExecutionException ex) {
				MonumentaRedisSync.getInstance().getLogger().severe("Got exception while loading data for player '" + loadfrom.getName() + "'");
				ex.printStackTrace();
				loadto.sendMessage(ChatColor.RED + "Failed to load data: " + ex.getMessage());
				return;
			}

			/* Kick the player on the main thread to force rejoin */
			Bukkit.getServer().getScheduler().runTask(mrs, () -> loadto.kickPlayer("Data loaded from player " + loadfrom.getName() + " at index " + Integer.toString(index) + " and you can now re-join the server"));
		});
	}

	/**
	 * Gets a specific remote data entry for a player.
	 *
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return null if no entry was present, String otherwise
	 */
	public static CompletableFuture<String> remoteDataGet(UUID uuid, String key) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<String> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hget(getRedisRemoteDataPath(uuid), key).toCompletableFuture();
	}

	/**
	 * Gets multiple remote data entries for a player.
	 *
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return null if no entry was present, String otherwise
	 */
	public static CompletableFuture<Map<String, String>> remoteDataGetMulti(UUID uuid, String... keys) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hmget(getRedisRemoteDataPath(uuid), keys).toCompletableFuture().thenApply((listResult) -> listResult.stream().collect(Collectors.toMap((entry) -> entry.getKey(), (entry) -> entry.getValue())));
	}

	/**
	 * Sets a specific remote data entry for a player.
	 *
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return True if an entry was set, False otherwise
	 */
	public static CompletableFuture<Boolean> remoteDataSet(UUID uuid, String key, String value) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hset(getRedisRemoteDataPath(uuid), key, value).toCompletableFuture();
	}

	/**
	 * Atomically increments a specific remote data entry for a player.
	 *
	 * Note that this will interpret the hash value as an integer (default 0 if not existing)
	 *
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return Resulting value
	 */
	public static CompletableFuture<Long> remoteDataIncrement(UUID uuid, String key, int incby) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<Long> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hincrby(getRedisRemoteDataPath(uuid), key, incby).toCompletableFuture();
	}

	/**
	 * Deletes a specific key in the player's remote data.
	 *
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return True if an entry was present and was deleted, False if no entry was present to begin with
	 */
	public static CompletableFuture<Boolean> remoteDataDel(UUID uuid, String key) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hdel(getRedisRemoteDataPath(uuid), key).thenApply((val) -> val == 1).toCompletableFuture();
	}

	/**
	 * Gets a map of all remote data for a player.
	 *
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return Non-null map of keys:values. If no data, will return an empty map
	 */
	public static CompletableFuture<Map<String, String>> remoteDataGetAll(UUID uuid) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hgetall(getRedisRemoteDataPath(uuid)).toCompletableFuture();
	}

	/**
	 * Returns the current server domain as set in the config file for this plugin.
	 *
	 * This domain info is useful as a prefix for redis keys so that multiple different types of
	 * servers can share the same redis database without intermingling data
	 */
	public static String getServerDomain() {
		return Conf.getDomain();
	}

	public static String getRedisDataPath(Player player) {
		return getRedisDataPath(player.getUniqueId());
	}

	public static String getRedisDataPath(UUID uuid) {
		return String.format("%s:playerdata:%s:data", Conf.getDomain(), uuid.toString());
	}

	public static String getRedisHistoryPath(Player player) {
		return getRedisHistoryPath(player.getUniqueId());
	}

	public static String getRedisHistoryPath(UUID uuid) {
		return String.format("%s:playerdata:%s:history", Conf.getDomain(), uuid.toString());
	}

	public static String getRedisPerShardDataPath(Player player) {
		return getRedisPerShardDataPath(player.getUniqueId());
	}

	public static String getRedisPerShardDataPath(UUID uuid) {
		return String.format("%s:playerdata:%s:sharddata", Conf.getDomain(), uuid.toString());
	}

	public static String getRedisPerShardDataWorldKey(World world) {
		return getRedisPerShardDataWorldKey(world.getUID(), world.getName());
	}

	public static String getRedisPerShardDataWorldKey(UUID worldUUID, String worldName) {
		return worldUUID.toString() + ":" + worldName;
	}


	public static String getRedisPluginDataPath(Player player) {
		return getRedisPluginDataPath(player.getUniqueId());
	}

	public static String getRedisPluginDataPath(UUID uuid) {
		return String.format("%s:playerdata:%s:plugins", Conf.getDomain(), uuid.toString());
	}

	public static String getRedisAdvancementsPath(Player player) {
		return getRedisAdvancementsPath(player.getUniqueId());
	}

	public static String getRedisAdvancementsPath(UUID uuid) {
		return String.format("%s:playerdata:%s:advancements", Conf.getDomain(), uuid.toString());
	}

	public static String getRedisScoresPath(Player player) {
		return getRedisScoresPath(player.getUniqueId());
	}

	public static String getRedisScoresPath(UUID uuid) {
		return String.format("%s:playerdata:%s:scores", Conf.getDomain(), uuid.toString());
	}

	public static String getRedisRemoteDataPath(Player player) {
		return getRedisRemoteDataPath(player.getUniqueId());
	}

	public static String getRedisRemoteDataPath(UUID uuid) {
		return String.format("%s:playerdata:%s:remotedata", Conf.getDomain(), uuid.toString());
	}

	public static String getStashPath() {
		return String.format("%s:stash", Conf.getDomain());
	}

	public static String getStashListPath() {
		return String.format("%s:stashlist", Conf.getDomain());
	}

	public static String getTimeDifferenceSince(long compareTime) {
		final long diff = System.currentTimeMillis() - compareTime;
		final long diffSeconds = diff / 1000 % 60;
		final long diffMinutes = diff / (60 * 1000) % 60;
		final long diffHours = diff / (60 * 60 * 1000) % 24;
		final long diffDays = diff / (24 * 60 * 60 * 1000);

		String timeStr = "";
		if (diffDays > 0) {
			timeStr += Long.toString(diffDays) + " day";
			if (diffDays > 1) {
				timeStr += "s";
			}
		}

		if (diffDays > 0 && (diffHours > 0 || diffMinutes > 0 || diffSeconds > 0)) {
			timeStr += " ";
		}

		if (diffHours > 0) {
			timeStr += Long.toString(diffHours) + " hour";
			if (diffHours > 1) {
				timeStr += "s";
			}
		}

		if (diffHours > 0 && (diffMinutes > 0 || diffSeconds > 0)) {
			timeStr += " ";
		}

		if (diffMinutes > 0) {
			timeStr += Long.toString(diffMinutes) + " minute";
			if (diffMinutes > 1) {
				timeStr += "s";
			}
		}

		if (diffMinutes > 0 && diffSeconds > 0 && (diffDays == 0 && diffHours == 0)) {
			timeStr += " ";
		}

		if (diffSeconds > 0 && (diffDays == 0 && diffHours == 0)) {
			timeStr += Long.toString(diffSeconds) + " second";
			if (diffSeconds > 1) {
				timeStr += "s";
			}
		}

		return timeStr;
	}

	private static void savePlayer(MonumentaRedisSync mrs, Player player) throws WrapperCommandSyntaxException {
		try {
			mrs.getVersionAdapter().savePlayer(player);
		} catch (Exception ex) {
			String message = "Failed to save player data for player '" + player.getName() + "'";
			mrs.getLogger().severe(message);
			ex.printStackTrace();
			CommandAPI.fail(message);
		}
	}

	/**
	 * Gets player plugin data from the cache.
	 *
	 * Only valid if the player is currently on this shard.
	 *
	 * @param uuid              Player's UUID to get data for
	 * @param pluginIdentifier  A unique string key identifying which plugin data to get for this player
	 *
	 * @return plugin data for this identifier (or null if it doesn't exist or player isn't online)
	 */
	public static @Nullable JsonObject getPlayerPluginData(UUID uuid, String pluginIdentifier) {
		JsonObject pluginData = DataEventListener.getPlayerPluginData(uuid);
		if (pluginData == null || !pluginData.has(pluginIdentifier)) {
			return null;
		}

		JsonElement pluginDataElement = pluginData.get(pluginIdentifier);
		if (!pluginDataElement.isJsonObject()) {
			return null;
		}

		return pluginDataElement.getAsJsonObject();
	}

	public static class PlayerWorldData {
		// Other sharddata fields that are not returned here: {"SpawnDimension":"minecraft:overworld","Dimension":0,"Paper.Origin":[-1450.0,241.0,-1498.0]}"}
		// Note: This list might be out of date

		private final Location mSpawnLoc; // {"SpawnX":-1450,"SpawnY":241,"SpawnZ":-1498,"SpawnAngle":0.0}
		private final Location mPlayerLoc; // {"Pos":[-1280.5,95.0,5369.7001953125],"Rotation":[-358.9,2.1]}
		private final Vector mMotion; // {"Motion":[0.0,-0.0784000015258789,0.0]}
		private final boolean mSpawnForced; // {"SpawnForced":true}
		private final boolean mFlying; // {"flying":false}
		private final boolean mFallFlying; // {"FallFlying":false}
		private final float mFallDistance; // {"FallDistance":0.0}
		private final boolean mOnGround; // {"OnGround":true}

		private PlayerWorldData(Location spawnLoc, Location playerLoc, Vector motion, boolean spawnForced, boolean flying, boolean fallFlying, float fallDistance, boolean onGround) {
			mSpawnLoc = spawnLoc;
			mPlayerLoc = playerLoc;
			mMotion = motion;
			mSpawnForced = spawnForced;
			mFlying = flying;
			mFallFlying = fallFlying;
			mFallDistance = fallDistance;
			mOnGround = onGround;
		}

		public Location getSpawnLoc() {
			return mSpawnLoc;
		}

		public Location getPlayerLoc() {
			return mPlayerLoc;
		}

		public Vector getMotion() {
			return mMotion;
		}

		public boolean getFallFlying() {
			return mFallFlying;
		}

		public double getFallDistance() {
			return mFallDistance;
		}

		public boolean getOnGround() {
			return mOnGround;
		}

		public void applyToPlayer(Player player) {
			player.teleport(mPlayerLoc);
			player.setVelocity(mMotion);
			player.setFlying(mFlying && player.getAllowFlight());
			player.setGliding(mFallFlying);
			player.setFallDistance(mFallDistance);
			player.setBedSpawnLocation(mSpawnLoc, mSpawnForced);
		}

		private static PlayerWorldData fromJson(@Nullable String jsonStr, World world) {
			// Defaults to world spawn
			Location spawnLoc = world.getSpawnLocation();
			Location playerLoc = spawnLoc.clone();
			Vector motion = new Vector(0, 0, 0);
			boolean spawnForced = true;
			boolean flying = false;
			boolean fallFlying = false;
			float fallDistance = 0;
			boolean onGround = true;

			if (jsonStr != null && !jsonStr.isEmpty()) {
				try {
					JsonObject obj = (new Gson()).fromJson(jsonStr, JsonObject.class);
					if (obj.has("SpawnX")) {
						spawnLoc.setX(obj.get("SpawnX").getAsDouble());
					}
					if (obj.has("SpawnY")) {
						spawnLoc.setY(obj.get("SpawnY").getAsDouble());
					}
					if (obj.has("SpawnZ")) {
						spawnLoc.setZ(obj.get("SpawnZ").getAsDouble());
					}
					if (obj.has("Pos")) {
						JsonArray arr = obj.get("Pos").getAsJsonArray();
						playerLoc.setX(arr.get(0).getAsDouble());
						playerLoc.setY(arr.get(1).getAsDouble());
						playerLoc.setZ(arr.get(2).getAsDouble());
					}
					if (obj.has("Rotation")) {
						JsonArray arr = obj.get("Rotation").getAsJsonArray();
						playerLoc.setYaw(arr.get(0).getAsFloat());
						playerLoc.setPitch(arr.get(1).getAsFloat());
					}
					if (obj.has("Motion")) {
						JsonArray arr = obj.get("Motion").getAsJsonArray();
						motion = new Vector(arr.get(0).getAsDouble(), arr.get(1).getAsDouble(), arr.get(2).getAsDouble());
					}
					if (obj.has("SpawnForced")) {
						spawnForced = obj.get("SpawnForced").getAsBoolean();
					}
					if (obj.has("flying")) {
						flying = obj.get("flying").getAsBoolean();
					}
					if (obj.has("FallFlying")) {
						fallFlying = obj.get("FallFlying").getAsBoolean();
					}
					if (obj.has("FallDistance")) {
						fallDistance = obj.get("FallDistance").getAsFloat();
					}
					if (obj.has("OnGround")) {
						onGround = obj.get("OnGround").getAsBoolean();
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}

			return new PlayerWorldData(spawnLoc, playerLoc, motion, spawnForced, flying, fallFlying, fallDistance, onGround);
		}
	}

	/**
	 * Gets player location data for a world
	 *
	 * Only valid if the player is currently on this shard.
	 *
	 * @param player  Player's to get data for
	 * @param world   World to get data for
	 *
	 * @return plugin data for this identifier (or null if it doesn't exist or player isn't online)
	 */
	public static PlayerWorldData getPlayerWorldData(Player player, World world) {
		Map<String, String> shardData = DataEventListener.getPlayerShardData(player.getUniqueId());
		if (shardData == null || shardData.isEmpty()) {
			return PlayerWorldData.fromJson(null, world);
		}

		String worldShardData = shardData.get(getRedisPerShardDataWorldKey(world));
		if (worldShardData == null || worldShardData.isEmpty()) {
			return PlayerWorldData.fromJson(null, world);
		}

		return PlayerWorldData.fromJson(worldShardData, world);
	}

	/**
	 * Retrieve the leaderboard entries between the specified start and stop indices (inclusive)
	 *
	 * @param objective The leaderboard objective name (one leaderboard per objective)
	 * @param start Starting index to retrieve (inclusive)
	 * @param stop Ending index to retrieve (inclusive)
	 * @param ascending If true, leaderboard and results are smallest to largest and vice versa
	 */
	public static CompletableFuture<Map<String, Integer>> getLeaderboard(String objective, long start, long stop, boolean ascending) {
		RedisAPI api = RedisAPI.getInstance();
		final RedisFuture<List<ScoredValue<String>>> values;
		if (ascending) {
			values = api.async().zrangeWithScores(getRedisLeaderboardPath(objective), start, stop);
		} else {
			values = api.async().zrevrangeWithScores(getRedisLeaderboardPath(objective), start, stop);
		}

		return values.thenApply((scores) -> {
			LinkedHashMap<String, Integer> map = new LinkedHashMap<String, Integer>();
			for (ScoredValue<String> value : scores) {
				map.put(value.getValue(), (int)value.getScore());
			}

			return (Map<String, Integer>)map;
		}).toCompletableFuture();
	}

	/**
	 * Updates the specified leaderboard with name/value.
	 *
	 * Update is dispatched asynchronously, this method does not block or return success/failure
	 *
	 * @param objective The leaderboard objective name (one leaderboard per objective)
	 * @param name The name to associate with the value
	 * @param value Leaderboard value
	 */
	public static void updateLeaderboardAsync(String objective, String name, long value) {
		RedisAPI api = RedisAPI.getInstance();
		api.async().zadd(getRedisLeaderboardPath(objective), (double)value, name);
	}

	public static String getRedisLeaderboardPath(String objective) {
		return String.format("%s:leaderboard:%s", Conf.getDomain(), objective);
	}

	/** Future returns non-null if successfully loaded data, null on error */
	@Nullable
	private static RedisPlayerData transformPlayerData(MonumentaRedisSync mrs, UUID uuid, TransactionResult result) {
		if (result.isEmpty() || result.size() != 4 || result.get(0) == null
		    || result.get(1) == null || result.get(2) == null || result.get(3) == null) {
			mrs.getLogger().severe("Failed to retrieve player data");
			return null;
		}

		try {
			byte[] data = result.get(0);
			String advancements = new String(result.get(1), StandardCharsets.UTF_8);
			String scores = new String(result.get(2), StandardCharsets.UTF_8);
			String pluginData = new String(result.get(3), StandardCharsets.UTF_8);
			String history = new String(result.get(4), StandardCharsets.UTF_8);

			return new RedisPlayerData(uuid, mrs.getVersionAdapter().retrieveSaveData(data, null), advancements, scores, pluginData, history);
		} catch (Exception e) {
			mrs.getLogger().severe("Failed to parse player data: " + e.getMessage());
			return null;
		}
	}

	public static CompletableFuture<RedisPlayerData> getOfflinePlayerData(UUID uuid) throws Exception {
		if (Bukkit.getPlayer(uuid) != null) {
			throw new Exception("Player " + uuid.toString() + " is online");
		}

		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		if (mrs == null) {
			throw new Exception("MonumentaRedisSync invoked but is not loaded");
		}

		RedisAsyncCommands<String, byte[]> commands = RedisAPI.getInstance().asyncStringBytes();
		commands.multi();

		commands.lindex(MonumentaRedisSyncAPI.getRedisDataPath(uuid), 0);
		commands.lindex(MonumentaRedisSyncAPI.getRedisAdvancementsPath(uuid), 0);
		commands.lindex(MonumentaRedisSyncAPI.getRedisScoresPath(uuid), 0);
		commands.lindex(MonumentaRedisSyncAPI.getRedisPluginDataPath(uuid), 0);
		commands.lindex(MonumentaRedisSyncAPI.getRedisHistoryPath(uuid), 0);

		return commands.exec().thenApply((TransactionResult result) -> transformPlayerData(mrs, uuid, result)).toCompletableFuture();
	}

	/**
	 * Gets a map of all player scoreboard values.
	 *
	 * If player is online, will pull them from the current scoreboard. This work will be done on the main thread (will take several milliseconds).
	 * If player is offline, will pull them from the most recent redis save on an async thread, then compose them into a map (basically no main thread time)
	 *
	 * The return future will always complete on the main thread with either results or an exception.
	 * Suggest chaining on .whenComplete((data, ex) -> your code) to do something with this data when complete
	 */
	public static CompletableFuture<Map<String, Integer>> getPlayerScores(UUID uuid) {
		CompletableFuture<Map<String, Integer>> future = new CompletableFuture<>();

		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		if (mrs == null) {
			future.completeExceptionally(new Exception("MonumentaRedisSync invoked but is not loaded"));
			return future;
		}

		Player player = Bukkit.getPlayer(uuid);
		if (player != null) {
			Map<String, Integer> scores = new HashMap<>();
			for (Objective objective : Bukkit.getScoreboardManager().getMainScoreboard().getObjectives()) {
				Score score = objective.getScore(player.getName());
				if (score != null) {
					scores.put(objective.getName(), score.getScore());
				}
			}
			future.complete(scores);
			return future;
		}

		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();

		commands.lindex(MonumentaRedisSyncAPI.getRedisScoresPath(uuid), 0)
			.thenApply(
				(scoreData) -> (new Gson()).fromJson(scoreData, JsonObject.class).entrySet().stream().collect(Collectors.toMap((entry) -> entry.getKey(), (entry) -> entry.getValue().getAsInt())))
			.whenComplete((scoreMap, ex) -> {
				Bukkit.getScheduler().runTask(mrs, () -> {
					if (ex != null) {
						future.completeExceptionally(ex);
					} else {
						future.complete(scoreMap);
					}
				});
			});

		return future;
	}

	private static Boolean transformPlayerSaveResult(MonumentaRedisSync mrs, TransactionResult result) {
		if (result.isEmpty() || result.size() != 4 || result.get(0) == null
		    || result.get(1) == null || result.get(2) == null || result.get(3) == null || result.get(4) == null) {
			mrs.getLogger().severe("Failed to commit player data");
			return false;
		}

		return true;
	}

	/** Future returns true if successfully committed, false if not */
	public static CompletableFuture<Boolean> saveOfflinePlayerData(RedisPlayerData data) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		if (mrs == null) {
			throw new Exception("MonumentaRedisSync invoked but is not loaded");
		}

		RedisAsyncCommands<String, byte[]> commands = RedisAPI.getInstance().asyncStringBytes();
		commands.multi();

		SaveData splitData = mrs.getVersionAdapter().extractSaveData(data.getNbtTagCompoundData(), null);
		commands.lpush(MonumentaRedisSyncAPI.getRedisDataPath(data.getUniqueId()), splitData.getData());
		commands.lpush(MonumentaRedisSyncAPI.getRedisAdvancementsPath(data.getUniqueId()), data.getAdvancements().getBytes(StandardCharsets.UTF_8));
		commands.lpush(MonumentaRedisSyncAPI.getRedisScoresPath(data.getUniqueId()), data.getScores().getBytes(StandardCharsets.UTF_8));
		commands.lpush(MonumentaRedisSyncAPI.getRedisPluginDataPath(data.getUniqueId()), data.getPluginData().getBytes(StandardCharsets.UTF_8));
		commands.lpush(MonumentaRedisSyncAPI.getRedisHistoryPath(data.getUniqueId()), data.getHistory().getBytes(StandardCharsets.UTF_8));

		return commands.exec().thenApply((TransactionResult result) -> transformPlayerSaveResult(mrs, result)).toCompletableFuture();
	}

	/*********************************************************************************
	 * rboard API
	 */

	public static String getRedisRboardPath(String name) throws Exception {
		if (!name.matches("^[-_0-9A-Za-z$]+$")) {
			throw new Exception("Name '" + name + "' contains illegal characters, must match '^[-_$0-9A-Za-z$]+'");
		}
		return String.format("%s:rboard:%s", Conf.getDomain(), name);
	}

	/********************* Set *********************/
	public static CompletableFuture<Long> rboardSet(String name, Map<String, String> data) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hset(getRedisRboardPath(name), data).toCompletableFuture();
	}

	/********************* Add *********************/
	public static CompletableFuture<Long> rboardAdd(String name, String key, long amount) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hincrby(getRedisRboardPath(name), key, amount).toCompletableFuture();
	}

	/********************* Get *********************/
	public static CompletableFuture<Map<String, String>> rboardGet(String name, String... keys) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		commands.multi();
		for (String key : keys) {
			commands.hincrby(getRedisRboardPath(name), key, 0);
		}
		CompletableFuture<Map<String, String>> retval = commands.hmget(getRedisRboardPath(name), keys).toCompletableFuture().thenApply(list -> {
			Map<String, String> transformed = new LinkedHashMap<>();
			list.forEach(item -> transformed.put(item.getKey(), item.getValue()));
			return transformed;
		});
		commands.exec();
		return retval;
	}

	/********************* GetAndReset *********************/
	public static CompletableFuture<Map<String, String>> rboardGetAndReset(String name, String... keys) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		commands.multi();
		CompletableFuture<Map<String, String>> retval = commands.hmget(getRedisRboardPath(name), keys).toCompletableFuture().thenApply(list -> {
			Map<String, String> transformed = new LinkedHashMap<>();
			list.forEach(item -> transformed.put(item.getKey(), item.getValue()));
			return transformed;
		});
		commands.hdel(getRedisRboardPath(name), keys).toCompletableFuture();
		commands.exec();
		return retval;
	}

	/********************* GetKeys *********************/
	public static CompletableFuture<List<String>> rboardGetKeys(String name) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hkeys(getRedisRboardPath(name)).toCompletableFuture();
	}

	/********************* GetAll *********************/
	public static CompletableFuture<Map<String, String>> rboardGetAll(String name) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hgetall(getRedisRboardPath(name)).toCompletableFuture();
	}

	/********************* Reset *********************/
	public static CompletableFuture<Long> rboardReset(String name, String... keys) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hdel(getRedisRboardPath(name), keys).toCompletableFuture();
	}

	/********************* ResetAll *********************/
	public static CompletableFuture<Long> rboardResetAll(String name) throws Exception {
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.del(getRedisRboardPath(name)).toCompletableFuture();
	}

	/*
	 * rboard API
	 *********************************************************************************/

	/**
	 * Runs the result of an asynchronous transaction on the main thread after it is completed
	 *
	 * Will always call the callback function eventually, even if the resulting transaction fails or is lost.
	 *
	 * When the function is called, either data will be non-null and exception null,
	 * or data will be null and the exception will be non-null
	 */
	public static <T> void runWhenAvailable(Plugin plugin, CompletableFuture<T> input, BiConsumer<T, Exception> func) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			Exception ex;
			T data;

			try {
				data = input.get();
				ex = null;
			} catch (Exception e) {
				data = null;
				ex = e;
			}

			final T result = data;
			final Exception except = ex;

			Bukkit.getScheduler().runTask(plugin, () -> {
				func.accept(result, except);
			});
		});
	}

	/**
	 * If MonumentaNetworkRelay is installed, returns a list of all other shard names
	 * that are currently up and valid transfer targets from this server.
	 *
	 * If MonumentaNetworkRelay is not installed, returns an empty array.
	 */
	public static String[] getOnlineTransferTargets() {
		return NetworkRelayIntegration.getOnlineTransferTargets();
	}

	public static <T> void runOnMainThreadWhenComplete(Plugin plugin, CompletableFuture<T> future, BiConsumer<T, Throwable> func) {
		future.whenComplete((T result, Throwable ex) -> {
			Bukkit.getScheduler().runTask(plugin, () -> {
				func.accept(result, ex);
			});
		});
	}
}
