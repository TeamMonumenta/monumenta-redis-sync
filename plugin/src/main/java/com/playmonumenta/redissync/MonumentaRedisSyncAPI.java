package com.playmonumenta.redissync;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
		private String mPluginData;
		private String mHistory;

		public RedisPlayerData(@Nonnull UUID uuid, @Nonnull Object nbtTagCompoundData, @Nonnull String advancements,
		                       @Nonnull String scores, @Nonnull String pluginData, @Nonnull String history) {
			mUUID = uuid;
			mNbtTagCompoundData = nbtTagCompoundData;
			mAdvancements = advancements;
			mScores = scores;
			mPluginData = pluginData;
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
		public String getPluginData() {
			return mPluginData;
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

		public void setPluginData(@Nonnull String pluginData) {
			this.mPluginData = pluginData;
		}

		public void setHistory(@Nonnull String history) {
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
		return String.format("%s:playerdata:%s:plugins", Conf.getDomain(), uuid.toString());
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
	 * Loads player plugin data asynchronously, and calls the provided callback function when the request completes
	 *
	 * Note that it may take up to several seconds to service the request, depending on the latency of the connection to the redis database
	 * This function will not block and will return very quickly, suitable for use on the main thread.
	 *
	 * @param uuid              Player's UUID to get data for
	 * @param pluginIdentifier  A unique string key identifying which plugin data to get for this player
	 * @param plugin			An enabled plugin handle to schedule Bukkit tasks under
	 * @param Consumer			A function to call with the data when it has finished loading.
	 *
	 * @return
	 *     If data loading is successful and data was retrieved, consumer will be called with (String, null)
	 *     If redis request was successful but there was no data to load, consumer will be called with (null, null)
	 *     If the redis request failed, consumer will be called with (null, Exception)
	 */
	public static @Nullable JsonObject getPlayerPluginData(@Nonnull UUID uuid, @Nonnull String pluginIdentifier) {
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
			String pluginData = new String(result.get(3), StandardCharsets.UTF_8);
			String history = new String(result.get(4), StandardCharsets.UTF_8);

			return new RedisPlayerData(uuid, mrs.getVersionAdapter().retrieveSaveData(data, null), advancements, scores, pluginData, history);
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
		commands.lindex(MonumentaRedisSyncAPI.getRedisPluginDataPath(uuid), 0);
		commands.lindex(MonumentaRedisSyncAPI.getRedisHistoryPath(uuid), 0);

		return commands.exec().thenApply((TransactionResult result) -> transformPlayerData(mrs, uuid, result)).toCompletableFuture();
	}

	@Nonnull
	private static Boolean transformPlayerSaveResult(@Nonnull MonumentaRedisSync mrs, @Nonnull TransactionResult result) {
		if (result.isEmpty() || result.size() != 4 || result.get(0) == null
		    || result.get(1) == null || result.get(2) == null || result.get(3) == null || result.get(4) == null) {
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
		commands.lpush(MonumentaRedisSyncAPI.getRedisPluginDataPath(data.getUniqueId()), data.getPluginData().getBytes(StandardCharsets.UTF_8));
		commands.lpush(MonumentaRedisSyncAPI.getRedisHistoryPath(data.getUniqueId()), data.getHistory().getBytes(StandardCharsets.UTF_8));

		return commands.exec().thenApply((TransactionResult result) -> transformPlayerSaveResult(mrs, result)).toCompletableFuture();
	}

	/*********************************************************************************
	 * rboard API
	 */

	@Nonnull
	public static String getRedisRboardPath(@Nonnull String name) throws Exception {
		if (!name.matches("^[-_0-9A-Za-z$]+$")) {
			throw new Exception("Name '" + name + "' contains illegal characters, must match '^[-_$0-9A-Za-z$]+'");
		}
		return String.format("%s:rboard:%s", Conf.getDomain(), name);
	}

	/********************* Set *********************/
	public static CompletableFuture<Long> rboardSet(String name, Map<String, String> data) throws Exception {
		RedisAsyncCommands<String,String> commands = RedisAPI.getInstance().async();
		return commands.hset(getRedisRboardPath(name), data).toCompletableFuture();
	}

	/********************* Add *********************/
	public static CompletableFuture<Long> rboardAdd(String name, String key, long amount) throws Exception {
		RedisAsyncCommands<String,String> commands = RedisAPI.getInstance().async();
		return commands.hincrby(getRedisRboardPath(name), key, amount).toCompletableFuture();
	}

	/********************* Get *********************/
	public static CompletableFuture<Map<String, String>> rboardGet(String name, String... keys) throws Exception {
		RedisAsyncCommands<String,String> commands = RedisAPI.getInstance().async();
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
		RedisAsyncCommands<String,String> commands = RedisAPI.getInstance().async();
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
		RedisAsyncCommands<String,String> commands = RedisAPI.getInstance().async();
		return commands.hkeys(getRedisRboardPath(name)).toCompletableFuture();
	}

	/********************* GetAll *********************/
	public static CompletableFuture<Map<String, String>> rboardGetAll(String name) throws Exception {
		RedisAsyncCommands<String,String> commands = RedisAPI.getInstance().async();
		return commands.hgetall(getRedisRboardPath(name)).toCompletableFuture();
	}

	/********************* Reset *********************/
	public static CompletableFuture<Long> rboardReset(String name, String... keys) throws Exception {
		RedisAsyncCommands<String,String> commands = RedisAPI.getInstance().async();
		return commands.hdel(getRedisRboardPath(name), keys).toCompletableFuture();
	}

	/********************* ResetAll *********************/
	public static CompletableFuture<Long> rboardResetAll(String name) throws Exception {
		RedisAsyncCommands<String,String> commands = RedisAPI.getInstance().async();
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
}
