import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

/**
 * Shortcut for <code>project.findProperty(key).toString()</code>.
 */
fun properties(key: String) = project.findProperty(key).toString()

/**
 * Shortcut for <code>System.getenv().getOrDefault(key, default).toString()</code>.
 */
fun environment(key: String, default: String) = System.getenv().getOrDefault(key, default).toString()

fun isRelease() = !project.version.toString().contains("-")

version = properties("plugin.version") + "+" + properties("plugin.since-build")

plugins {
    idea
    java
    id("org.jetbrains.intellij.platform") version "2.1.0"
    id("org.jetbrains.changelog") version "2.2.1"   // https://github.com/JetBrains/gradle-changelog-plugin
    id("io.freefair.lombok") version "8.10"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(properties("platform.java-version"))
        vendor = JvmVendorSpec.JETBRAINS
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}


repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        snapshots()
        jetbrainsRuntime()
    }
}

dependencies {
    intellijPlatform {
        create(properties("platform.type"), properties("platform.version"))

        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.properties")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("org.jetbrains.idea.maven")
        bundledPlugin("org.jetbrains.plugins.gradle")

        // instrumentation
        instrumentationTools()
        javaCompiler()
        // verify
        pluginVerifier()
        // sign
        zipSigner()

        testFramework(TestFrameworkType.JUnit5)
    }

    implementation("jakarta.validation:jakarta.validation-api:3.1.0")
    implementation("org.apache.commons", "commons-collections4", "4.4")
    implementation("org.apache.commons", "commons-lang3", "3.14.0")
    implementation("org.springframework.boot", "spring-boot", "3.3.4")
    implementation("com.miguelfonseca.completely", "completely-core", "0.9.0")

    testImplementation("org.mockito", "mockito-core", "2.12.0")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation(platform("org.junit:junit-bom:5.11.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("junit:junit:4.13.2")
}

changelog {
    path = rootDir.resolve("CHANGELOG.md").path
}

intellijPlatform {
    pluginConfiguration {
        id = properties("plugin.id")
        name = properties("plugin.name")
        version = project.version as String
        ideaVersion {
            sinceBuild = properties("plugin.since-build")
            untilBuild = provider { null }
        }
        changeNotes = changelog.run {
            renderItem(
                if (isRelease()) get(project.version as String) else getUnreleased(),
                Changelog.OutputType.HTML
            )
        }
        description = rootDir.resolve("README.md").readText().lines().run {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            if (!containsAll(listOf(start, end))) {
                throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
            }
            subList(indexOf(start) + 1, indexOf(end))
        }.joinToString(
            separator = "\n",
            postfix = "\nProject [document](" + properties("plugin.source-url") + "/#readme)\n"
        ).run { markdownToHTML(this) }

        vendor {
            name = properties("vendor.name")
            email = properties("vendor.email")
            url = properties("vendor.url")
        }
    }

    signing {
        val chain = rootProject.file("chain.crt")
        if (chain.exists()) {
            certificateChainFile.set(chain)
        } else {
            certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        }
        val private = rootProject.file("private.pem")
        if (private.exists()) {
            privateKeyFile.set(rootProject.file("private.pem"))
        } else {
            privateKey.set(System.getenv("PRIVATE_KEY"))
        }
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#publishPlugin
    publishing {
        token = System.getenv("PUBLISH_TOKEN")
        channels = listOf(
            (version.toString().split('-').firstOrNull() ?: "default").split('.', '+').first()
        )
    }

    pluginVerification {
        ides {
            select {
                types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = properties("plugin.since-build")
                untilBuild = provider { null }
            }
        }
    }
}


tasks {
    compileJava {
        options.release = properties("platform.java-version").toInt()
    }

    runIde {
        jvmArgs("-ea", "-Xdebug")
        systemProperty("idea.is.internal", "true")
        systemProperty("idea.log.debug.categories", "dev.flikas,in.oneton.idea.spring.assistant.plugin")
        systemProperty("intellij.idea.indices.debug", "true")
        systemProperty("intellij.idea.indices.debug.extra.sanity", "true")
    }

    publishPlugin {
        if (!version.toString().contains('-')) {
            dependsOn("patchChangelog")
        }
    }

    test {
        useJUnitPlatform()
        testLogging {
            events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
        }
    }

    printProductsReleases {
        channels = listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.EAP)
        types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
        sinceBuild = properties("plugin.since-build")
        untilBuild = provider { null }
    }
}
