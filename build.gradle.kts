plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "com.blockworlds"
version = "1.0.0"
description = "EQ2-style collectibles system for Paper"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")  // For latest MockBukkit snapshots
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // PlaceholderAPI for placeholder integration (optional soft-depend)
    compileOnly("me.clip:placeholderapi:2.11.7")

    // Database connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")

    // bStats for community metrics
    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("com.mysql:mysql-connector-j:9.1.0") {
        exclude(group = "com.google.protobuf") // X DevAPI not needed
    }

    // Testing - Use Paper 1.21.1 for MockBukkit compatibility
    // MockBukkit v1.21 is built against 1.21.1-R0.1-SNAPSHOT
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching(listOf("paper-plugin.yml", "plugin.yml")) {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        mergeServiceFiles()

        // Relocate dependencies to avoid conflicts with other plugins
        relocate("com.zaxxer.hikari", "com.blockworlds.collections.lib.hikari")
        relocate("org.sqlite", "com.blockworlds.collections.lib.sqlite")
        relocate("com.mysql", "com.blockworlds.collections.lib.mysql")
        relocate("org.bstats", "com.blockworlds.collections.lib.bstats")
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("1.21.4")
    }
}
