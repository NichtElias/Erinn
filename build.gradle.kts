
plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.3.2"
}

group = "party.elias"
version = "1.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test-junit5"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()

    maxHeapSize = "2G"
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "party.elias.erinn.MainKt"
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the chess engine"

    classpath(sourceSets.main.get().runtimeClasspath)

    mainClass.set("party.elias.erinn.MainKt")

    enableAssertions = true

    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}
