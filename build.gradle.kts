buildscript {
    dependencies {
        classpath(libs.android.shortcut.gradle)
    }
}

plugins {
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.moko) apply false
    alias(libs.plugins.sqldelight) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.register("installAndroidSdk") {
    doLast {
        val sdkRoot = System.getenv("ANDROID_HOME") ?: "${System.getProperty("user.home")}/Android/Sdk"
        val cmdlineToolsDir = file("$sdkRoot/cmdline-tools/latest")
        val sdkManagerPath = if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            "$cmdlineToolsDir/bin/sdkmanager.bat"
        } else {
            "$cmdlineToolsDir/bin/sdkmanager"
        }
        
        // Create SDK and build directories
        file(sdkRoot).mkdirs()
        layout.buildDirectory.get().asFile.mkdirs()
        
        // Download command line tools if not present
        if (!cmdlineToolsDir.exists()) {
            val toolsZip = file("${layout.buildDirectory.get()}/cmdline-tools.zip")
            val toolsUrl = "https://dl.google.com/android/repository/commandlinetools-" + when {
                org.gradle.internal.os.OperatingSystem.current().isWindows -> "win"
                org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "mac"
                else -> "linux"
            } + "-latest.zip"
            
            // Download using URLConnection
            println("Downloading Android SDK command-line tools from $toolsUrl")
            val connection = java.net.URL(toolsUrl).openConnection()
            connection.connect()
            toolsZip.outputStream().use { output ->
                connection.getInputStream().use { input ->
                    input.copyTo(output)
                }
            }
            
            // Extract using Java's ZIP API
            println("Extracting command-line tools")
            java.util.zip.ZipFile(toolsZip).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val dest = if (entry.name.startsWith("cmdline-tools/")) {
                        file("$sdkRoot/${entry.name}")
                    } else {
                        file("$sdkRoot/cmdline-tools/latest/${entry.name}")
                    }
                    if (entry.isDirectory) {
                        dest.mkdirs()
                    } else {
                        dest.parentFile.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            dest.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (dest.name.endsWith(".sh") || dest.name == "sdkmanager") {
                            dest.setExecutable(true)
                        }
                    }
                }
            }
            toolsZip.delete()
        }

        // Accept licenses
        println("Accepting Android SDK licenses")
        exec {
            workingDir = file(sdkRoot)
            executable = if (org.gradle.internal.os.OperatingSystem.current().isWindows) "cmd" else "sh"
            args = if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
                listOf("/c", "echo y | $sdkManagerPath --licenses")
            } else {
                listOf("-c", "yes | $sdkManagerPath --licenses")
            }
        }

        // Install required SDK components
        val components = listOf(
            "platform-tools",
            "platforms;android-33",
            "build-tools;33.0.0"
        )
        
        println("Installing Android SDK components: ${components.joinToString()}")
        components.forEach { component ->
            exec {
                workingDir = file(sdkRoot)
                executable = sdkManagerPath
                args = listOf(component)
            }
        }

        // Create local.properties with SDK path
        val localProperties = file("local.properties")
        println("Creating local.properties with sdk.dir=${sdkRoot.replace("\\", "/")}")
        localProperties.writeText("sdk.dir=${sdkRoot.replace("\\", "/")}")
    }
}
