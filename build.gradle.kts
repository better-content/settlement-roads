import javax.xml.parsers.DocumentBuilderFactory

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    eclipse
    `maven-publish`
    jacoco
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("net.minecraftforge.gradle") version "6.0.54"
}

val minecraftVersion = property("minecraft_version") as String
val forgeVersion = property("forge_version") as String
val kotlinForForgeVersion = property("kotlinforforge_version") as String
val modId = property("mod_id") as String
val modName = property("mod_name") as String
val modVersion = property("mod_version") as String
val modAuthors = property("mod_authors") as String
val modDescription = property("mod_description") as String
val modLicense = property("mod_license") as String
val modIssueTrackerUrl = property("mod_issue_tracker_url") as String

group = property("mod_group") as String
version = modVersion

base {
    archivesName.set("settlement-roads")
}

fun deobf(notation: String): Any =
    requireNotNull(extensions.getByName("fg").withGroovyBuilder { "deobf"(notation) })

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

minecraft {
    mappings("official", minecraftVersion)
    copyIdeResources = true

    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "info")
            property("forge.enabledGameTestNamespaces", modId)
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)

            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }

        create("client")

        create("server") {
            arg("--nogui")
        }

        create("gameTestServer")

        create("data") {
            args(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources").absolutePath
            )
        }
    }
}

sourceSets.named("main") {
    resources.srcDir("src/generated/resources")
}

val syncGameTestStructures by tasks.registering(Copy::class) {
    from("gameteststructures")
    from("src/main/resources/gameteststructures")
    into(file("run/gameteststructures"))
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

dependencies {
    minecraft("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    implementation("thedarkcolour:kotlinforforge:$kotlinForForgeVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.processResources {
    val props = mapOf(
        "minecraftVersion" to minecraftVersion,
        "forgeVersion" to forgeVersion,
        "kotlinForForgeVersion" to kotlinForForgeVersion,
        "modId" to modId,
        "modName" to modName,
        "modVersion" to modVersion,
        "modAuthors" to modAuthors,
        "modDescription" to modDescription,
        "modIssueTrackerUrl" to modIssueTrackerUrl,
        "modLicense" to modLicense
    )

    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(props)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

tasks.named<Jar>("jar") {
    finalizedBy("reobfJar")
}

val stageRuntimeJar by tasks.registering(Copy::class) {
    group = "build"
    description = "Stages the reobfuscated runtime jar into build/libs using the canonical release filename."
    dependsOn(tasks.named("reobfJar"))
    from(layout.buildDirectory.file("reobfJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$version.jar" }
}

tasks.named("assemble") {
    dependsOn(stageRuntimeJar)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.matching { it.name == "prepareRunGameTestServer" }.configureEach {
    dependsOn(syncGameTestStructures)
}

tasks.register("headlessGameTest") {
    group = "verification"
    description = "Runs Forge game tests in a headless dedicated server."
    dependsOn(tasks.named("runGameTestServer"))
}

jacoco {
    toolVersion = "0.8.12"
}

val coveredPlannerFiles = sourceSets.main.get().output.classesDirs.asFileTree.matching {
    include(
        "com/bettercontent/settlementroads/planner/**/*.class",
        "com/bettercontent/settlementroads/data/**/*.class"
    )
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(coveredPlannerFiles)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

val verifyCoverageInputs by tasks.registering {
    group = "verification"
    description = "Rejects missing planner/data classes or missing JaCoCo execution data."
    dependsOn(tasks.test)
    doLast {
        val selected = tasks.jacocoTestReport.get().classDirectories.asFileTree.files
        for (scope in listOf("planner", "data")) {
            check(selected.any { it.invariantSeparatorsPath.contains("/com/bettercontent/settlementroads/$scope/") }) {
                "Coverage selection is missing expected classes in $scope"
            }
        }
        val execution = tasks.test.get().extensions.getByType<JacocoTaskExtension>().destinationFile
        check(execution != null && execution.isFile && execution.length() > 0) {
            "Coverage execution data is missing or empty"
        }
    }
}

tasks.jacocoTestReport { dependsOn(verifyCoverageInputs) }

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(tasks.jacocoTestReport.map { it.classDirectories })
    doFirst {
        val xml = tasks.jacocoTestReport.get().reports.xml.outputLocation.get().asFile
        check(xml.isFile) { "Coverage report is missing" }
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        val document = factory.newDocumentBuilder().parse(xml)
        val classes = document.getElementsByTagName("class")
        val executableClasses = (0 until classes.length).mapNotNull { index ->
            val klass = classes.item(index) as org.w3c.dom.Element
            val counters = klass.childNodes
            val executable = (0 until counters.length).any { child ->
                val counter = counters.item(child) as? org.w3c.dom.Element
                counter?.tagName == "counter" && counter.getAttribute("type") == "LINE" &&
                    counter.getAttribute("missed").toLong() + counter.getAttribute("covered").toLong() > 0
            }
            if (executable) klass.getAttribute("name").replace('/', '.') else null
        }
        for (scope in listOf("planner", "data")) {
            check(executableClasses.any { it.startsWith("com.bettercontent.settlementroads.$scope.") }) {
                "Coverage report is missing executable counters in $scope"
            }
        }
    }
    violationRules {
        rule {
            element = "BUNDLE"
            includes = listOf("*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.register("verifyFast") {
    group = "verification"
    description = "Runs the fast deterministic verification lane."
    dependsOn(tasks.named("check"))
}

tasks.register("verifyFull") {
    group = "verification"
    description = "Runs the full verification lane, including headless Forge GameTests."
    dependsOn(tasks.named("verifyFast"))
    dependsOn(tasks.named("headlessGameTest"))
}
