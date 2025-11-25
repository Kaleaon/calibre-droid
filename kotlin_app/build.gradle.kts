plugins {
    kotlin("jvm") version "1.9.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
    implementation("org.apache.pdfbox:pdfbox:2.0.29")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
}

application {
    mainClass.set("org.calibre.metadata.MainKt")
}

sourceSets {
    main {
        java.srcDir("../shared/src/main/kotlin")
        resources.srcDir("../shared/src/main/resources")
    }
}
