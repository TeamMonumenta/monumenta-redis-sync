plugins {
    id("com.playmonumenta.redissync.java-conventions")
}

dependencies {
    compileOnly(project(":redissync"))
    compileOnly("com.destroystokyo.paper:paper-api:1.15.2-R0.1-SNAPSHOT")
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
}

group = "com.playmonumenta"
description = "redissync-example"
version = rootProject.version
