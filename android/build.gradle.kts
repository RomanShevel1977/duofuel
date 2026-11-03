allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

// file_picker 11 skips the Kotlin plugin on AGP 9 and expects built-in
// Kotlin. This project keeps android.builtInKotlin=false so older plugins
// can still apply the Kotlin Gradle plugin, which leaves FilePickerPlugin
// uncompiled. Register this before evaluationDependsOn, which evaluates
// :app (and therefore file_picker) immediately.
subprojects {
    val libraryProject = this
    pluginManager.withPlugin("com.android.library") {
        if (libraryProject.name != "file_picker") {
            return@withPlugin
        }
        if (!libraryProject.pluginManager.hasPlugin("org.jetbrains.kotlin.android")) {
            libraryProject.pluginManager.apply("org.jetbrains.kotlin.android")
        }
        libraryProject.extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension>(
            "kotlin",
        ) {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }
}

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
