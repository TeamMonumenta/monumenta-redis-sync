package com.playmonumenta.redissync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerAdvancementDataSaveEvent;
import com.destroystokyo.paper.event.player.PlayerDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerDataSaveEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.adapters.VersionAdapter;
import com.playmonumenta.redissync.adapters.VersionAdapter.SaveData;
import com.playmonumenta.redissync.utils.ScoreboardUtils;

import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisFuture;

public class DataEventListener implements Listener {
	public static class ReturnParams {
		public final Location mReturnLoc;
		public final Float mReturnYaw;
		public final Float mReturnPitch;

		private ReturnParams(Location returnLoc, Float returnYaw, Float returnPitch) {
			mReturnLoc = returnLoc;
			mReturnYaw = returnYaw;
			mReturnPitch = returnPitch;
		}
	}

	private static final String TRANSFER_UNLOCK_TASK_METAKEY = "RedisSyncTransferUnlockMetakey";
	private static final int TRANSFER_UNLOCK_TIMEOUT_TICKS = 10 * 20;
	private static DataEventListener INSTANCE = null;

	private final Gson mGson = new Gson();
	private final Logger mLogger;
	private final VersionAdapter mAdapter;
	private final Set<UUID> mTransferringPlayers = new HashSet<>();
	private final Map<UUID, ReturnParams> mReturnParams = new HashMap<>();

	private final Map<UUID, List<RedisFuture<?>>> mPendingSaves = new HashMap<>();

	protected DataEventListener(Logger logger, VersionAdapter adapter) {
		mLogger = logger;
		mAdapter = adapter;
		INSTANCE = this;
	}

	/********************* Protected API *********************/

	protected static void setPlayerAsTransferring(Player player) throws Exception {
		if (INSTANCE.mTransferringPlayers.contains(player.getUniqueId())) {
			throw new Exception("Player " + player.getName() + " is already transferring");
		}
		INSTANCE.mTransferringPlayers.add(player.getUniqueId());

		/*
		 * Start a task to automatically unlock the player if transfer times out.
		 * This task is cancelled when player leaves the server (PlayerQuitEvent)
		 */
		Plugin plugin = MonumentaRedisSync.getInstance();
		BukkitRunnable unlockRunnable = new BukkitRunnable() {
			@Override
			public void run() {
				if (DataEventListener.isPlayerTransferring(player)) {
					player.sendMessage(ChatColor.RED + "Transferring timed out and your player has been unlocked");
					DataEventListener.setPlayerAsNotTransferring(player);
					player.removeMetadata(TRANSFER_UNLOCK_TASK_METAKEY, plugin);
				}
			}
		};
		unlockRunnable.runTaskLater(plugin, TRANSFER_UNLOCK_TIMEOUT_TICKS);
		player.setMetadata(TRANSFER_UNLOCK_TASK_METAKEY,
		                   new FixedMetadataValue(plugin, unlockRunnable));
	}

	protected static void setPlayerReturnParams(Player player, Location returnLoc, Float returnYaw, Float returnPitch) {
		INSTANCE.mReturnParams.put(player.getUniqueId(), new ReturnParams(returnLoc, returnYaw, returnPitch));
	}

	protected static void setPlayerAsNotTransferring(Player player) {
		INSTANCE.mTransferringPlayers.remove(player.getUniqueId());
		INSTANCE.mReturnParams.remove(player.getUniqueId());
	}

	protected static boolean isPlayerTransferring(Player player) {
		return INSTANCE.mTransferringPlayers.contains(player.getUniqueId());
	}

	protected static void waitForPlayerToSaveThenSync(Player player, Runnable callback) {
		INSTANCE.waitForPlayerToSaveInternal(player, callback, true);
	}

	protected static void waitForPlayerToSaveThenAsync(Player player, Runnable callback) {
		INSTANCE.waitForPlayerToSaveInternal(player, callback, false);
	}

