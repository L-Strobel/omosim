import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("com.gradleup.shadow") version "8.3.11"
    id("java")
    id("org.jetbrains.dokka") version "2.2.0"
    id("maven-publish")
    id("org.jetbrains.kotlinx.benchmark") version "0.4.17"
    kotlin("plugin.allopen") version "2.4.10"
    application
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

group = "de.uniwuerzburg.omosim"
version = "2.6.3"

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.osgeo.org/repository/release/")
    }
    maven {
        url = uri("https://repo.osgeo.org/repository/snapshot/")
    }
    mavenCentral()
}

val commonImplementation = configurations.create("commonImplementation")
configurations.implementation {
    extendsFrom(commonImplementation)
}

// Fix old std dependency in us.dustinj.timezonemap:timezonemap:4.5
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.4.32"))
            .using(module("org.jetbrains.kotlin:kotlin-stdlib:2.4.10"))
    }
}

dependencies {
    commonImplementation(files("libs/alglib-java/alglib406free.jar"))

    commonImplementation("org.geotools:gt-epsg-hsql:35.0")
    commonImplementation("org.geotools:gt-main:35.0")
    commonImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json: 1.11.0")
    commonImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    commonImplementation("org.locationtech.jts:jts-core:1.20.0")
    commonImplementation("org.locationtech.jts.io:jts-io-common:1.20.0")
    commonImplementation("org.apache.commons:commons-math3:3.6.1")
    commonImplementation("com.github.ajalt.clikt:clikt:5.1.0")
    commonImplementation("com.graphhopper:graphhopper-core:11.0")
    commonImplementation("com.graphhopper:graphhopper-reader-gtfs:11.0")
    commonImplementation("ch.qos.logback:logback-classic:1.5.38")
    commonImplementation("org.openstreetmap.osmosis:osmosis-pbf:0.48.3")
    commonImplementation("org.openstreetmap.osmosis:osmosis-xml:0.48.3")
    commonImplementation("org.openstreetmap.osmosis:osmosis-areafilter:0.48.3")
    commonImplementation("com.google.guava:guava:33.6.0-jre")
    commonImplementation("org.duckdb:duckdb_jdbc: 1.5.4.0")
    commonImplementation("us.dustinj.timezonemap:timezonemap:4.5")
    commonImplementation("org.xerial:sqlite-jdbc:3.53.2.0")
    commonImplementation("com.gurobi:gurobi:13.0.2")
    commonImplementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.17")
    commonImplementation("com.akuleshov7:ktoml-core:0.7.1")
    commonImplementation("org.jetbrains.kotlinx:multik-default:0.3.1")
    commonImplementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat") // For com.akuleshov7:ktoml-core:0.7.1

    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.1")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.1")
    testImplementation("org.tensorflow:tensorflow-core-platform:1.1.0")

    // Libraries that come with heavy platform specific binaries
    implementation("org.tensorflow:tensorflow-core-platform:1.1.0")
    implementation("com.github.haifengl:smile-core:4.4.2")
}

publishing {
    publications {
        create<MavenPublication>("omosim") {
            from(components["java"])
        }
    }
}

application {
    mainClass.set("de.uniwuerzburg.omosim.cli.MainKt")
}

benchmark {
    targets {
        register("benchmark")
    }
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    create("benchmark")
}

