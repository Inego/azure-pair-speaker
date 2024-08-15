plugins {
    kotlin("jvm") version "2.0.0"
    application
}

group = "org.inego"
version = "1.0-SNAPSHOT"

val kotlinxCoroutinesVersion = "1.8.1"
val stanfordNlpVersion = "4.5.5"
val slf4jVersion = "2.0.16"

repositories {
    mavenCentral()
    maven("https://csspeechstorage.blob.core.windows.net/maven/")
}

dependencies {

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinxCoroutinesVersion")

    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")

    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.38.0@jar")
    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    implementation("edu.stanford.nlp:stanford-corenlp:$stanfordNlpVersion")
    implementation("edu.stanford.nlp:stanford-corenlp:$stanfordNlpVersion:models")
    implementation("edu.stanford.nlp:stanford-corenlp:$stanfordNlpVersion:models-english")
    implementation("edu.stanford.nlp:stanford-corenlp:$stanfordNlpVersion:models-english-kbp")

    // SLF4J dependencies
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")

    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("MainWindowKt")
}
