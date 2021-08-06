package com.playmonumenta.redissync;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.playmonumenta.redissync.adapters.VersionAdapter;
import com.playmonumenta.redissync.commands.ChangeLogLevel;
import com.playmonumenta.redissync.commands.PlayerHistory;
import com.playmonumenta.redissync.commands.PlayerLoadFromPlayer;
import com.playmonumenta.redissync.commands.PlayerRollback;
import com.playmonumenta.redissync.commands.RboardCommand;
import com.playmonumenta.redissync.commands.Stash;
import com.playmonumenta.redissync.commands.TransferServer;
import com.playmonumenta.redissync.commands.UpgradeAllPlayers;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MonumentaRedisSync extends JavaPlugin {
	private static MonumentaRedisSync INSTANCE = null;
	private RedisAPI mRedisAPI = null;
	private VersionAdapter mVersionAdapter = null;
	private CustomLogger mLogger = null;

	private void loadVersionAdapter() {
		/* From https://github.com/mbax/AbstractionExamplePlugin */

		String packageName = this.getServer().getClass().getPackage().getName();
		String version = packageName.substring(packageName.lastIndexOf('.') + 1);

		try {
			final Class<?> clazz = Class.forName("com.playmonumenta.redissync.adapters.VersionAdapter_" + version);
			// Check if we have a valid adapter class at that location.
			if (VersionAdapter.class.isAssignableFrom(clazz)) {
				mVersionAdapter = (VersionAdapter) clazz.getConstructor(Logger.class).newInstance(this.getLogger());
			}
		} catch (final Exception e) {
			e.printStackTrace();
			getLogger().severe("Server version " + version + " is not supported!");
			return;
		}
		getLogger().info("Loading support for " + version);
	}

	@Override
	public void onLoad() {
		loadVersionAdapter();

		/*
		 * CommandAPI commands which register directly and are usable in functions
		 *
		 * These need to register immediately on load to prevent function loading errors
		 */
		TransferServer.register();
		Stash.register();
		PlayerHistory.register(this);
		PlayerRollback.register();
		PlayerLoadFromPlayer.register();
		UpgradeAllPlayers.register(this);
		ChangeLogLevel.register(this);
		RboardCommand.register(this);
	}

	@Override
	public void onEnable() {
		/* Refuse to enable without a version adapter */
		if (mVersionAdapter == null) {
			this.setEnabled(false);
			return;
		}

		/* Needed to tell Netty where it moved to */
		System.setProperty("com.playmonumenta.redissync.internal.io.netty", "com.playmonumenta.redissync.internal");

		INSTANCE = this;
		loadConfig();
		mRedisAPI = new RedisAPI(Conf.getHost(), Conf.getPort());
		getServer().getPluginManager().registerEvents(new DataEventListener(this.getLogger(), mVersionAdapter), this);
		getServer().getPluginManager().registerEvents(new ScoreboardCleanupListener(this, this.getLogger(), mVersionAdapter), this);
		if (Conf.getTicksPerPlayerAutosave() > 0) {
			getServer().getPluginManager().registerEvents(new AutoSaveListener(this, mVersionAdapter), this);
		}

		if (getServer().getPluginManager().isPluginEnabled("MonumentaNetworkRelay")) {
			try {
				getServer().getPluginManager().registerEvents(new NetworkRelayIntegration(this.getLogger()), this);
			} catch (Exception ex) {
				getLogger().severe("Failed to enable MonumentaNetworkRelay integration: " + ex.getMessage());
			}
		}

		this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
	}

	@Override
	public void onDisable() {
		INSTANCE = null;
		if (mRedisAPI != null) {
			mRedisAPI.shutdown();
		}
		mRedisAPI = null;
		getServer().getScheduler().cancelTasks(this);
	}

	protected static MonumentaRedisSync getInstance() {
		return INSTANCE;
	}

	public VersionAdapter getVersionAdapter() {
		return mVersionAdapter;
	}

	private void loadConfig() {
		File configFile = new File(this.getDataFolder(), "config.yml");
		/* TODO: Default file if not exist */
		FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
		String host = config.getString("redis_host", "redis");
		int port = config.getInt("redis_port", 6379);
		String domain = config.getString("server_domain", "default_domain");

		/* Get default shard name from network relay if enabled */
		String shard = NetworkRelayIntegration.getShardName();
		if (shard == null) {
			shard = "default_shard";
		}
		shard = config.getString("shard_name", shard);

		World defaultWorld = Bukkit.getWorlds().get(0);
		String shardDataName = config.getString("shard_data_name", defaultWorld.getUID().toString() + ":" + defaultWorld.getName());

		int history = config.getInt("history_amount", 20);
		int ticksPerPlayerAutosave = config.getInt("ticksPerPlayerAutosave", 6060);
		boolean savingDisabled = config.getBoolean("saving_disabled", false);
		boolean scoreboardCleanupEnabled = config.getBoolean("scoreboard_cleanup_enabled", true);

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

		new Conf(host, port, domain, shard, shardDataName, history, ticksPerPlayerAutosave, savingDisabled, scoreboardCleanupEnabled);
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