kotlin {
    jvmToolchain(21)
    target {
        compilations.getByName("benchmark")
            .associateWith(compilations.getByName("main"))
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.shadowJar {
    description = "Builds big shadow JARs that works with all platforms"
    mergeServiceFiles()
}

val platforms = listOf("windows-x86_64", "linux-x86_64")

platforms.forEach { platform ->
    val platformConfig = configurations.create("shadow-$platform") {
        extendsFrom(commonImplementation)
        exclude(group = "org.bytedeco", module = "openblas-platform")
    }

    dependencies {
        "shadow-$platform"("com.github.haifengl:smile-core:4.4.2")
        "shadow-$platform"("org.bytedeco:openblas:0.3.31-1.5.13:$platform")

        "shadow-$platform"("org.tensorflow:tensorflow-core-api:1.1.0")
        "shadow-$platform"("org.tensorflow:tensorflow-core-native:1.1.0:$platform")
    }

    tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar-$platform") {
        group = "shadow"
        description = "Compiles a shadow JAR for $platform"

        configurations = listOf(platformConfig)
        from(sourceSets.main.get().output)
        archiveClassifier.set(platform)

        manifest {
            attributes("Main-Class" to application.mainClass.get())
        }

        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")

        mergeServiceFiles()
    }
}

tasks.register("shadowJarAll") {
    group = "shadow"
    description = "Builds shadow JARs for all supported platforms individually"

    platforms.forEach { platform ->
        dependsOn(tasks.named("shadowJar-$platform"))
    }
    dependsOn("shadowJar")
}

tasks.register("ciPipeline") {
    group = "verification"
    description = "Run unit tests, build, smoke tests, and acceptance tests."

    dependsOn("test", "shadowJarAll", "smokeTest")
}

// Python tests against a build jar
val venvDir = file("${projectDir}/system_tests/.venv") // Venv location

val installPythonDeps = tasks.register<Exec>("installPythonDeps") {
    mustRunAfter("test")

    group = "verification"
    description = "Sets up a venv and installs Python test dependencies."

    inputs.file("system_tests/requirements.txt")
    outputs.dir(venvDir)

    if (OperatingSystem.current().isWindows) {
        commandLine("cmd", "/c", "python -m venv $venvDir && $venvDir\\Scripts\\pip install -r system_tests/requirements.txt")
    } else {
        commandLine("sh", "-c", "python3 -m venv $venvDir && $venvDir/bin/pip install -r system_tests/requirements.txt")
    }
}

val acceptanceTest = tasks.register<Exec>("acceptanceTest") {
    dependsOn("shadowJarAll", "installPythonDeps")
    mustRunAfter("test", "smokeTest")

    group = "verification"
    description = "Runs the acceptance tests using pytest."
    workingDir = file("system_tests")

    val arch = System.getProperty("os.arch").lowercase()
    val is86 = arch.contains("x86_64") || arch.contains("amd64")
    val os = OperatingSystem.current()

    val jarFile = if (os.isWindows and is86) {
        file("${project.layout.buildDirectory.get()}/libs/${project.name}-${version}-windows-x86_64.jar")
    } else if (os.isLinux and is86) {
        file("${project.layout.buildDirectory.get()}/libs/${project.name}-${version}-linux-x86_64.jar")
    } else {
        file("${project.layout.buildDirectory.get()}/libs/${project.name}-${version}-all.jar")
    }
    environment("APP_JAR_PATH", jarFile.absolutePath)

    val pytestBinary = if (os.isWindows) {
        "$venvDir\\Scripts\\pytest.exe"
    } else {
        "$venvDir/bin/pytest"
    }

    commandLine(pytestBinary, "acceptance_tests/", "-s")
}

val smokeTest = tasks.register<Exec>("smokeTest") {
    dependsOn("shadowJarAll", "installPythonDeps")

    group = "verification"
    description = "Builds the Shadow JAR and runs a smoke test against it."
    workingDir = file("system_tests")

    val arch = System.getProperty("os.arch").lowercase()
    val is86 = arch.contains("x86_64") || arch.contains("amd64")
    val os = OperatingSystem.current()

    val jarFile = if (os.isWindows and is86) {
        file("${project.layout.buildDirectory.get()}/libs/${project.name}-${version}-windows-x86_64.jar")
    } else if (os.isLinux and is86) {
        file("${project.layout.buildDirectory.get()}/libs/${project.name}-${version}-linux-x86_64.jar")
    } else {
        file("${project.layout.buildDirectory.get()}/libs/${project.name}-${version}-all.jar")
    }
    environment("APP_JAR_PATH", jarFile.absolutePath)

    val pytestBinary = if (os.isWindows) {
        "$venvDir\\Scripts\\pytest.exe"
    } else {
        "$venvDir/bin/pytest"
    }

    commandLine(pytestBinary, "smoke_tests/")
}