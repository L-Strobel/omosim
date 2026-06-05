import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("com.gradleup.shadow") version "8.+"
    id("java")
    id("org.jetbrains.dokka") version "2.2.0"
    id("maven-publish")
    id("org.jetbrains.kotlinx.benchmark") version "0.4.13"
    kotlin("plugin.allopen") version "2.0.20"
    application
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

group = "de.uniwuerzburg.omosim"
version = "2.3.4-ic-mx"

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

dependencies {
    implementation("org.geotools:gt-epsg-hsql:31.+")
    implementation("org.geotools:gt-main:31.+")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.locationtech.jts:jts-core:1.+")
    implementation("org.apache.commons:commons-math3:3.+")
    implementation("com.github.ajalt.clikt:clikt:4.+")
    implementation("com.graphhopper:graphhopper-core:9.+")
    implementation("com.graphhopper:graphhopper-reader-gtfs:9.1")
    implementation("ch.qos.logback:logback-classic:1.+")
    implementation("org.openstreetmap.osmosis:osmosis-pbf:0.48.+")
    implementation("org.openstreetmap.osmosis:osmosis-xml:0.48.+")
    implementation("org.openstreetmap.osmosis:osmosis-areafilter:0.48.+")
    implementation("com.google.guava:guava:33.2.1-jre")
    implementation("org.duckdb:duckdb_jdbc:1.1.1")
    implementation("us.dustinj.timezonemap:timezonemap:4.+")
    implementation("org.xerial:sqlite-jdbc:3.+")
    implementation("org.jetbrains.kotlinx:multik-core:0.2.3")
    implementation("org.jetbrains.kotlinx:multik-default:0.2.3")
    implementation("com.gurobi:gurobi:11.0.2")
    implementation("com.github.haifengl:smile-core:4.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.13")
    implementation("org.tensorflow:tensorflow-core-platform:1.1.0")
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
    mergeServiceFiles()
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

tasks.register("ciPipeline") {
    group = "verification"
    description = "Run unit tests, build, smoke tests, and acceptance tests."

    dependsOn("test", "shadowJar", "smokeTest", "acceptanceTest")
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
    dependsOn("shadowJar", "installPythonDeps")
    mustRunAfter("test", "smokeTest")

    group = "verification"
    description = "Runs the acceptance tests using pytest."
    workingDir = file("system_tests")

    val jarFile = file("${project.layout.buildDirectory.get()}/libs/${project.name}-${version}-all.jar")
    environment("APP_JAR_PATH", jarFile.absolutePath)

    val pytestBinary = if (OperatingSystem.current().isWindows) {
        "$venvDir\\Scripts\\pytest.exe"
    } else {
        "$venvDir/bin/pytest"
    }

    commandLine(pytestBinary, "acceptance_tests/")
}

val smokeTest = tasks.register<Exec>("smokeTest") {
    dependsOn("shadowJar", "installPythonDeps")

    group = "verification"
    description = "Builds the Shadow JAR and runs a smoke test against it."
    workingDir = file("system_tests")

    val jarFile = file("${project.layout.buildDirectory.get()}/libs/${project.name}-${version}-all.jar")
    environment("APP_JAR_PATH", jarFile.absolutePath)

    val pytestBinary = if (OperatingSystem.current().isWindows) {
        "$venvDir\\Scripts\\pytest.exe"
    } else {
        "$venvDir/bin/pytest"
    }

    commandLine(pytestBinary, "smoke_tests/")
}