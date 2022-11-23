plugins {
    id("com.palantir.git-version") version "0.12.2"
}

description = "monumenta-redis-sync"
val gitVersion: groovy.lang.Closure<String> by extra
version = gitVersion()
