plugins {
    java
}

group = "com.smartwithdraw"
version = "2.0.0"

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
