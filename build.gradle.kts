plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.3.2"
}

group = "party.elias"
version = "0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "party.elias.MainKt"
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the chess engine"

    classpath(sourceSets.main.get().runtimeClasspath)

    mainClass.set("party.elias.MainKt")

    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}
