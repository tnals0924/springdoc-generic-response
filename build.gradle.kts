plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    id("org.springframework.boot") version "3.4.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "io.github.tnals0924"
version = "1.0-SNAPSHOT"

subprojects {
    repositories {
        mavenCentral()
    }
}
