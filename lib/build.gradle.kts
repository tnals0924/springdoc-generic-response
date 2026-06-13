plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.4.0")

    testImplementation(kotlin("test"))
    testImplementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:3.4.0")
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
repositories {
    mavenCentral()
}