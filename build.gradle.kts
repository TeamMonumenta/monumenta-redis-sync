import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	id("com.playmonumenta.gradle-config") version "2.+"
}

val mixinapi = libs.mixinapi


tasks {
    javadoc {
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    }
}

monumenta {
	name("MonumentaRedisSync")
	pluginProject(":redissync")
	paper(
		"com.playmonumenta.redissync.MonumentaRedisSync", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.20",
		depends = listOf("CommandAPI"),
		softDepends = listOf("MonumentaNetworkRelay")
	)

	waterfall("com.playmonumenta.redissync.MonumentaRedisSyncBungee", "1.20")

	versionAdapterApi("adapter_api", paper = "1.18.2")
	versionAdapter("adapter_v1_18_R2", "1.18.2")
	versionAdapter("adapter_v1_19_R2", "1.19.3")
	versionAdapter("adapter_v1_19_R3", "1.19.4")
	versionAdapter("adapter_v1_20_R3", "1.20.4") {
		dependencies {
			compileOnly(mixinapi)
		}
	}
	javaSimple(":redissync-example")
}
