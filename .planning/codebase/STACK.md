# Technology Stack

**Analysis Date:** 2026-01-20

## Languages

**Primary:**
- Java 21 - All plugin source code

**Secondary:**
- Kotlin DSL - Gradle build configuration (`build.gradle.kts`, `settings.gradle.kts`)
- YAML - Configuration files, collection definitions, zone definitions

## Runtime

**Environment:**
- Java 21 (JDK toolchain enforced via Gradle)
- Paper 1.21.4 (Minecraft server platform)
- Folia-compatible (declared in `paper-plugin.yml`)

**Package Manager:**
- Gradle 8.14.3
- Wrapper included (`gradlew`, `gradlew.bat`)
- Lockfile: Not present (standard for Gradle)

## Frameworks

**Core:**
- Paper API 1.21.4-R0.1-SNAPSHOT - Minecraft server API (compile-only)
- Adventure API - Text/component handling (provided by Paper at runtime)
- MiniMessage - Rich text formatting (provided by Paper at runtime)
- Brigadier - Command framework (via Paper lifecycle events)

**Testing:**
- JUnit Jupiter 5.11.0 - Test framework
- MockBukkit 4.14.0 (v1.21) - Paper server mocking
- Note: Tests run against Paper API 1.21.1 for MockBukkit compatibility

**Build/Dev:**
- Shadow Plugin 8.3.5 (com.gradleup.shadow) - Fat JAR packaging with relocation
- Run Paper Plugin 3.0.2 (xyz.jpenilla.run-paper) - Development server

## Key Dependencies

**Critical:**
- `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT` - Server API (compile-only, not shaded)

**Infrastructure:**
- `com.zaxxer:HikariCP:5.1.0` - Connection pool for SQLite database
  - Relocated to: `com.blockworlds.collections.lib.hikari`
- `org.xerial:sqlite-jdbc:3.45.3.0` - SQLite JDBC driver
  - Relocated to: `com.blockworlds.collections.lib.sqlite`

## Configuration

**Environment:**
- No environment variables required
- All configuration via YAML files in plugin data folder

**Build:**
- `build.gradle.kts` - Main build configuration
- `settings.gradle.kts` - Project name definition
- `gradle/wrapper/gradle-wrapper.properties` - Gradle version

**Runtime Config Files:**
- `src/main/resources/config.yml` - Plugin settings
- `src/main/resources/zones.yml` - Spawn zone definitions
- `src/main/resources/paper-plugin.yml` - Plugin metadata (modern format)
- `src/main/resources/plugin.yml` - Legacy plugin metadata (backup)
- `src/main/resources/collections/*.yml` - Collection definitions

## Build Commands

```bash
# Build plugin JAR (outputs to build/libs/Collections-1.0.0.jar)
./gradlew shadowJar

# Run development server (downloads Paper automatically)
./gradlew runServer

# Run tests
./gradlew test

# Full build (includes shadowJar)
./gradlew build
```

## Compiler Settings

**Critical flags:**
- `-parameters` - Required for Brigadier command argument names
- `UTF-8` encoding

**ShadowJar Configuration:**
- Relocates HikariCP to `com.blockworlds.collections.lib.hikari`
- Relocates SQLite JDBC to `com.blockworlds.collections.lib.sqlite`
- Excludes signature files (`META-INF/*.SF`, `META-INF/*.DSA`, `META-INF/*.RSA`)
- Merges service files

## Platform Requirements

**Development:**
- Java 21 JDK
- Gradle 8.x (wrapper provided)
- No additional system dependencies

**Production:**
- Paper 1.21.4+ server
- Java 21 runtime
- Folia compatible (no Bukkit scheduler, uses region/entity schedulers)

## Version Information

```
Plugin Version: 1.0.0
Group: com.blockworlds
Artifact: Collections
Min Server Version: 1.21 (API version)
```

---

*Stack analysis: 2026-01-20*
