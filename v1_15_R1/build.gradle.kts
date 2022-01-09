plugins {
    id("com.playmonumenta.redissync.java-conventions")
}

dependencies {
    compileOnly(project(":adapterapi"))
    compileOnly("com.destroystokyo.paper:paper:1.15.2-R0.1-SNAPSHOT")
}

description = "v1_15_R1"
version = rootProject.version
