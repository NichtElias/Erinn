plugins {
    kotlin("multiplatform") version "2.2.21"
}

group = "party.elias"
version = "0"

repositories {
    mavenCentral()
}

kotlin {

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    linuxX64 {
        binaries {
            executable {
                entryPoint = "party.elias.main"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.named<Jar>("jvmJar") {
    manifest {
        attributes["Main-Class"] = "party.elias.MainKt"
    }

    from(configurations.named("jvmRuntimeClasspath").get().map { file ->
        if (file.isDirectory) file else zipTree(file)
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<JavaExec>("runJar") {
    group = "application"
    description = "Builds and runs the Jar"

    dependsOn("jvmJar")

    val jarTask = tasks.named<Jar>("jvmJar").get()
    classpath(jarTask.archiveFile)

    mainClass.set("party.elias.MainKt")

    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}
