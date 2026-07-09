plugins {
    id("io.papermc.paperweight.userdev")
}

group = "com.willfp"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.kyori:adventure-text-serializer-ansi:5.1.1")
    paperweight.paperDevBundle("1.21.8-R0.1-SNAPSHOT")
}

tasks {
    build {
        dependsOn(reobfJar)
    }

    reobfJar {
        mustRunAfter(shadowJar)
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}