	private void waitForPlayerToSaveInternal(Player player, Runnable callback, boolean sync) {
		Plugin plugin = MonumentaRedisSync.getInstance();

		if (!mPendingSaves.containsKey(player.getUniqueId()) && !Conf.getSavingDisabled()) {
			mLogger.warning("Got request to wait for save commit but no pending save operations found. This might be a bug with the plugin that uses MonumentaRedisSync");
		}

		long startTime = System.currentTimeMillis();

		new BukkitRunnable() {
			public void run() {
				blockingWaitForPlayerToSave(player);

				/* TODO: Verbosity */
				mLogger.info("Committing save took " + Long.toString(System.currentTimeMillis() - startTime) + " milliseconds");

				if (sync) {
					/* Run the sync callback on the main thread */
					new BukkitRunnable() {
						public void run() {
							callback.run();
						}
					}.runTask(plugin);
				} else {
					/* Run the async callback directly */
					callback.run();
				}
			}
		}.runTaskAsynchronously(plugin);
	}

	private void blockingWaitForPlayerToSave(Player player) {
		List<RedisFuture<?>> futures = mPendingSaves.remove(player.getUniqueId());

		if (futures == null || futures.isEmpty()) {
			return;
		}

		if (!LettuceFutures.awaitAll(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS, futures.toArray(new RedisFuture[futures.size()]))) {
			mLogger.severe("Got timeout waiting to commit transactions for player '" + player.getName() + "'. This is very bad!");
		}
	}

