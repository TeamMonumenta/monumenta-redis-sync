import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    id("com.playmonumenta.redissync.java-conventions")
    id("net.minecrell.plugin-yml.bukkit") version "0.5.1" // Generates plugin.yml
    id("net.minecrell.plugin-yml.bungee") version "0.5.1" // Generates bungee.yml
}

dependencies {
    implementation(project(":adapterapi"))
    implementation(project(":v1_15_R1"))
    implementation(project(":v1_16_R3"))
    implementation(project(":v1_17_R1"))
    implementation("io.lettuce:lettuce-core:5.3.5.RELEASE")
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
    compileOnly("com.playmonumenta:monumenta-network-relay:1.0")
    compileOnly("com.destroystokyo.paper:paper:1.15.2-R0.1-SNAPSHOT")
    compileOnly("dev.jorel.CommandAPI:commandapi-core:6.0.0")
}

group = "com.playmonumenta"
description = "redissync"
version = rootProject.version

// Configure plugin.yml generation
bukkit {
    load = BukkitPluginDescription.PluginLoadOrder.STARTUP
    main = "com.playmonumenta.redissync.MonumentaRedisSync"
    apiVersion = "1.15"
    authors = listOf("Combustible")
    depend = listOf("CommandAPI")
    softDepend = listOf("MonumentaNetworkRelay")
}

// Configure bungee.yml generation
bungee {
    main = "com.playmonumenta.redissync.MonumentaRedisSyncBungee"
    author = "Combustible"
}
