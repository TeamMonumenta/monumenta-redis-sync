import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.playmonumenta.redissync.java-conventions")
    id("net.minecrell.plugin-yml.bukkit") version "0.5.1" // Generates plugin.yml
    id("net.minecrell.plugin-yml.bungee") version "0.5.1" // Generates bungee.yml
    id("java")
    id("net.ltgt.errorprone") version "2.0.2"
    id("net.ltgt.nullaway") version "1.3.0"
	id("com.playmonumenta.deployment") version "1.0"
}

dependencies {
    implementation(project(":adapter_api"))
    implementation(project(":adapter_v1_18_R2", "reobf"))
    implementation(project(":adapter_v1_19_R2", "reobf"))
    implementation(project(":adapter_v1_19_R3", "reobf"))
    implementation("io.lettuce:lettuce-core:5.3.5.RELEASE")
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
    compileOnly("com.playmonumenta:monumenta-network-relay:1.0")
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    compileOnly("dev.jorel.CommandAPI:commandapi-core:8.7.0")
    errorprone("com.google.errorprone:error_prone_core:2.10.0")
    errorprone("com.uber.nullaway:nullaway:0.9.5")
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
    depend = listOf("CommandAPI")
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

ssh.easySetup(tasks.named<ShadowJar>("shadowJar").get(), "MonumentaRedisSync")
