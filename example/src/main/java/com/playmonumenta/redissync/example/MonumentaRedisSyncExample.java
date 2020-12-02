package com.playmonumenta.redissync.example;

import org.bukkit.plugin.java.JavaPlugin;

/* This is an example Paper plugin that uses MonumentaRedisSync as a dependency */
public class MonumentaRedisSyncExample extends JavaPlugin {
	private ExampleServerListener mServerListener = null;

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(new ExampleServerListener(this), this);
	}

	@Override
	public void onDisable() {
		if (mServerListener != null) {
			mServerListener.saveAllAndWaitForCompletion();
		}
	}
}
