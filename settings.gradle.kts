rootProject.name = "parent"
include(":adapter_api")
include(":adapter_v1_16_R3")
include(":adapter_v1_18_R1")
include(":adapter_v1_18_R2")
include(":redissync-example")
include(":redissync")
project(":redissync-example").projectDir = file("example")
project(":redissync").projectDir = file("plugin")

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://papermc.io/repo/repository/maven-public/")
  }
}
