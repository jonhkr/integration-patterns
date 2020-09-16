import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("idea")
    id("application")
    id("com.diffplug.spotless") version Versions.spotless
    kotlin("jvm") version Versions.kotlin
}

repositories {
    jcenter()
}

group = "com.ebanx"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("ebanx.AppKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

dependencies {
    implementation(Libs.slf4jApi)
    implementation(Libs.logbackClassic)
    implementation(Libs.kotlinStdlib)
    implementation(Libs.kotlinStdlibJdk8)
    implementation(Libs.kafkaClients)
    implementation(Libs.jacksonDatabind)

    testImplementation(Libs.junitApi)
    testImplementation(Libs.junitEngine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

spotless {
    kotlin {
        ktlint()
    }
    java {
        importOrder()
        removeUnusedImports()
        googleJavaFormat()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "gradle/*.gradle.kts")
        ktlint()
    }
}