	/********************* Data Save/Load Event Handlers *********************/

	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerAdvancementDataLoadEvent(PlayerAdvancementDataLoadEvent event) {
		Player player = event.getPlayer();

		if (Conf.getSavingDisabled()) {
			/* No data saved, no data loaded */
			return;
		}

		/* TODO: Decrease verbosity */
		mLogger.info("Loading advancements data for player=" + player.getName());

		/* Wait until player has finished saving if they just logged out and back in */
		blockingWaitForPlayerToSave(player);

		RedisFuture<String> advanceFuture = RedisAPI.getInstance().async().lindex(MonumentaRedisSyncAPI.getRedisAdvancementsPath(player), 0);
		RedisFuture<String> scoreFuture = RedisAPI.getInstance().async().lindex(MonumentaRedisSyncAPI.getRedisScoresPath(player), 0);

		try {
			/* Advancements */
			String jsonData = advanceFuture.get();
			mLogger.finer("Data:" + jsonData);
			if (jsonData != null) {
				event.setJsonData(jsonData);
			} else {
				mLogger.warning("No advancements data for player '" + player.getName() + "' - if they are not new, this is a serious error!");
			}

			/* Scoreboards */
			/* TODO: Decrease verbosity */
			mLogger.info("Loading scoreboard data for player=" + player.getName());
			jsonData = scoreFuture.get();
			mLogger.finer("Data:" + jsonData);
			if (jsonData != null) {
				JsonObject obj = mGson.fromJson(jsonData, JsonObject.class);
				if (obj != null) {
					ScoreboardUtils.loadFromJsonObject(player, obj);
				} else {
					mLogger.severe("Failed to parse player '" + player.getName() + "' scoreboard data as JSON. This results in data loss!");
				}
			} else {
				mLogger.warning("No scoreboard data for player '" + player.getName() + "' - if they are not new, this is a serious error!");
			}
		} catch (InterruptedException | ExecutionException ex) {
			mLogger.severe("Failed to get advancements/scores data for player '" + player.getName() + "'. This is very bad!");
			ex.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerAdvancementDataSaveEvent(PlayerAdvancementDataSaveEvent event) {
		/* Always cancel saving the player file to disk with this plugin present */
		event.setCancelled(true);

		if (Conf.getSavingDisabled()) {
			/* No data saved, no data loaded */
			return;
		}

		Player player = event.getPlayer();
		if (isPlayerTransferring(player)) {
			mLogger.fine("Ignoring PlayerAdvancementDataSaveEvent for player:" + player.getName());
			return;
		}

		List<RedisFuture<?>> futures = mPendingSaves.remove(player.getUniqueId());
		if (futures == null) {
			futures = new ArrayList<>();
		} else {
			futures.removeIf(future -> future.isDone());
		}

		/* Advancements */
		/* TODO: Decrease verbosity */
		mLogger.info("Saving advancements data for player=" + player.getName());
		mLogger.finer("Data:" + event.getJsonData());
		String advPath = MonumentaRedisSyncAPI.getRedisAdvancementsPath(player);
		futures.add(RedisAPI.getInstance().async().lpush(advPath, event.getJsonData()));
		futures.add(RedisAPI.getInstance().async().ltrim(advPath, 0, Conf.getHistory()));

		/* Scoreboards */
		/* TODO: Decrease verbosity */
		mLogger.info("Saving scoreboard data for player=" + player.getName());
		long startTime = System.currentTimeMillis();
		String data = ScoreboardUtils.getAsJsonObject(player).toString();
		/* TODO: Decrease verbosity */
		mLogger.info("Scoreboard saving took " + Long.toString(System.currentTimeMillis() - startTime) + " milliseconds on main thread");
		mLogger.finer("Data:" + data);
		String scorePath = MonumentaRedisSyncAPI.getRedisScoresPath(player);
		futures.add(RedisAPI.getInstance().async().lpush(scorePath, data));
		futures.add(RedisAPI.getInstance().async().ltrim(scorePath, 0, Conf.getHistory()));

		/* Don't block - store the pending futures for completion later */
		mPendingSaves.put(player.getUniqueId(), futures);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerDataLoadEvent(PlayerDataLoadEvent event) {
		Player player = event.getPlayer();

		if (Conf.getSavingDisabled()) {
			/* No data saved, no data loaded */
			return;
		}

		/* TODO: Decrease verbosity */
		mLogger.info("Loading data for player=" + player.getName());

		/* Wait until player has finished saving if they just logged out and back in */
		blockingWaitForPlayerToSave(player);

		/* Load the primary shared NBT data */
		byte[] data = RedisAPI.getInstance().syncStringBytes().lindex(MonumentaRedisSyncAPI.getRedisDataPath(player), 0);
		if (data == null) {
			mLogger.warning("No data for player '" + player.getName() + "' - if they are not new, this is a serious error!");
			return;
		}
		mLogger.finer("data: " + b64encode(data));

		/* Load the per-shard data */
		String shardData = RedisAPI.getInstance().sync().hget(MonumentaRedisSyncAPI.getRedisPerShardDataPath(player), Conf.getShard());
		if (shardData == null) {
			/* This is not an error - this will happen whenever a player first visits a new shard */
			mLogger.info("Player '" + player.getName() + "' has never been to this shard before");
		} else {
			mLogger.finer("sharddata: " + shardData);
		}

		try {
			Object nbtTagCompound = mAdapter.retrieveSaveData(player, data, shardData);
			event.setData(nbtTagCompound);
		} catch (IOException ex) {
			mLogger.severe("Failed to load player data: " + ex.toString());
			ex.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerDataSaveEvent(PlayerDataSaveEvent event) {
		event.setCancelled(true);

		if (Conf.getSavingDisabled()) {
			/* No data saved, no data loaded */
			return;
		}

		Player player = event.getPlayer();
		if (isPlayerTransferring(player)) {
			mLogger.fine("Ignoring PlayerDataSaveEvent for player:" + player.getName());
			return;
		}

		/* TODO: Decrease verbosity */
		mLogger.info("Saving data for player=" + player.getName());

		List<RedisFuture<?>> futures = mPendingSaves.remove(player.getUniqueId());
		if (futures == null) {
			futures = new ArrayList<>();
		} else {
			futures.removeIf(future -> future.isDone());
		}

		try {
			/* Grab the return parameters if they were set when starting transfer. If they are null, that's fine too */
			ReturnParams returnParams = mReturnParams.get(player.getUniqueId());
			SaveData data = mAdapter.extractSaveData(player, event.getData(), returnParams);

			mLogger.finer("data: " + b64encode(data.getData()));
			mLogger.finer("sharddata: " + data.getShardData());
			String dataPath = MonumentaRedisSyncAPI.getRedisDataPath(player);
			futures.add(RedisAPI.getInstance().asyncStringBytes().lpush(dataPath, data.getData()));
			futures.add(RedisAPI.getInstance().asyncStringBytes().ltrim(dataPath, 0, Conf.getHistory()));
			futures.add(RedisAPI.getInstance().async().hset(MonumentaRedisSyncAPI.getRedisPerShardDataPath(player), Conf.getShard(), data.getShardData()));
			String histPath = MonumentaRedisSyncAPI.getRedisHistoryPath(player);
			futures.add(RedisAPI.getInstance().async().lpush(histPath, Conf.getShard() + "|" + Long.toString(System.currentTimeMillis()) + "|" + player.getName()));
			futures.add(RedisAPI.getInstance().async().ltrim(histPath, 0, Conf.getHistory()));
		} catch (IOException ex) {
			mLogger.severe("Failed to save player data: " + ex.toString());
			ex.printStackTrace();
		}

		/* Don't block - store the pending futures for completion later */
		mPendingSaves.put(player.getUniqueId(), futures);
	}

	/********************* Transferring Restriction Event Handlers *********************/

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerJoinEvent(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		setPlayerAsNotTransferring(player);

		String nameStr = player.getName();
		String uuidStr = player.getUniqueId().toString();

		Bukkit.getServer().getScheduler().runTaskAsynchronously(MonumentaRedisSync.getInstance(), () -> {
			RedisAPI.getInstance().async().hset("uuid2name", uuidStr, nameStr);
			RedisAPI.getInstance().async().hset("name2uuid", nameStr, uuidStr);
		});
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerQuitEvent(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		if (player.hasMetadata(TRANSFER_UNLOCK_TASK_METAKEY)) {
			BukkitRunnable runnable = (BukkitRunnable) player.getMetadata(TRANSFER_UNLOCK_TASK_METAKEY).get(0).value();
			if (!runnable.isCancelled()) {
				runnable.cancel();
			}
			player.removeMetadata(TRANSFER_UNLOCK_TASK_METAKEY, MonumentaRedisSync.getInstance());
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerInteractEvent(PlayerInteractEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void blockPlaceEvent(BlockPlaceEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerInteractEntityEvent(PlayerInteractEntityEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerArmorStandManipulateEvent(PlayerArmorStandManipulateEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerDropItemEvent(PlayerDropItemEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerSwapHandItemsEvent(PlayerSwapHandItemsEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerFishEvent(PlayerFishEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerItemConsumeEvent(PlayerItemConsumeEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerItemDamageEvent(PlayerItemDamageEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerBedEnterEvent(PlayerBedEnterEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerGameModeChangeEvent(PlayerGameModeChangeEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void blockBreakEvent(BlockBreakEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void entityPickupItemEvent(EntityPickupItemEvent event) {
		cancelEventIfTransferring(event.getEntity(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void inventoryClickEvent(InventoryClickEvent event) {
		cancelEventIfTransferring(event.getWhoClicked(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void inventoryDragEvent(InventoryDragEvent event) {
		cancelEventIfTransferring(event.getWhoClicked(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void inventoryOpenEvent(InventoryOpenEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void inventoryInteractEvent(InventoryInteractEvent event) {
		cancelEventIfTransferring(event.getWhoClicked(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void entityCombustByEntityEvent(EntityCombustByEntityEvent event) {
		cancelEventIfTransferring(event.getEntity(), event);
		cancelEventIfTransferring(event.getCombuster(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void entityDamageByEntityEvent(EntityDamageByEntityEvent event) {
		cancelEventIfTransferring(event.getEntity(), event);
		cancelEventIfTransferring(event.getDamager(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void entityDamageEvent(EntityDamageEvent event) {
		cancelEventIfTransferring(event.getEntity(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void hangingBreakByEntityEvent(HangingBreakByEntityEvent event) {
		cancelEventIfTransferring(event.getRemover(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void projectileLaunchEvent(ProjectileLaunchEvent event) {
		ProjectileSource shooter = event.getEntity().getShooter();
		if (shooter != null && shooter instanceof Player) {
			cancelEventIfTransferring((Player)shooter, event);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void potionSplashEvent(PotionSplashEvent event) {
		event.getAffectedEntities().removeIf(entity -> (entity instanceof Player && isPlayerTransferring((Player)entity)));
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void areaEffectCloudApplyEvent(AreaEffectCloudApplyEvent event) {
		event.getAffectedEntities().removeIf(entity -> (entity instanceof Player && isPlayerTransferring((Player)entity)));
	}

	/********************* Private Utility Methods *********************/

	private void cancelEventIfTransferring(Entity entity, Cancellable event) {
		if (entity != null && entity instanceof Player && isPlayerTransferring((Player)entity)) {
			event.setCancelled(true);
		}
	}

	private static String b64encode(byte[] data) {
		return Base64.getEncoder().encodeToString(data);
	}
}
