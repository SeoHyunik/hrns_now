plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
}

tasks.test {
    useJUnit()
}
