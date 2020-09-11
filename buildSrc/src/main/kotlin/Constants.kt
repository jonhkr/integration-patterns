object Versions {
    const val kotlin = "1.4.10"
    const val spotless = "5.3.0"
    const val junit = "5.6.2"
    const val slf4j = "1.7.30"
    const val logback = "1.2.3"
}

object Libs {
    const val slf4jApi = "org.slf4j:slf4j-api:${Versions.slf4j}"
    const val logbackClassic = "ch.qos.logback:logback-classic:${Versions.logback}"
    const val kotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}"
    const val kotlinStdlibJdk8 = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}"
    const val junitApi = "org.junit.jupiter:junit-jupiter-api:${Versions.junit}"
    const val junitEngine = "org.junit.jupiter:junit-jupiter-engine:${Versions.junit}"

}