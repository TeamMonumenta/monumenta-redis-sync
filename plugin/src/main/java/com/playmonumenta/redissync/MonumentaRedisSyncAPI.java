package com.playmonumenta.redissync;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.playmonumenta.redissync.adapters.VersionAdapter.SaveData;
import com.playmonumenta.redissync.event.PlayerServerTransferEvent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

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
		private String mHistory;

		public RedisPlayerData(@Nonnull UUID uuid, @Nonnull Object nbtTagCompoundData, @Nonnull String advancements,
		                       @Nonnull String scores, @Nonnull String history) {
			mUUID = uuid;
			mNbtTagCompoundData = nbtTagCompoundData;
			mAdvancements = advancements;
			mScores = scores;
			mHistory = history;
		}

		@Nonnull
		public UUID getUniqueId() {
			return mUUID;
		}

		@Nonnull
		public Object getNbtTagCompoundData() {
			return mNbtTagCompoundData;
		}

		@Nonnull
		public String getAdvancements() {
			return mAdvancements;
		}

		@Nonnull
		public String getScores() {
			return mScores;
		}

		@Nonnull
		public String getHistory() {
			return mHistory;
		}

		@Nonnull
		public UUID getmUUID() {
			return mUUID;
		}

		public void setNbtTagCompoundData(@Nonnull Object nbtTagCompoundData) {
			this.mNbtTagCompoundData = nbtTagCompoundData;
		}

		public void setAdvancements(@Nonnull String advancements) {
			this.mAdvancements = advancements;
		}

		public void setScores(@Nonnull String scores) {
			this.mScores = scores;
		}

		public void setHistory(@Nonnull String history) {
			this.mHistory = history;
		}
	}

	public static final int TIMEOUT_SECONDS = 10;

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

	public static void sendPlayer(Plugin plugin, Player player, String target) throws Exception {
		sendPlayer(plugin, player, target, null);
	}

	public static void sendPlayer(Plugin plugin, Player player, String target, Location returnLoc) throws Exception {
		sendPlayer(plugin, player, target, returnLoc, null, null);
	}

	public static void sendPlayer(Plugin plugin, Player player, String target, Location returnLoc, Float returnYaw, Float returnPitch) throws Exception {
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

			player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
		});

		/* TODO: Verbosity */
		mrs.getLogger().info("Transferring players took " + Long.toString(System.currentTimeMillis() - startTime) + " milliseconds on main thread");
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
				RedisFuture<String> historyFuture = api.async().lindex(MonumentaRedisSyncAPI.getRedisHistoryPath(player), 0);

				futures.add(api.asyncStringBytes().hset(getStashPath(), saveName.toString() + "-data", dataFuture.get()));
				futures.add(api.async().hset(getStashPath(), saveName.toString() + "-scores", scoreFuture.get()));
				futures.add(api.async().hset(getStashPath(), saveName.toString() + "-advancements", advanceFuture.get()));
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
				RedisFuture<String> historyFuture = api.async().hget(getStashPath(), saveName.toString() + "-history");

				/* Make sure there's actually data */
				if (dataFuture.get() == null || advanceFuture.get() == null || scoreFuture.get() == null || historyFuture.get() == null) {
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

		new BukkitRunnable() {
			public void run() {
				RedisAPI api = RedisAPI.getInstance();

				String saveName = name;
				if (saveName == null) {
					saveName = player.getUniqueId().toString();
				}

				String history = api.sync().hget(getStashPath(), saveName.toString() + "-history");
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
			}
		}.runTaskAsynchronously(mrs);
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
				RedisFuture<String> historyFuture = api.async().lindex(getRedisHistoryPath(player), rollbackIndex);

				/* Make sure there's actually data */
				if (dataFuture.get() == null || advanceFuture.get() == null || scoreFuture.get() == null || historyFuture.get() == null) {
					moderator.sendMessage(ChatColor.RED + "Failed to retrieve player's rollback data");
					return;
				}

				futures.add(api.asyncStringBytes().lpush(MonumentaRedisSyncAPI.getRedisDataPath(player), dataFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisAdvancementsPath(player), advanceFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisScoresPath(player), scoreFuture.get()));
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
				RedisFuture<String> historyFuture = api.async().lindex(getRedisHistoryPath(loadfrom), index);

				/* Make sure there's actually data */
				if (dataFuture.get() == null || advanceFuture.get() == null || scoreFuture.get() == null || historyFuture.get() == null) {
					loadto.sendMessage(ChatColor.RED + "Failed to retrieve player's data to load");
					return;
				}

				futures.add(api.asyncStringBytes().lpush(MonumentaRedisSyncAPI.getRedisDataPath(loadto), dataFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisAdvancementsPath(loadto), advanceFuture.get()));
				futures.add(api.async().lpush(MonumentaRedisSyncAPI.getRedisScoresPath(loadto), scoreFuture.get()));
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

	@Nonnull
	public static String getRedisDataPath(@Nonnull Player player) {
		return getRedisDataPath(player.getUniqueId());
	}

	@Nonnull
	public static String getRedisDataPath(@Nonnull UUID uuid) {
		return String.format("%s:playerdata:%s:data", Conf.getDomain(), uuid.toString());
	}

	@Nonnull
	public static String getRedisHistoryPath(@Nonnull Player player) {
		return getRedisHistoryPath(player.getUniqueId());
	}

	@Nonnull
	public static String getRedisHistoryPath(@Nonnull UUID uuid) {
		return String.format("%s:playerdata:%s:history", Conf.getDomain(), uuid.toString());
	}

	@Nonnull
	public static String getRedisPerShardDataPath(@Nonnull Player player) {
		return getRedisPerShardDataPath(player.getUniqueId());
	}

	@Nonnull
	public static String getRedisPerShardDataPath(@Nonnull UUID uuid) {
		return String.format("%s:playerdata:%s:sharddata", Conf.getDomain(), uuid.toString());
	}

	@Nonnull
	public static String getRedisPluginDataPath(@Nonnull Player player) {
		return getRedisPluginDataPath(player.getUniqueId());
	}

	@Nonnull
	public static String getRedisPluginDataPath(@Nonnull UUID uuid) {
		return String.format("%s:playerdata:%s:plugindata", Conf.getDomain(), uuid.toString());
	}

	@Nonnull
	public static String getRedisAdvancementsPath(@Nonnull Player player) {
		return getRedisAdvancementsPath(player.getUniqueId());
	}

	@Nonnull
	public static String getRedisAdvancementsPath(@Nonnull UUID uuid) {
		return String.format("%s:playerdata:%s:advancements", Conf.getDomain(), uuid.toString());
	}

	@Nonnull
	public static String getRedisScoresPath(@Nonnull Player player) {
		return getRedisScoresPath(player.getUniqueId());
	}

	@Nonnull
	public static String getRedisScoresPath(@Nonnull UUID uuid) {
		return String.format("%s:playerdata:%s:scores", Conf.getDomain(), uuid.toString());
	}

	@Nonnull
	public static String getStashPath() {
		return String.format("%s:stash", Conf.getDomain());
	}

	@Nonnull
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
	 * Saves player plugin data asynchronously, and calls the provided callback function when the request completes
	 *
	 * @param uuid              Player's UUID to get data for
	 * @param pluginIdentifier  A unique string key identifying which plugin data to get for this player
	 * @param data              The data to save. Recommend compacted JSON string or similar. Unicode is supported.
	 * @param plugin			An enabled plugin handle to schedule Bukkit tasks under
	 * @param consumer			A function to call with the data when it has finished saving.
	 *
	 * @return
	 *   If data saving is successful, consumer will be called with <null>
	 *   If data saving was not successful, consumer will be called with an <Exception>
	 *
	 * Note that it may take up to several seconds to service the request, depending on the latency of the connection to the redis database
	 *
	 * This function will not block and will return very quickly, suitable for use on the main thread.
	 */
	public static void savePlayerPluginData(@Nonnull UUID uuid, @Nonnull String pluginIdentifier, @Nonnull String data, @Nonnull Plugin plugin, @Nonnull Consumer<Exception> consumer) {
		/* Start the data save request on the main thread */
		final CompletableFuture<Boolean> future = savePlayerPluginData(uuid, pluginIdentifier, data);

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			Boolean saveResult;
			try {
				/* On an async thread, block until the result is available */
				saveResult = future.get();

				Bukkit.getScheduler().runTask(plugin, () -> {
					if (saveResult) {
						// Success
						consumer.accept(null);
					} else {
						// Failed - throw an exception for consistency
						consumer.accept(new Exception("Data saving returned False"));
					}
				});
			} catch (InterruptedException | ExecutionException e) {
				Bukkit.getScheduler().runTask(plugin, () -> {
					// Pass the exception to the consumer
					consumer.accept(e);
				});
			}
		});
	}

	/**
	 * Starts saving player plugin data asynchronously, and returns a future that can be read to check the status.
	 *
	 * This is the more complicated of the two methods to use. Recommend using the other version unless you have a need for this.
	 *
	 * @param uuid              Player's UUID to get data for
	 * @param pluginIdentifier  A unique string key identifying which plugin data to get for this player
	 * @param data              The data to save. Recommend compacted JSON string or similar. Unicode is supported.
	 *
	 * @return
	 *   Consumer that can be used to check whether data was saved successfully when .get() is called on it.
	 *
	 * This function will not block and is suitable for the main thread, but .get() on the returned value will - potentially for several seconds
	 * depending on the latency of the connection to the redis database. You should not call .get() on the main thread.
	 */
	@Nonnull
	public static CompletableFuture<Boolean> savePlayerPluginData(@Nonnull UUID uuid, @Nonnull String pluginIdentifier, @Nonnull String data) {
		RedisAPI api = RedisAPI.getInstance();
		return api.async().hset(getRedisPluginDataPath(uuid), pluginIdentifier, data).toCompletableFuture();
	}

	/**
	 * Loads player plugin data asynchronously, and calls the provided callback function when the request completes
	 *
	 * @param uuid              Player's UUID to get data for
	 * @param pluginIdentifier  A unique string key identifying which plugin data to get for this player
	 * @param plugin			An enabled plugin handle to schedule Bukkit tasks under
	 * @param Consumer			A function to call with the data when it has finished loading.
	 *
	 * @return
	 *   If data loading is successful and data was retrieved, consumer will be called with <String, null>
	 *   If redis request was successful but there was no data to load, consumer will be called with <null, null>
	 *   If the redis request failed, consumer will be called with <null, Exception>
	 *
	 * Note that it may take up to several seconds to service the request, depending on the latency of the connection to the redis database
	 *
	 * This function will not block and will return very quickly, suitable for use on the main thread.
	 */
	public static void loadPlayerPluginData(@Nonnull UUID uuid, @Nonnull String pluginIdentifier, @Nonnull Plugin plugin, @Nonnull BiConsumer<String, Exception> consumer) {
		/* Start the data load request on the main thread */
		final CompletableFuture<String> data = loadPlayerPluginData(uuid, pluginIdentifier);

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			String jsonData;
			try {
				/* On an async thread, block until the data is available */
				jsonData = data.get();

				Bukkit.getScheduler().runTask(plugin, () -> {
					// Pass the data to the consumer
					consumer.accept(jsonData, null);
				});
			} catch (InterruptedException | ExecutionException e) {
				Bukkit.getScheduler().runTask(plugin, () -> {
					// Pass the exception to the consumer
					consumer.accept(null, e);
				});
			}
		});
	}

	/**
	 * Starts loading player plugin data asynchronously, and returns a future that can be read later to get the data.
	 *
	 * This is the more complicated of the two methods to use. Recommend using the other version unless you have a need for this.
	 *
	 * @param uuid              Player's UUID to get data for
	 * @param pluginIdentifier  A unique string key identifying which plugin data to get for this player
	 *
	 * @return
	 *   Consumer that will return the data when .get() is called on it.
	 *
	 * This function will not block and is suitable for the main thread, but .get() on the returned value will - potentially for several seconds
	 * depending on the latency of the connection to the redis database. You should not call .get() on the main thread.
	 */
	@Nonnull
	public static CompletableFuture<String> loadPlayerPluginData(@Nonnull UUID uuid, @Nonnull String pluginIdentifier) {
		RedisAPI api = RedisAPI.getInstance();
		return api.async().hget(getRedisPluginDataPath(uuid), pluginIdentifier).toCompletableFuture();
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

	@Nonnull
	public static String getRedisLeaderboardPath(String objective) {
		return String.format("%s:leaderboard:%s", Conf.getDomain(), objective);
	}

	/** Future returns non-null if successfully loaded data, null on error */
	@Nullable
	private static RedisPlayerData transformPlayerData(@Nonnull MonumentaRedisSync mrs, @Nonnull UUID uuid, @Nonnull TransactionResult result) {
		if (result.isEmpty() || result.size() != 4 || result.get(0) == null
		    || result.get(1) == null || result.get(2) == null || result.get(3) == null) {
			mrs.getLogger().severe("Failed to retrieve player data");
			return null;
		}

		try {
			byte[] data = result.get(0);
			String advancements = new String(result.get(1), StandardCharsets.UTF_8);
			String scores = new String(result.get(2), StandardCharsets.UTF_8);
			String history = new String(result.get(3), StandardCharsets.UTF_8);

			return new RedisPlayerData(uuid, mrs.getVersionAdapter().retrieveSaveData(data, null), advancements, scores, history);
		} catch (Exception e) {
			mrs.getLogger().severe("Failed to parse player data: " + e.getMessage());
			return null;
		}
	}

	@Nonnull
	public static CompletableFuture<RedisPlayerData> getOfflinePlayerData(@Nonnull UUID uuid) throws Exception {
		if (Bukkit.getPlayer(uuid) != null) {
			throw new Exception("Player " + uuid.toString() + " is online");
		}

		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		if (mrs == null) {
			throw new Exception("MonumentaRedisSync invoked but is not loaded");
		}

		RedisAsyncCommands<String,byte[]> commands = RedisAPI.getInstance().asyncStringBytes();
		commands.multi();

		commands.lindex(MonumentaRedisSyncAPI.getRedisDataPath(uuid), 0);
		commands.lindex(MonumentaRedisSyncAPI.getRedisAdvancementsPath(uuid), 0);
		commands.lindex(MonumentaRedisSyncAPI.getRedisScoresPath(uuid), 0);
		commands.lindex(MonumentaRedisSyncAPI.getRedisHistoryPath(uuid), 0);

		return commands.exec().thenApply((TransactionResult result) -> transformPlayerData(mrs, uuid, result)).toCompletableFuture();
	}

	@Nonnull
	private static Boolean transformPlayerSaveResult(@Nonnull MonumentaRedisSync mrs, @Nonnull TransactionResult result) {
		if (result.isEmpty() || result.size() != 4 || result.get(0) == null
		    || result.get(1) == null || result.get(2) == null || result.get(3) == null) {
			mrs.getLogger().severe("Failed to commit player data");
			return false;
		}

		return true;
	}

	/** Future returns true if successfully committed, false if not */
	@Nonnull
	public static CompletableFuture<Boolean> saveOfflinePlayerData(@Nonnull RedisPlayerData data) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		if (mrs == null) {
			throw new Exception("MonumentaRedisSync invoked but is not loaded");
		}

		RedisAsyncCommands<String,byte[]> commands = RedisAPI.getInstance().asyncStringBytes();
		commands.multi();

		SaveData splitData = mrs.getVersionAdapter().extractSaveData(data.getNbtTagCompoundData(), null);
		commands.lpush(MonumentaRedisSyncAPI.getRedisDataPath(data.getUniqueId()), splitData.getData());
		commands.lpush(MonumentaRedisSyncAPI.getRedisAdvancementsPath(data.getUniqueId()), data.getAdvancements().getBytes(StandardCharsets.UTF_8));
		commands.lpush(MonumentaRedisSyncAPI.getRedisScoresPath(data.getUniqueId()), data.getScores().getBytes(StandardCharsets.UTF_8));
		commands.lpush(MonumentaRedisSyncAPI.getRedisHistoryPath(data.getUniqueId()), data.getHistory().getBytes(StandardCharsets.UTF_8));

		return commands.exec().thenApply((TransactionResult result) -> transformPlayerSaveResult(mrs, result)).toCompletableFuture();
	}
}
