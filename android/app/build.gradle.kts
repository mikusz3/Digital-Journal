plugins { id("com.android.application") }

android {
    namespace = "pl.digitalbujo.app"
    compileSdk = 36
    buildFeatures { resValues = true }
    defaultConfig {
        applicationId = "pl.digitalbujo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.5.0"
        resValue("string", "app_name", "Digital Journal")
    }
    signingConfigs {
        create("journalRelease") {
            val keyPath = System.getenv("JOURNAL_KEYSTORE")
            if (keyPath != null) {
                storeFile = file(keyPath)
                storePassword = System.getenv("JOURNAL_STORE_PASSWORD")
                keyAlias = System.getenv("JOURNAL_KEY_ALIAS") ?: "journal"
                keyPassword = System.getenv("JOURNAL_KEY_PASSWORD") ?: System.getenv("JOURNAL_STORE_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (System.getenv("JOURNAL_KEYSTORE") != null) signingConfig = signingConfigs.getByName("journalRelease")
        }
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            resValue("string", "app_name", "Digital Journal QA")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
abstract class JournalTemplates : DefaultTask() {
    @get:InputFile abstract val source: RegularFileProperty
    @get:InputFile abstract val locales: RegularFileProperty
    @get:InputFile abstract val notices: RegularFileProperty
    @get:InputFile abstract val themes: RegularFileProperty
    @get:OutputDirectory abstract val output: DirectoryProperty
    @TaskAction fun generate() {
        output.get().asFile.mkdirs()
        source.get().asFile.copyTo(output.file("templates.json").get().asFile, overwrite = true)
        themes.get().asFile.copyTo(output.file("themes.json").get().asFile, overwrite = true)
        notices.get().asFile.copyTo(output.file("notices.txt").get().asFile, overwrite = true)
        locales.get().asFile.copyTo(output.file("locales.json").get().asFile, overwrite = true)
    }
}
val copyJournalTemplates by tasks.registering(JournalTemplates::class) {
    source.set(layout.projectDirectory.file("../../core/templates.json"))
    themes.set(layout.projectDirectory.file("../../core/themes.json"))
    notices.set(layout.projectDirectory.file("../../THIRD_PARTY_NOTICES.md"))
    locales.set(layout.projectDirectory.file("../../core/locales.json"))
    output.set(layout.buildDirectory.dir("generated/journalAssets"))
}
androidComponents.onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(copyJournalTemplates, JournalTemplates::output) }
dependencies {
    implementation("androidx.core:core:1.17.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
