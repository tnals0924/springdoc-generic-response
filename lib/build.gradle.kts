plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `maven-publish`
    signing
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

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["java"])

            groupId = rootProject.group.toString()
            artifactId = "springdoc-generic-response"
            version = rootProject.version.toString()

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
    }

    repositories {
        maven {
            name = "mavenCentral"
            url = if (version.toString().endsWith("-SNAPSHOT")) {
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            } else {
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            }
            credentials {
                username = project.findProperty("mavenCentralUsername") as String?
                password = project.findProperty("mavenCentralPassword") as String?
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(
        project.findProperty("signingKeyId") as String?,
        project.findProperty("signingKey") as String?,
        project.findProperty("signingPassword") as String?
    )
    sign(publishing.publications["mavenKotlin"])
}