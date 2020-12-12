package com.playmonumenta.redissync;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.playmonumenta.redissync.adapters.VersionAdapter;
import com.playmonumenta.redissync.commands.PlayerHistory;
import com.playmonumenta.redissync.commands.PlayerLoadFromPlayer;
import com.playmonumenta.redissync.commands.PlayerRollback;
import com.playmonumenta.redissync.commands.Stash;
import com.playmonumenta.redissync.commands.TransferServer;
import com.playmonumenta.redissync.commands.UpgradeAllPlayers;

public class MonumentaRedisSync extends JavaPlugin {
	private static MonumentaRedisSync INSTANCE = null;
	private RedisAPI mRedisAPI = null;
	private VersionAdapter mVersionAdapter = null;

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
		TransferServer.register(this);
		Stash.register();
		PlayerHistory.register(this);
		PlayerRollback.register();
		PlayerLoadFromPlayer.register();
		UpgradeAllPlayers.register(this);
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

		if (Bukkit.getPluginManager().isPluginEnabled("OpenInv")) {
			getServer().getPluginManager().registerEvents(new OpenInvIntegration(), this);
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
		String shard = config.getString("shard_name", "default_shard");
		int history = config.getInt("history_amount", 20);
		int ticksPerPlayerAutosave = config.getInt("ticksPerPlayerAutosave", 6060);
		boolean savingDisabled = config.getBoolean("saving_disabled", false);
		boolean scoreboardCleanupEnabled = config.getBoolean("scoreboard_cleanup_enabled", true);
		new Conf(host, port, domain, shard, history, ticksPerPlayerAutosave, savingDisabled, scoreboardCleanupEnabled);
	}
}
