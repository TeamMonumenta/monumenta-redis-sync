plugins {
    id("com.playmonumenta.redissync.java-conventions")
}

dependencies {
    compileOnly(project(":adapter_api"))
    compileOnly("com.destroystokyo.paper:paper:1.16.5-R0.1-SNAPSHOT")
}

description = "v1_16_R3"
version = rootProject.version
