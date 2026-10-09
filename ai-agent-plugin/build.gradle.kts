import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// 本地 IDE 安装目录；可在 gradle.properties 里用 idePath 覆盖
val idePath: String = providers.gradleProperty("idePath")
    .getOrElse("D:/Program Files/JetBrains/IntelliJ IDEA 2026.2.0.1")

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
    intellijPlatform {
        // 必须用 defaultRepositories()：
        //  - 含 localPlatformArtifacts()，否则 local(IDE 目录) 会报 Could not find localIde:IU:IU-262.xxxxx
        //  - 含 intellijDependencies()，否则 :instrumentCode 找不到 java-compiler-ant-tasks
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    // Kotlin emits null checks itself; this plugin has no GUI Designer .form files.
    // Avoid the Ant instrumentation JDK-layout dependency (e.g. missing Packages on MS JDK 21).
    instrumentCode = false

    pluginConfiguration {
        version = "0.1.0"
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        local(idePath)
        // IdeToolExecutor 使用 Java PSI（JavaPsiFacade / PsiClass / PsiJavaFile …），
        // 必须显式声明 Java 插件依赖，否则 compileKotlin 报 Unresolved reference
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
}

// Only load this plugin and its Java dependency in the test sandbox.
tasks.test {
    systemProperty("idea.load.plugins.id", "com.github.joansyu.aiagentplugin,com.intellij.java")
}
