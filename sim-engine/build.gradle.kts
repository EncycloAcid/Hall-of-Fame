plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

// ./gradlew :sim-engine:runSample [-Pseed=2026]
tasks.register<JavaExec>("runSample") {
    group = "application"
    description = "Simulates one sample game and prints both box scores and the play-by-play."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.collegedynasty.sim.sample.SampleGameKt")
    args(project.findProperty("seed")?.toString() ?: "2026")
}

// ./gradlew :sim-engine:runBatch [-Pgames=1000] [-Pseed=1] [-Prules=ncaa-men|ncaa-women|nba]
tasks.register<JavaExec>("runBatch") {
    group = "application"
    description = "Runs a batch of simulated games and prints aggregate statistics."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.collegedynasty.sim.sample.BatchRunnerKt")
    args(
        project.findProperty("games")?.toString() ?: "1000",
        project.findProperty("seed")?.toString() ?: "1",
        project.findProperty("rules")?.toString() ?: "ncaa-men",
    )
}
