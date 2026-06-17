plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("com.vanniktech.maven.publish") version "0.30.0"
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

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId = rootProject.group.toString(),
        artifactId = "springdoc-generic-response",
        version = rootProject.version.toString()
    )

    pom {
        name = "springdoc-generic-response"
        description = "A library that fixes generic type flattening in Spring Boot + springdoc-openapi environments."
        url = "https://github.com/tnals0924/springdoc-generic-response"

        licenses {
            license {
                name = "Apache License 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        developers {
            developer {
                id = "tnals0924"
                name = "Sumin Hwang"
                email = "tnals655@gmail.com"
            }
        }

        scm {
            connection = "scm:git:git://github.com/tnals0924/springdoc-generic-response.git"
            developerConnection = "scm:git:ssh://github.com/tnals0924/springdoc-generic-response.git"
            url = "https://github.com/tnals0924/springdoc-generic-response"
        }
    }
}
