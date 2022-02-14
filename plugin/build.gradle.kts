import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.hidetake.groovy.ssh.core.Remote
import org.hidetake.groovy.ssh.core.RunHandler
import org.hidetake.groovy.ssh.core.Service
import org.hidetake.groovy.ssh.session.SessionHandler

plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.playmonumenta.redissync.java-conventions")
    id("net.minecrell.plugin-yml.bukkit") version "0.5.1" // Generates plugin.yml
    id("net.minecrell.plugin-yml.bungee") version "0.5.1" // Generates bungee.yml
    id("org.hidetake.ssh") version "2.10.1"
}

dependencies {
    implementation(project(":adapter_api"))
    implementation(project(":adapter_v1_16_R3"))
    implementation(project(":adapter_v1_17_R1"))
    implementation(project(":adapter_v1_18_R1", "reobf"))
    implementation("io.lettuce:lettuce-core:5.3.5.RELEASE")
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
    compileOnly("com.playmonumenta:monumenta-network-relay:1.0")
    compileOnly("com.destroystokyo.paper:paper:1.16.5-R0.1-SNAPSHOT")
    compileOnly("dev.jorel.CommandAPI:commandapi-core:6.0.0")
}

val basicssh = remotes.create("basicssh") {
    host = "admin-eu.playmonumenta.com"
    port = 8822
    user = "epic"
    agent = true
    knownHosts = allowAnyHosts
}

val adminssh = remotes.create("adminssh") {
    host = "admin-eu.playmonumenta.com"
    port = 9922
    user = "epic"
    agent = true
    knownHosts = allowAnyHosts
}

group = "com.playmonumenta"
description = "System for storing player data in a Redis database"
version = rootProject.version

// Configure plugin.yml generation
bukkit {
    load = BukkitPluginDescription.PluginLoadOrder.POSTWORLD
    main = "com.playmonumenta.redissync.MonumentaRedisSync"
    apiVersion = "1.16"
    name = "MonumentaRedisSync"
    authors = listOf("Combustible")
    depend = listOf("CommandAPI")
    softDepend = listOf("MonumentaNetworkRelay")
}

// Configure bungee.yml generation
bungee {
    name = "MonumentaRedisSync"
    main = "com.playmonumenta.redissync.MonumentaRedisSyncBungee"
    author = "Combustible"
}

// Relocation / shading
tasks {
    shadowJar {
       relocate("org.reactivestreams", "com.playmonumenta.redissync.internal.org.reactivestreams")
       relocate("reactor.adapter", "com.playmonumenta.redissync.internal.reactor.adapter")
       relocate("reactor.core", "com.playmonumenta.redissync.internal.reactor.core")
       relocate("reactor.util", "com.playmonumenta.redissync.internal.reactor.util")
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        project.shadow.component(this)
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/TeamMonumenta/monumenta-redis-sync")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

tasks.create("dev1-deploy") {
    val shadowJar by tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    doLast {
        ssh.runSessions {
            session(basicssh) {
                val dstFile = shadowJar.archiveFileName.get().replace("Monumenta", "")
                execute("cd /home/epic/dev1_shard_plugins && rm -f RedisSync*.jar")
                execute("cd /home/epic/dev4_shard_plugins && rm -f redissync*.jar")
                put(shadowJar.archiveFile.get().getAsFile(), File("/home/epic/dev1_shard_plugins", dstFile))
            }
        }
    }
}

tasks.create("dev2-deploy") {
    val shadowJar by tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    doLast {
        ssh.runSessions {
            session(basicssh) {
                val dstFile = shadowJar.archiveFileName.get().replace("Monumenta", "")
                execute("cd /home/epic/dev2_shard_plugins && rm -f RedisSync*.jar")
                execute("cd /home/epic/dev4_shard_plugins && rm -f redissync*.jar")
                put(shadowJar.archiveFile.get().getAsFile(), File("/home/epic/dev2_shard_plugins", dstFile))
            }
        }
    }
}

tasks.create("dev3-deploy") {
    val shadowJar by tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    doLast {
        ssh.runSessions {
            session(basicssh) {
                val dstFile = shadowJar.archiveFileName.get().replace("Monumenta", "")
                execute("cd /home/epic/dev3_shard_plugins && rm -f RedisSync*.jar")
                execute("cd /home/epic/dev4_shard_plugins && rm -f redissync*.jar")
                put(shadowJar.archiveFile.get().getAsFile(), File("/home/epic/dev3_shard_plugins", dstFile))
            }
        }
    }
}

tasks.create("dev4-deploy") {
    val shadowJar by tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    doLast {
        ssh.runSessions {
            session(basicssh) {
                val dstFile = shadowJar.archiveFileName.get().replace("Monumenta", "")
                execute("cd /home/epic/dev4_shard_plugins && rm -f RedisSync*.jar")
                execute("cd /home/epic/dev4_shard_plugins && rm -f redissync*.jar")
                put(shadowJar.archiveFile.get().getAsFile(), File("/home/epic/dev4_shard_plugins", dstFile))
            }
        }
    }
}

tasks.create("stage-deploy") {
    val shadowJar by tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    doLast {
        ssh.runSessions {
            session(basicssh) {
                put(shadowJar.archiveFile.get().getAsFile(), "/home/epic/stage/m12/server_config/plugins")
                execute("cd /home/epic/stage/m12/server_config/plugins && rm -f MonumentaRedisSync.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaRedisSync.jar")
            }
        }
    }
}

tasks.create("build-deploy") {
    val shadowJar by tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    doLast {
        ssh.runSessions {
            session(adminssh) {
                put(shadowJar.archiveFile.get().getAsFile(), "/home/epic/project_epic/server_config/plugins")
                execute("cd /home/epic/project_epic/server_config/plugins && rm -f MonumentaRedisSync.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaRedisSync.jar")
            }
        }
    }
}

tasks.create("play-deploy") {
    val shadowJar by tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    doLast {
        ssh.runSessions {
            session(adminssh) {
                put(shadowJar.archiveFile.get().getAsFile(), "/home/epic/play/m8/server_config/plugins")
                put(shadowJar.archiveFile.get().getAsFile(), "/home/epic/play/m11/server_config/plugins")
                execute("cd /home/epic/play/m8/server_config/plugins && rm -f MonumentaRedisSync.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaRedisSync.jar")
                execute("cd /home/epic/play/m8/server_config/plugins && rm -f MonumentaRedisSync.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaRedisSync.jar")
            }
        }
    }
}

fun Service.runSessions(action: RunHandler.() -> Unit) =
    run(delegateClosureOf(action))

fun RunHandler.session(vararg remotes: Remote, action: SessionHandler.() -> Unit) =
    session(*remotes, delegateClosureOf(action))

fun SessionHandler.put(from: Any, into: Any) =
    put(hashMapOf("from" to from, "into" to into))
