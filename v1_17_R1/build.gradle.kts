/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    id("com.playmonumenta.redissync.java-conventions")
}

dependencies {
    compileOnly(project(":adapterapi"))
    compileOnly("io.papermc.paper:paper:1.17.1-R0.1-SNAPSHOT")
}

description = "v1_17_R1"
version = rootProject.version
