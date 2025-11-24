plugins {
    kotlin("jvm") version "1.9.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("org.calibre.metadata.MainKt")
}
