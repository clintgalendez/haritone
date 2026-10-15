import com.diffplug.gradle.spotless.SpotlessExtension
import net.ltgt.gradle.errorprone.errorprone

buildscript {
    repositories { gradlePluginPortal() }
    dependencies {
        // The CycloneDX plugin pulls Jackson 2.22.2, which has advisories fixed in 2.22.3.
        // Build-time only; nothing from the build classpath ships in the mod jar.
        classpath(platform("com.fasterxml.jackson:jackson-bom:2.22.3"))
    }
}

plugins {
    alias(libs.plugins.cyclonedx) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotless) apply false
}

check(JavaVersion.current() >= JavaVersion.VERSION_25) {
    "Haritone needs JDK 25 or newer to build (found ${JavaVersion.current()}). Point JAVA_HOME at a JDK 25+."
}

// Two modules share one Java setup; a convention plugin would be the upgrade if more modules appear.
subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "net.ltgt.errorprone")

    repositories { mavenCentral() }

    dependencies {
        "errorprone"(rootProject.libs.errorprone.core)
        "testImplementation"(platform(rootProject.libs.junit.bom))
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
    }

    tasks.withType<JavaCompile>().configureEach {
        // The Hytale server targets Java 25 (class file major 69); newer JDKs may build it.
        options.release = 25
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
        options.errorprone.disableWarningsInGeneratedCode = true
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
        finalizedBy("jacocoTestReport")
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    configure<JacocoPluginExtension> { toolVersion = rootProject.libs.versions.jacoco.get() }

    configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat()
        }
    }
}
