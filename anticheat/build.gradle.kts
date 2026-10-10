plugins {
    java
}

group = "ru.strazh"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

// Обычная сборка берёт Paper API и PacketEvents из их Maven-репозиториев.
// Для офлайн-сборки (без доступа к repo.papermc.io / repo.codemc.io) можно
// передать -PlocalLibs=<папка с paper-api.jar и packetevents.jar>.
val localLibs: String? = providers.gradleProperty("localLibs").orNull

repositories {
    if (localLibs != null) {
        maven("https://maven-central.storage-download.googleapis.com/maven2")
    }
    mavenCentral()
    if (localLibs == null) {
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
    }
}

dependencies {
    if (localLibs != null) {
        compileOnly(fileTree(localLibs) { include("*.jar") })
        val adventure = "4.26.1"
        listOf(
            "com.google.guava:guava:33.3.1-jre",
            "com.google.code.gson:gson:2.11.0",
            "org.yaml:snakeyaml:2.2",
            "org.joml:joml:1.10.8",
            "it.unimi.dsi:fastutil:8.5.15",
            "org.apache.logging.log4j:log4j-api:2.24.1",
            "org.slf4j:slf4j-api:2.0.16",
            "net.md-5:bungeecord-chat:1.21-R0.1",
            "net.kyori:adventure-api:$adventure",
            "net.kyori:adventure-key:$adventure",
            "net.kyori:adventure-nbt:$adventure",
            "net.kyori:adventure-text-minimessage:$adventure",
            "net.kyori:adventure-text-serializer-legacy:$adventure",
            "net.kyori:adventure-text-serializer-plain:$adventure",
            "net.kyori:adventure-text-serializer-gson:$adventure",
            "net.kyori:examination-api:1.3.0",
            "org.jspecify:jspecify:1.0.0",
            "io.netty:netty-all:4.1.72.Final",
        ).forEach {
            compileOnly(it)
            testImplementation(it)
        }
        testImplementation(fileTree(localLibs) { include("*.jar") })
    } else {
        compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
        compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
        testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
        testImplementation("com.github.retrooper:packetevents-spigot:2.14.0")
    }
    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    test {
        useJUnitPlatform()
    }
    jar {
        archiveFileName.set("StrazhAC-${project.version}.jar")
    }
}
