import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

android {
    namespace = "com.phantom.kuchupuchi"
    compileSdk = 37

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    defaultConfig {
        applicationId = "com.phantom.kuchupuchi"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GITHUB_OWNER", "\"phantom\"")
    }

    flavorDimensions += "app"
    productFlavors {
        create("kuchu") {
            dimension = "app"
            applicationIdSuffix = ".kuchu"
            buildConfigField("String", "FIREBASE_DB_URL", "\"https://kuchupuchi-default-rtdb.asia-southeast1.firebasedatabase.app/\"")
            buildConfigField("String", "FLAVOR_NAME", "\"kuchu\"")
            buildConfigField("String", "PARTNER_FLAVOR_NAME", "\"puchi\"")
            buildConfigField("String", "SELF_DB_PATH", "\"kuchu\"")
            buildConfigField("String", "PARTNER_DB_PATH", "\"puchi\"")
            resValue("string", "flavor_name", "kuchu")
            resValue("string", "partner_flavor_name", "puchi")
            resValue("string", "self_db_path", "kuchu")
            resValue("string", "partner_db_path", "puchi")
        }
        create("puchi") {
            dimension = "app"
            applicationIdSuffix = ".puchi"
            buildConfigField("String", "FIREBASE_DB_URL", "\"https://kuchupuchi-default-rtdb.asia-southeast1.firebasedatabase.app/\"")
            buildConfigField("String", "FLAVOR_NAME", "\"puchi\"")
            buildConfigField("String", "PARTNER_FLAVOR_NAME", "\"kuchu\"")
            buildConfigField("String", "SELF_DB_PATH", "\"puchi\"")
            buildConfigField("String", "PARTNER_DB_PATH", "\"kuchu\"")
            resValue("string", "flavor_name", "puchi")
            resValue("string", "partner_flavor_name", "kuchu")
            resValue("string", "self_db_path", "puchi")
            resValue("string", "partner_db_path", "kuchu")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register("deployToGitHub") {
    notCompatibleWithConfigurationCache("deployToGitHub task modifies build file and performs network requests")
    group = "deployment"
    description = "Bumps versionCode, builds release APKs, creates a GitHub Release, and uploads flavor assets."
    dependsOn("assembleRelease")
    doLast {
        val buildFile = layout.projectDirectory.file("build.gradle.kts").asFile
        val buildFileContent = buildFile.readText()
        val versionCodeRegex = Regex("""versionCode\s*=\s*(\d+)""")
        val matchResult = versionCodeRegex.find(buildFileContent)
            ?: throw GradleException("Could not find versionCode in build.gradle.kts")

        val currentVersionCode = matchResult.groupValues[1].toInt()
        val newVersionCode = currentVersionCode + 1
        val updatedContent = buildFileContent.replaceFirst(
            versionCodeRegex,
            "versionCode = $newVersionCode"
        )
        buildFile.writeText(updatedContent)
        println("[deployToGitHub] Version code bumped from $currentVersionCode to $newVersionCode")

        val localPropertiesFile = layout.projectDirectory.file("../local.properties").asFile
        val localProperties = Properties()
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }

        val githubToken = localProperties.getProperty("GITHUB_TOKEN")?.trim()
        val githubOwner = localProperties.getProperty("GITHUB_OWNER")?.trim()?.ifEmpty { null } ?: "phantom"
        val githubRepo = localProperties.getProperty("GITHUB_REPO")?.trim()?.ifEmpty { null } ?: "kuchupuchi"

        if (githubToken.isNullOrBlank()) {
            throw GradleException("GITHUB_TOKEN must be set in local.properties")
        }

        val releaseUrl = URI("https://api.github.com/repos/$githubOwner/$githubRepo/releases").toURL()
        val releaseConn = releaseUrl.openConnection() as HttpURLConnection
        releaseConn.requestMethod = "POST"
        releaseConn.setRequestProperty("Authorization", "Bearer $githubToken")
        releaseConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        releaseConn.setRequestProperty("Content-Type", "application/json")
        releaseConn.doOutput = true

        val payload = """{"tag_name": "$newVersionCode", "name": "v$newVersionCode", "body": "Release v$newVersionCode", "draft": false, "prerelease": false}"""
        releaseConn.outputStream.use { os ->
            os.write(payload.toByteArray(Charsets.UTF_8))
        }

        val responseCode = releaseConn.responseCode
        val responseText = if (responseCode in 200..299) {
            releaseConn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val errText = releaseConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            if (responseCode == 422 && errText.contains("Repository is empty")) {
                val errorMessage = """
                    ========================================================================
                    [deployToGitHub ERROR] Your GitHub repository '$githubOwner/$githubRepo' is empty (0 commits).
                    GitHub requires at least one commit before creating a release.
                    Please run the following commands in terminal to push your initial commit:
                      git init
                      git add .
                      git commit -m "Initial commit"
                      git branch -M main
                      git remote add origin https://github.com/$githubOwner/$githubRepo.git
                      git push -u origin main
                    ========================================================================
                """.trimIndent()
                println(errorMessage)
                throw GradleException("GitHub repository '$githubOwner/$githubRepo' is empty (0 commits). Push an initial commit before releasing.")
            }
            throw GradleException("Failed to create GitHub release (HTTP $responseCode): $errText")
        }

        val releaseIdMatch = Regex(""""id"\s*:\s*(\d+)""").find(responseText)
            ?: throw GradleException("Could not parse release ID from response: $responseText")
        val releaseId = releaseIdMatch.groupValues[1]

        val uploadUrlMatch = Regex(""""upload_url"\s*:\s*"([^"]+)"""").find(responseText)
            ?: throw GradleException("Could not parse upload_url from response: $responseText")
        val rawUploadUrl = uploadUrlMatch.groupValues[1]
        val uploadUrlBase = rawUploadUrl.replace(Regex("""\{.*\}"""), "")

        println("[deployToGitHub] Created GitHub release v$newVersionCode (ID: $releaseId)")

        val kuchuDir = layout.buildDirectory.dir("outputs/apk/kuchu/release").get().asFile
        val kuchuApk = listOf(
            File(kuchuDir, "app-kuchu-release.apk"),
            File(kuchuDir, "kuchu-release.apk"),
            File(kuchuDir, "app-kuchu-release-unsigned.apk"),
            File(kuchuDir, "kuchu-release-unsigned.apk")
        ).firstOrNull { it.exists() } ?: kuchuDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }

        val puchiDir = layout.buildDirectory.dir("outputs/apk/puchi/release").get().asFile
        val puchiApk = listOf(
            File(puchiDir, "app-puchi-release.apk"),
            File(puchiDir, "puchi-release.apk"),
            File(puchiDir, "app-puchi-release-unsigned.apk"),
            File(puchiDir, "puchi-release-unsigned.apk")
        ).firstOrNull { it.exists() } ?: puchiDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }

        val assetsToUpload = mapOf(
            "kuchu-release.apk" to kuchuApk,
            "puchi-release.apk" to puchiApk
        )

        for ((assetName, apkFile) in assetsToUpload) {
            if (apkFile == null || !apkFile.exists()) {
                throw GradleException("Release APK for asset $assetName not found.")
            }

            val uploadUrl = URI("$uploadUrlBase?name=$assetName").toURL()
            val uploadConn = uploadUrl.openConnection() as HttpURLConnection
            uploadConn.requestMethod = "POST"
            uploadConn.setRequestProperty("Authorization", "Bearer $githubToken")
            uploadConn.setRequestProperty("Content-Type", "application/vnd.android.package-archive")
            uploadConn.setRequestProperty("Content-Length", apkFile.length().toString())
            uploadConn.doOutput = true

            apkFile.inputStream().use { input ->
                uploadConn.outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            val uploadResponseCode = uploadConn.responseCode
            if (uploadResponseCode == 201 || uploadResponseCode == 200) {
                println("[deployToGitHub] Successfully uploaded asset: $assetName")
            } else {
                val errText = uploadConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw GradleException("Failed to upload asset $assetName (HTTP $uploadResponseCode): $errText")
            }
        }
    }
}
