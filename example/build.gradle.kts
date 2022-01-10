plugins {
    id("com.playmonumenta.redissync.java-conventions")
}

dependencies {
    compileOnly(project(":redissync"))
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
}

group = "com.playmonumenta"
description = "redissync-example"
version = rootProject.version
