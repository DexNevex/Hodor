plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}

// Optional: put build output somewhere else, e.g. outside a cloud-synced folder (iCloud and
// Dropbox can copy files mid-build and break dex merging). Set it in ~/.gradle/gradle.properties:
//   hodorBuildDir=/Users/you/Library/Caches/HodorBuild
providers.gradleProperty("hodorBuildDir").orNull?.let { dir ->
    allprojects { layout.buildDirectory.set(file("$dir/${project.name}")) }
}
