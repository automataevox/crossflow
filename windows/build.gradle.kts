import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.util.Calendar

// ── Version Management ──────────────────────────────────────────────────────
fun readVersion(): String {
    val versionFile = File("${project.projectDir}/version.properties")
    return if (versionFile.exists()) {
        versionFile.readLines()
            .filter { it.startsWith("APP_VERSION=") }
            .firstOrNull()
            ?.substringAfter("APP_VERSION=")
            ?.trim() ?: "1.0.0"
    } else {
        "1.0.0"
    }
}

fun getBuildVersion(): String {
    // Get build version from current time (HHmm format)
    // Example: 1430 = 2:30 PM, 0845 = 8:45 AM
    val cal = Calendar.getInstance()
    val hour = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
    val minute = String.format("%02d", cal.get(Calendar.MINUTE))
    val timeString = "$hour$minute".toInt()  // Convert to int (1430, 0845, etc.)
    return timeString.toString()
}

fun convertToMsiVersion(version: String, buildTimestamp: String): String {
    // Convert MAJOR.MINOR.FIX → MAJOR.MINOR.BUILD (HHmm time)
    // Windows MSI format is MAJOR.MINOR.BUILD where BUILD can be 0-65535
    val parts = version.split(".")
    return try {
        val major = parts.getOrNull(0)?.toInt() ?: 1
        val minor = parts.getOrNull(1)?.toInt() ?: 0
        
        "$major.$minor.$buildTimestamp"
    } catch (e: Exception) {
        "1.0.0"
    }
}

val appVersion = readVersion()
val buildTimestamp = getBuildVersion()
val msiVersion = convertToMsiVersion(appVersion, buildTimestamp)
val fullVersion = "$appVersion.$buildTimestamp"
println("📦 CrossFlow Windows Version: $fullVersion (MSI: $msiVersion)")

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
            packageVersion = msiVersion
            description    = "Cross-platform clipboard sync"
            
            // Disable ProGuard minification (incompatible with Java 21+)
            // ProGuard 7.2.2 doesn't support Java 21 bytecode (version 65.0)
            buildTypes.release.proguard.isEnabled = false

            windows {
                menuGroup      = "CrossFlow"
                shortcut       = true
                dirChooser     = true
                perUserInstall = true
                upgradeUuid    = "3E8F2AA7-5B1C-4D9E-A3F6-7C82B04D51E9"
                iconFile       = File("${project.projectDir}/src/main/resources/icon.ico")
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

// ── Version increment tasks ─────────────────────────────────────────────────
tasks.register("incrementFix") {
    group = "version"
    description = "Increment fix version (1.0.0 → 1.0.1) - build time auto-generated"
    doLast {
        incrementVersion("fix")
    }
}

tasks.register("incrementMinor") {
    group = "version"
    description = "Increment minor version (1.0.x → 1.1.0) - build time auto-generated"
    doLast {
        incrementVersion("minor")
    }
}

tasks.register("incrementMajor") {
    group = "version"
    description = "Increment major version (1.x.x → 2.0.0) - build time auto-generated"
    doLast {
        incrementVersion("major")
    }
}

tasks.register("showVersion") {
    group = "version"
    description = "Show current app version with build time"
    doLast {
        println("Current CrossFlow version: $fullVersion")
        println("MSI package version:       $msiVersion")
        println("Build time (HHmm):         $buildTimestamp")
        println("✓ upgradeUuid: 3E8F2AA7-5B1C-4D9E-A3F6-7C82B04D51E9")
        println("✓ MSI upgrades enabled - no uninstall needed!")
        println("✓ Build number is current time in HHmm format (e.g., 1430 = 2:30 PM)")
    }
}

fun incrementVersion(type: String) {
    val versionFile = File("${project.projectDir}/version.properties")
    val currentVersion = readVersion()
    val parts = currentVersion.split(".")
    
    val (major, minor, fix) = try {
        val m = parts.getOrNull(0)?.toInt() ?: 1
        val mi = parts.getOrNull(1)?.toInt() ?: 0
        val f = parts.getOrNull(2)?.toInt() ?: 0
        Triple(m, mi, f)
    } catch (e: Exception) {
        Triple(1, 0, 0)
    }
    
    val newVersion = when (type) {
        "fix" -> "$major.$minor.${fix + 1}"
        "minor" -> "$major.${minor + 1}.0"
        "major" -> "${major + 1}.0.0"
        else -> currentVersion
    }
    
    val content = "# CrossFlow Windows Version\n# Format: MAJOR.MINOR.FIX\n# BUILD number is automatically generated from current time (HHmm)\n# This ensures each build is unique and can be used for upgrades\nAPP_VERSION=$newVersion\n"
    versionFile.writeText(content)
    
    println("✓ Version updated: $currentVersion → $newVersion")
    println("✓ Build number will be auto-generated from current time (HHmm) on next build")
    println("✓ Ready to build with: ./gradlew packageMsi")
}
