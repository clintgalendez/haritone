import java.util.zip.ZipFile
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component

plugins {
    alias(libs.plugins.cyclonedx)
}

repositories {
    // Official Hytale repository. The server API is compileOnly: it is never bundled or redistributed.
    maven("https://maven.hytale.com/release") {
        name = "hytale"
        content { includeGroup("com.hypixel.hytale") }
    }
}

dependencies {
    implementation(project(":core"))
    compileOnly(libs.hytale.server)
}

base { archivesName = "haritone" }

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("manifest.json") { expand("version" to version) }
}

tasks.jar {
    // Bundle core so the mod ships as a single jar; Hytale classes stay out (compileOnly).
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF")
    }
    from(rootProject.files("LICENSE", "NOTICE")) { into("META-INF") }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val verifyPluginJar = tasks.register("verifyPluginJar") {
    description = "Checks the packaged mod: manifest present and versioned, core bundled, no Hytale classes."
    group = "verification"
    val jarFile = tasks.jar.flatMap { it.archiveFile }
    val expectedVersion = project.version.toString()
    inputs.file(jarFile)
    doLast {
        ZipFile(jarFile.get().asFile).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            val manifest = zip.getEntry("manifest.json") ?: throw GradleException("manifest.json missing")
            val text = zip.getInputStream(manifest).bufferedReader().readText()
            check("\"Version\": \"$expectedVersion\"" in text) { "manifest.json Version is not $expectedVersion" }
            val main = Regex("\"Main\": \"([^\"]+)\"").find(text)?.groupValues?.get(1)
                ?: throw GradleException("manifest.json has no Main")
            check(main.replace('.', '/') + ".class" in names) { "Main class $main not in jar" }
            check(names.any { it.startsWith("io/github/clintgalendez/haritone/pathfinding/") }) { "core classes not bundled" }
            check(names.none { it.startsWith("com/hypixel/") }) { "Hytale classes must not be bundled" }
            check("META-INF/LICENSE" in names && "META-INF/NOTICE" in names) { "LICENSE/NOTICE missing" }
        }
    }
}

tasks.check { dependsOn(verifyPluginJar) }

tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    projectType = Component.Type.APPLICATION
    componentName = "haritone"
    includeConfigs = listOf("runtimeClasspath")
}
