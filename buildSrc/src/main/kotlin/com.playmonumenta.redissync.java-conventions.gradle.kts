plugins {
    `java-library`
    `maven-publish`
    checkstyle
    pmd
}

repositories {
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.maven.apache.org/maven2/")
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    // NBT API
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://maven.playmonumenta.com/")
}

group = "com.playmonumenta.redissync"
java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

pmd {
    isConsoleOutput = true
    toolVersion = "6.41.0"
    ruleSets = listOf("$rootDir/pmd-ruleset.xml")
    setIgnoreFailures(true)
}
