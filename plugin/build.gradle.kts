import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
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
    id("java")
    id("net.ltgt.errorprone") version "2.0.2"
    id("net.ltgt.nullaway") version "1.3.0"
}

dependencies {
    implementation(project(":adapter_api"))
    implementation(project(":adapter_v1_18_R2", "reobf"))
    implementation(project(":adapter_v1_19_R2", "reobf"))
    implementation("io.lettuce:lettuce-core:5.3.5.RELEASE")
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
    compileOnly("com.playmonumenta:monumenta-network-relay:1.0")
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    implementation("dev.jorel.CommandAPI:commandapi-shade:8.7.0")
    errorprone("com.google.errorprone:error_prone_core:2.10.0")
    errorprone("com.uber.nullaway:nullaway:0.9.5")
}

val basicssh = remotes.create("basicssh") {
    host = "admin-eu.playmonumenta.com"
    port = 8822
    user = "epic"
    knownHosts = allowAnyHosts
    agent = System.getenv("IDENTITY_FILE") == null
    identity = if (System.getenv("IDENTITY_FILE") == null) null else file(System.getenv("IDENTITY_FILE"))
}

val adminssh = remotes.create("adminssh") {
    host = "admin-eu.playmonumenta.com"
    port = 9922
    user = "epic"
    knownHosts = allowAnyHosts
    agent = System.getenv("IDENTITY_FILE") == null
    identity = if (System.getenv("IDENTITY_FILE") == null) null else file(System.getenv("IDENTITY_FILE"))
}

group = "com.playmonumenta"
description = "System for storing player data in a Redis database"
version = rootProject.version

// Configure plugin.yml generation
bukkit {
    load = BukkitPluginDescription.PluginLoadOrder.POSTWORLD
    main = "com.playmonumenta.redissync.MonumentaRedisSync"
    apiVersion = "1.18"
    name = "MonumentaRedisSync"
    authors = listOf("Combustible")
    depend = listOf()
    softDepend = listOf("MonumentaNetworkRelay")
}

// Configure bungee.yml generation
bungee {
    name = "MonumentaRedisSync"
    main = "com.playmonumenta.redissync.MonumentaRedisSyncBungee"
    author = "Combustible"
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xmaxwarns")
    options.compilerArgs.add("10000")

    options.compilerArgs.add("-Xlint:deprecation")

    options.errorprone {
        option("NullAway:AnnotatedPackages", "com.playmonumenta")

        allErrorsAsWarnings.set(true)

        /*** Disabled checks ***/
        // These we almost certainly don't want
        check("CatchAndPrintStackTrace", CheckSeverity.OFF) // This is the primary way a lot of exceptions are handled
        check("FutureReturnValueIgnored", CheckSeverity.OFF) // This one is dumb and doesn't let you check return values with .whenComplete()
        check("ImmutableEnumChecker", CheckSeverity.OFF) // Would like to turn this on but we'd have to annotate a bunch of base classes
        check("LockNotBeforeTry", CheckSeverity.OFF) // Very few locks in our code, those that we have are simple and refactoring like this would be ugly
        check("StaticAssignmentInConstructor", CheckSeverity.OFF) // We have tons of these on purpose
        check("StringSplitter", CheckSeverity.OFF) // We have a lot of string splits too which are fine for this use
        check("MutablePublicArray", CheckSeverity.OFF) // These are bad practice but annoying to refactor and low risk of actual bugs
        check("InlineMeSuggester", CheckSeverity.OFF) // This seems way overkill
    }
}


// Relocation / shading
tasks {
    shadowJar {
       relocate("org.reactivestreams", "com.playmonumenta.redissync.internal.org.reactivestreams")
       relocate("reactor.adapter", "com.playmonumenta.redissync.internal.reactor.adapter")
       relocate("reactor.core", "com.playmonumenta.redissync.internal.reactor.core")
       relocate("reactor.util", "com.playmonumenta.redissync.internal.reactor.util")
       relocate("dev.jorel.commandapi", "com.playmonumenta.redissync.internal.dev.jorel.commandapi")
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
                execute("cd /home/epic/dev1_shard_plugins && rm -f redissync*.jar")
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
                execute("cd /home/epic/dev2_shard_plugins && rm -f redissync*.jar")
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
                execute("cd /home/epic/dev3_shard_plugins && rm -f redissync*.jar")
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
                put(shadowJar.archiveFile.get().getAsFile(), "/home/epic/play/m13/server_config/plugins")
                put(shadowJar.archiveFile.get().getAsFile(), "/home/epic/play/m14/server_config/plugins")
                put(shadowJar.archiveFile.get().getAsFile(), "/home/epic/play/m15/server_config/plugins")
                execute("cd /home/epic/play/m8/server_config/plugins && rm -f MonumentaRedisSync.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaRedisSync.jar")
                execute("cd /home/epic/play/m11/server_config/plugins && rm -f MonumentaRedisSync.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaRedisSync.jar")
                execute("cd /home/epic/play/m13/server_config/plugins && rm -f MonumentaRedisSync.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaRedisSync.jar")
                execute("cd /home/epic/play/m14/server_config/plugins && rm -f MonumentaRedisSync.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaRedisSync.jar")
                execute("cd /home/epic/play/m15/server_config/plugins && rm -f MonumentaRedisSync.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaRedisSync.jar")
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
