import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import kotlin.plus

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


tasks.register<FastChessBenchmarkTask>("fastchessBenchmark") {
    val shadowJarTask = tasks.named<ShadowJar>("shadowJar")
    builtJar.set(shadowJarTask.flatMap { it.archiveFile })
    projectRootDir.set(project.rootDir)
    jarBaseName.set(project.name + "-" + project.version)
    javaLauncher.set(project.extensions.getByType<JavaToolchainService>().launcherFor {
        languageVersion.set(java.toolchain.languageVersion)
    })
}


abstract class FastChessBenchmarkTask @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:Input
    @get:Option(option = "id", description = "Identifier for the feature to be benchmarked")
    abstract val featureId: Property<String>

    @get:Input
    @get:Option(option = "base", description = "Identifier of the baseline engine version to be benchmarked against")
    abstract val baselineId: Property<String>

    @get:Input
    @get:Option(option = "rounds", description = "How many rounds to play")
    abstract val roundCount: Property<Int>

    @get:Input
    @get:Option(option = "seed", description = "The seed to use for opening randomization")
    abstract val rngSeed: Property<Int>

    @get:Input
    @get:Option(option = "tc", description = "Time controls for fastchess")
    abstract val timeControls: Property<String>

    @get:InputFile
    abstract val builtJar: RegularFileProperty

    @get:Internal
    abstract val projectRootDir: DirectoryProperty

    @get:Internal
    abstract val jarBaseName: Property<String>

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    init {
        baselineId.convention("base")
        roundCount.convention(100)
        rngSeed.convention(39872039)
        timeControls.convention("5+0.2")
        dependsOn("shadowJar")
    }

    @TaskAction
    fun runBenchmark() {
        val id = featureId.get()
        val baseId = baselineId.get()

        val javaPath = javaLauncher.get().executablePath.asFile.absolutePath
        val testBenchPath = projectRootDir.get().asFile.toPath().resolve("testbench")
        val featureJarName = "${jarBaseName.get()}-$id.jar"
        val baseJarName = "${jarBaseName.get()}-$baseId.jar"

        fileSystemOperations.copy {
            from(builtJar.get().asFile)
            into(testBenchPath.toFile())
            rename { featureJarName }
        }

        execOperations.exec {
            commandLine(
                "fastchess",
                "-engine", "name=$id", "args=-Xmx300M -jar ${testBenchPath.resolve(featureJarName)}",
                "-engine", "name=$baseId", "args=-Xmx300M -jar ${testBenchPath.resolve(baseJarName)}",
                "-each", "cmd=$javaPath", "tc=${timeControls.get()}", "-rounds", "${roundCount.get()}", "-concurrency", "5", "-maxmoves", "100",
                "-openings", "file=./8moves_v3.pgn", "format=pgn", "order=random", "-srand", "${rngSeed.get()}",
                "-autosaveinterval", "0", "-config", "outname=fastchess-config.json"
            )
        }
    }
}