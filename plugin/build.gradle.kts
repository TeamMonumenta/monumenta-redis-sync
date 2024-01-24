import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	id("com.github.johnrengelman.shadow") version "7.1.2"
	id("com.playmonumenta.redissync.java-conventions")
	id("net.minecrell.plugin-yml.bukkit") version "0.5.1" // Generates plugin.yml
	id("net.minecrell.plugin-yml.bungee") version "0.5.1" // Generates bungee.yml
	id("java")
	id("net.ltgt.errorprone") version "3.1.0"
	id("net.ltgt.nullaway") version "1.6.0"
	id("com.playmonumenta.deployment") version "1.+"
}

repositories {
	maven("https://repo.codemc.org/repository/maven-public/")
	maven("https://maven.playmonumenta.com/releases")
}

dependencies {
	implementation(project(":adapter_api"))
	implementation(project(":adapter_v1_18_R2", "reobf"))
	implementation(project(":adapter_v1_19_R2", "reobf"))
	implementation(project(":adapter_v1_19_R3", "reobf"))
	implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
	compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
	compileOnly("com.playmonumenta:monumenta-network-relay:2.7")
	compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
	compileOnly("dev.jorel:commandapi-bukkit-core:9.4.1")
	errorprone("com.google.errorprone:error_prone_core:2.29.1")
	errorprone("com.uber.nullaway:nullaway:0.9.5")

	// velocity depenedencies
  compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
  annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
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
		check(
			"FutureReturnValueIgnored",
			CheckSeverity.OFF
		) // This one is dumb and doesn't let you check return values with .whenComplete()
		check(
			"ImmutableEnumChecker",
			CheckSeverity.OFF
		) // Would like to turn this on but we'd have to annotate a bunch of base classes
		check(
			"LockNotBeforeTry",
			CheckSeverity.OFF
		) // Very few locks in our code, those that we have are simple and refactoring like this would be ugly
		check("StaticAssignmentInConstructor", CheckSeverity.OFF) // We have tons of these on purpose
		check("StringSplitter", CheckSeverity.OFF) // We have a lot of string splits too which are fine for this use
		check(
			"MutablePublicArray",
			CheckSeverity.OFF
		) // These are bad practice but annoying to refactor and low risk of actual bugs
		check("InlineMeSuggester", CheckSeverity.OFF) // This seems way overkill
	}
}

java {
	withJavadocJar()
	withSourcesJar()
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			artifact(tasks.shadowJar)
			artifact(tasks["javadocJar"])
			artifact(tasks["sourcesJar"])
		}
	}
	repositories {
		maven {
			name = "MonumentaMaven"
			url = when (version.toString().endsWith("SNAPSHOT")) {
				true -> uri("https://maven.playmonumenta.com/snapshots")
				false -> uri("https://maven.playmonumenta.com/releases")
			}

			credentials {
				username = System.getenv("USERNAME")
				password = System.getenv("TOKEN")
			}
		}
	}
}

ssh.easySetup(tasks.shadowJar.get(), "MonumentaRedisSync")
