import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    application
}

group = "org.inego"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://csspeechstorage.blob.core.windows.net/maven/")
}

dependencies {

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.0")

    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")

    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.19.0@jar")
    implementation("org.apache.commons:commons-csv:1.9.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.0.5")
}

application {
    mainClass.set("MainWindowKt")
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}