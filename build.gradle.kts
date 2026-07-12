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
    // build.gradle (group 'org.black_ixx', published here).
    maven("https://repo.rosewooddev.io/repository/public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")

    // Verified from your PlayerPoints-master/build.gradle: group
    // 'org.black_ixx', published artifact id 'playerpoints'. Double
    // check this version still exists in the repo above before
    // building - if PlayerPoints released something newer, bump it.
    compileOnly("org.black_ixx:playerpoints:3.3.5")

    compileOnly("me.clip:placeholderapi:2.11.6")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
