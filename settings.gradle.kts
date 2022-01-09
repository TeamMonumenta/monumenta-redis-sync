rootProject.name = "parent"
include(":adapterapi")
include(":redissync-example")
include(":redissync")
include(":v1_15_R1")
include(":v1_17_R1")
include(":v1_16_R3")
project(":redissync-example").projectDir = file("example")
project(":redissync").projectDir = file("plugin")
