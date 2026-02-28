import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

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

kotlin {
    jvmToolchain(21)
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

tasks.register<ShadowJar>("tunePstJar") {
    group = "build"
    archiveClassifier.set("tunepst")

    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes["Main-Class"] = "party.elias.tunepst.TunePSTKt"
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

tasks.register<JavaExec>("tunePst") {
    group = "application"
    description = "Tune the piece square tables"

    classpath(sourceSets.main.get().runtimeClasspath)

    mainClass.set("party.elias.tunepst.TunePSTKt")

    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err

    jvmArgs("-Xmx12G")
}
