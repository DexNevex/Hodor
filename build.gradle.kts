plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}

// Keep build output out of the iCloud-synced Desktop: sync creates "X 2" copies
// of files mid-build, which breaks dex merging.
allprojects {
    layout.buildDirectory.set(file("${System.getProperty("user.home")}/Library/Caches/HodorBuild/${project.name}"))
}
