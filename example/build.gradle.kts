import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	id("com.playmonumenta.gradle-config") version "1.3+"
}

dependencies {
	compileOnly(project(":redissync"))
	compileOnly(libs.commandapi)
	compileOnly(libs.velocity)
	compileOnly(libs.lettuce)
}

group = "com.playmonumenta"
description = "redissync-example"
version = rootProject.version

monumenta {
	name("redissync-example")
	paper(
		"com.playmonumenta.redissync.MonumentaRedisSyncExample", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.19",
		depends = listOf("CommandAPI", "MonumentaRedisSync"),
	)

	waterfall("com.playmonumenta.redissync.MonumentaRedisSyncExampleBungee", "1.19")
}
