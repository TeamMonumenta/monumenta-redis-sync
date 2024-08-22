rootProject.name = "monumenta-redis-sync"
include(":adapter_api")
include(":adapter_v1_18_R2")
include(":adapter_v1_19_R2")
include(":adapter_v1_19_R3")
include(":redissync-example")
include(":redissync")
project(":redissync-example").projectDir = file("example")
project(":redissync").projectDir = file("plugin")

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
		maven("https://maven.playmonumenta.com/releases")
  }
}
