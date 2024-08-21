import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    java
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.playmonumenta.redissync.java-conventions")
    id("net.minecrell.plugin-yml.bukkit") version "0.5.1" // Generates plugin.yml
    id("net.minecrell.plugin-yml.bungee") version "0.5.1" // Generates bungee.yml
    id("com.playmonumenta.deployment") version "1.+"
}

dependencies {
    compileOnly(project(":redissync"))
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("dev.jorel:commandapi-bukkit-core:9.5.3")
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
    compileOnly("io.lettuce:lettuce-core:6.3.2.RELEASE")
}

group = "com.playmonumenta"
description = "redissync-example"
version = rootProject.version

// Configure plugin.yml generation
bukkit {
	load = BukkitPluginDescription.PluginLoadOrder.POSTWORLD
	main = "com.playmonumenta.redissync.example.MonumentaRedisSyncExample"
	apiVersion = "1.19"
	name = "redissync-example"
	author = "The Monumenta Team"
	depend = listOf("CommandAPI", "MonumentaRedisSync")
}

// Configure bungee.yml generation
bungee {
	name = "redissync-example-bungee"
	main = "com.playmonumenta.redissync.example.MonumentaRedisSyncExampleBungee"
	author = "The Monumenta Team"
}

ssh.easySetup(tasks.named<ShadowJar>("shadowJar").get(), "redissync-example")