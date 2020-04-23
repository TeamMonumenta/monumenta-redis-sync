package com.playmonumenta.redissync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.playmonumenta.redissync.event.PlayerServerTransferEvent;

import io.github.jorelali.commandapi.api.CommandAPI;
import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisFuture;

public class MonumentaRedisSyncAPI {
	public static final int TIMEOUT_SECONDS = 10;

	public static CompletableFuture<String> uuidToName(UUID uuid) {
		return RedisAPI.getInstance().async().hget(BungeeListener.uuidToNamePath, uuid.toString()).toCompletableFuture();
	}

	public static CompletableFuture<UUID> nameToUUID(String name) {
		return RedisAPI.getInstance().async().hget(BungeeListener.nameToUUIDPath, name).thenApply((uuid) -> UUID.fromString(uuid)).toCompletableFuture();
	}

	public static CompletableFuture<Set<String>> getAllPlayerNames() {
		RedisFuture<Map<String, String>> future = RedisAPI.getInstance().async().hgetall(BungeeListener.nameToUUIDPath);
		return future.thenApply((data) -> data.keySet()).toCompletableFuture();
	}

	public static CompletableFuture<Set<UUID>> getAllPlayerUUIDs() {
		RedisFuture<Map<String, String>> future = RedisAPI.getInstance().async().hgetall(BungeeListener.nameToUUIDPath);
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

		try {
			mrs.getVersionAdapter().savePlayer(player);
		} catch (Exception ex) {
			String message = "Failed to save player data for player '" + player.getName() + "'";
			mrs.getLogger().severe(message);
			ex.printStackTrace();
			CommandAPI.fail(message);
		}

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

		try {
			mrs.getVersionAdapter().savePlayer(player);
		} catch (Exception ex) {
			String message = "Failed to save player data for player '" + player.getName() + "'";
			mrs.getLogger().severe(message);
			ex.printStackTrace();
			CommandAPI.fail(message);
		}

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
				}
			} catch (InterruptedException | ExecutionException ex) {
				MonumentaRedisSync.getInstance().getLogger().severe("Got exception while committing stash data for player '" + player.getName() + "'");
				ex.printStackTrace();
				player.sendMessage(ChatColor.RED + "Failed to save stash data: " + ex.getMessage());
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
		try {
			mrs.getVersionAdapter().savePlayer(player);
		} catch (Exception ex) {
			String message = "Failed to save player data for player '" + player.getName() + "'";
			mrs.getLogger().severe(message);
			ex.printStackTrace();
			CommandAPI.fail(message);
		}

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
				}
			} catch (InterruptedException | ExecutionException ex) {
				MonumentaRedisSync.getInstance().getLogger().severe("Got exception while loading stash data for player '" + player.getName() + "'");
				ex.printStackTrace();
				player.sendMessage(ChatColor.RED + "Failed to load stash data: " + ex.getMessage());
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
		try {
			mrs.getVersionAdapter().savePlayer(player);
		} catch (Exception ex) {
			String message = "Failed to save player data for player '" + player.getName() + "'";
			mrs.getLogger().severe(message);
			ex.printStackTrace();
			CommandAPI.fail(message);
		}

		/* Now that data has saved, the index we want to roll back to is +1 older */
		final int rollbackIndex = index + 1;

		/* Lock player during stash get */
		DataEventListener.setPlayerAsTransferring(player);

		/* Wait for save to complete */
		DataEventListener.waitForPlayerToSaveThenAsync(player, () -> {
			List<RedisFuture<?>> futures = new ArrayList<>();

			RedisAPI api = RedisAPI.getInstance();

			try {
				/* Read from the stash, and push it to the player's data */

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
				}
			} catch (InterruptedException | ExecutionException ex) {
				MonumentaRedisSync.getInstance().getLogger().severe("Got exception while loading rollback data for player '" + player.getName() + "'");
				ex.printStackTrace();
				moderator.sendMessage(ChatColor.RED + "Failed to load rollback data: " + ex.getMessage());
			}

			moderator.sendMessage(ChatColor.GREEN + "Player " + player.getName() + " rolled back successfully");

			/* Kick the player on the main thread to force rejoin */
			Bukkit.getServer().getScheduler().runTask(mrs, () -> player.kickPlayer("Your player data has been rolled back, and you can now re-join the server"));
		});
	}


	public static String getRedisDataPath(Player player) {
		return String.format("%s:playerdata:%s:data", Conf.getDomain(), player.getUniqueId().toString());
	}

	public static String getRedisHistoryPath(Player player) {
		return String.format("%s:playerdata:%s:history", Conf.getDomain(), player.getUniqueId().toString());
	}

	public static String getRedisPerShardDataPath(Player player) {
		return String.format("%s:playerdata:%s:sharddata", Conf.getDomain(), player.getUniqueId().toString());
	}

	public static String getRedisAdvancementsPath(Player player) {
		return String.format("%s:playerdata:%s:advancements", Conf.getDomain(), player.getUniqueId().toString());
	}

	public static String getRedisScoresPath(Player player) {
		return String.format("%s:playerdata:%s:scores", Conf.getDomain(), player.getUniqueId().toString());
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

}

