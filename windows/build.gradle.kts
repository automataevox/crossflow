import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")                          version "1.9.22"
    id("org.jetbrains.compose")            version "1.6.1"
    kotlin("plugin.serialization")         version "1.9.22"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // mDNS / DNS-SD
    implementation("org.jmdns:jmdns:3.5.5")
}

compose.desktop {
    application {
        mainClass = "dev.crossflow.windows.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)  // Skip Exe due to Windows file locking issues
            packageName    = "CrossFlow"
            packageVersion = "1.0.0"
            description    = "Cross-platform clipboard sync"

            windows {
                menuGroup      = "CrossFlow"
                shortcut       = true
                dirChooser     = true
                perUserInstall = true
                upgradeUuid    = "3E8F2AA7-5B1C-4D9E-A3F6-7C82B04D51E9"
            }
        }
    }
}

// Workaround for Windows file locking issues during packageExe
afterEvaluate {
    tasks.findByName("packageExe")?.let{ task ->
        task.doFirst {
            val exeDir = file("build/compose/binaries/main/exe")
            if (exeDir.exists()) {
                println("Cleaning locked exe directory before packaging...")
                try {
                    exeDir.deleteRecursively()
                    Thread.sleep(1000)  // Wait for OS to release locks
                    println("Exe directory cleaned successfully")
                } catch (e: Exception) {
                    println("Warning: Could not fully delete exe dir: ${e.message}")
                    println("Attempting to continue anyway...")
                }
            }
        }
    }
}
