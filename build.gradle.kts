import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    eclipse
    `maven-publish`
    jacoco
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
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
    archivesName.set(modId)
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.matching { it.name == "prepareRunGameTestServer" }.configureEach {
    dependsOn(syncGameTestStructures)
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main").get()) {
            exclude(
                "**/com/gerald/settlementroads/command/**",
                "**/com/gerald/settlementroads/config/**",
                "**/com/gerald/settlementroads/registry/**",
                "**/com/gerald/settlementroads/tag/**",
                "**/com/gerald/settlementroads/runtime/**",
                "**/com/gerald/settlementroads/worldgen/**",
                "**/com/gerald/settlementroads/debug/**",
                "**/com/gerald/settlementroads/gametest/**",
                "**/com/gerald/settlementroads/SettlementRoadsMod*"
            )
        }
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "BUNDLE"
            includes = listOf(
                "com.gerald.settlementroads.planner.*",
                "com.gerald.settlementroads.planner.model.*",
                "com.gerald.settlementroads.planner.terrain.*",
                "com.gerald.settlementroads.planner.palette.*",
                "com.gerald.settlementroads.planner.placement.*",
                "com.gerald.settlementroads.planner.bridge.*",
                "com.gerald.settlementroads.data.*"
            )
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
