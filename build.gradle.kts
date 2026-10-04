plugins {
    java
}

group = "com.smartwithdraw"
version = (findProperty("pluginVersion") as String?) ?: "4.0.0"

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")

    maven("https://jitpack.io")

    maven("https://repo.codemc.org/repository/maven-releases/")

    // Required for PlayerPoints - verified against PlayerPoints' own
    // build.gradle (group 'org.black_ixx').
    maven("https://repo.rosewooddev.io/repository/public/")

    // Required for PlaceholderAPI - it publishes to its own repo, not
    // Maven Central or any of the ones above.
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("org.black_ixx:playerpoints:3.3.5")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// The jar file is always called SmartWithdraw.jar (no version in the name),
// so nobody has to rename anything. The real version lives inside the jar
// (plugin.yml), and that is what the updater and /sw status read.
tasks.jar {
    archiveFileName.set("SmartWithdraw.jar")
}

// Puts the build version into plugin.yml so the jar's internal version
// always matches the release tag.
tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
