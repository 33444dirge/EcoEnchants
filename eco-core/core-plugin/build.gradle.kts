group = "com.willfp"
version = rootProject.version

dependencies {
    compileOnly(fileTree("../../lib") {
        include("*.jar")
    }
    )
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("com.willfp:eco:${rootProject.property("eco-version")}")
    testImplementation("com.willfp:libreforge:${rootProject.property("libreforge-version")}:shadow") {
        isTransitive = false
    }
    testImplementation("com.willfp:libreforge-loader:${rootProject.property("libreforge-version")}") {
        isTransitive = false
    }
    testImplementation("com.github.ben-manes.caffeine:caffeine:3.2.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    testImplementation("io.mockk:mockk-jvm:1.13.17")
    compileOnly("net.essentialsx:EssentialsX:2.19.7") {
        exclude("*", "*")
    }
}

tasks {
    build {
        dependsOn(publishToMavenLocal)
    }
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            from(components["java"])
            artifactId = rootProject.name
        }
    }

    publishing {
        repositories {
            maven {
                name = "Auxilor"
                url = uri("https://repo.auxilor.io/repository/maven-releases/")
                credentials {
                    username = System.getenv("MAVEN_USERNAME")
                    password = System.getenv("MAVEN_PASSWORD")
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileTestKotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
}
