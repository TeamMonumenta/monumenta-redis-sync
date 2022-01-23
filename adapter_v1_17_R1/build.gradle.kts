plugins {
    id("com.playmonumenta.redissync.java-conventions")
}

dependencies {
    compileOnly(project(":adapter_api"))
    compileOnly("io.papermc.paper:paper:1.17.1-R0.1-SNAPSHOT")
}

description = "v1_17_R1"
version = rootProject.version
