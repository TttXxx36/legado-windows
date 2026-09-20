import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.0.20"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
}

group = "io.legado.desktop"
version = "1.2.0"

kotlin {
    jvm("jvm") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

                // HTTP & HTML parsing
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("org.jsoup:jsoup:1.18.1")

                // JavaScript runtime for Legado book source evaluation
                implementation("org.mozilla:rhino:1.7.15")

                // SQLite database (Windows x64 stripped minimal runtime: 0.74 MB)
                implementation(files("libs/sqlite-jdbc-3.46.1.0-win64.jar"))
                implementation("org.slf4j:slf4j-api:1.7.36")

                // System directories (Windows AppData)
                implementation("dev.dirs:directories:26")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.legado.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "LegadoDesktop"
            packageVersion = "1.2.0"
            description = "Legado with MD3 for Windows"
            copyright = "© 2026 Legado Community"
            vendor = "Legado Open Source"

            modules("java.base", "java.desktop", "java.sql", "jdk.unsupported", "jdk.crypto.ec")

            windows {
                menuGroup = "Legado"
                upgradeUuid = "a72513ea-4d83-4ee1-b753-1579899fa9b1"
                dirChooser = true
                perUserInstall = true
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
            }
        }
    }
}
