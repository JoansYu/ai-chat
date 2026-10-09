pluginManagement {
    // Gradle 插件版本必须显式声明，否则会报
    // "Plugin [id: 'org.jetbrains.kotlin.jvm'] was not found ... must include a version number"
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.10"
        id("org.jetbrains.intellij.platform") version "2.19.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ai-agent-plugin"
