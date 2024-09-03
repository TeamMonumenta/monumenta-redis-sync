import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	id("com.playmonumenta.gradle-config") version "1.+"
}

monumenta {
	name("MonumentaRedisSync")
	pluginProject(":redissync")
	paper(
		"com.playmonumenta.redissync.MonumentaRedisSync", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.18",
		depends = listOf("CommandAPI"),
		softDepends = listOf("MonumentaNetworkRelay")
	)

	waterfall("com.playmonumenta.redissync.MonumentaRedisSyncBungee", "1.18")

	versionAdapterApi("adapter_api", paper = "1.18.2")
	versionAdapter("adapter_v1_18_R2", "1.18.2")
	versionAdapter("adapter_v1_19_R2", "1.19.3")
	versionAdapter("adapter_v1_19_R3", "1.19.4")
}
