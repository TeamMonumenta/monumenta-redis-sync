package com.playmonumenta.redissync.example;

import com.playmonumenta.redissync.example.redislockexamples.RedisLockExampleCommands;
import org.bukkit.plugin.java.JavaPlugin;

/* This is an example Paper plugin that uses MonumentaRedisSync as a dependency */
public class MonumentaRedisSyncExample extends JavaPlugin {
	@Override
	public void onLoad() {
		RedisLockExampleCommands.register(this);
	}

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(new ExampleServerListener(this), this);
	}
}
