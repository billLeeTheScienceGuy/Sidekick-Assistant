plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.sidekick.app"
    compileSdk = 35
    ndkVersion = "27.0.12077973"
    defaultConfig {
        applicationId = "com.sidekick.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "0.10.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    buildTypes {
        create("benchmark") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".benchmark"
            matchingFallbacks += "debug"
            externalNativeBuild { cmake { arguments += "-DSIDEKICK_BENCHMARK=ON" } }
        }
    }
    testBuildType = if (providers.gradleProperty("speechBenchmark").isPresent) "benchmark" else "debug"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    androidResources { noCompress += "bin" }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".deps/language-fixtures"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
val verifySpeechModel by tasks.registering {
    val modelFile = layout.projectDirectory.file("src/main/assets/models/ggml-tiny.en.bin")
    val vadFile = layout.projectDirectory.file("src/main/assets/models/ggml-silero-v5.1.2.bin")
    val multilingualFile = layout.projectDirectory.file("src/main/assets/models/ggml-tiny.bin")
    val speakerFile = layout.projectDirectory.file("src/main/assets/models/voxceleb_resnet34_LM.onnx")
    inputs.file(modelFile)
    inputs.file(vadFile)
    inputs.file(multilingualFile)
    inputs.file(speakerFile)
    doLast {
        check(multilingualFile.asFile.isFile && multilingualFile.asFile.length() == 77_691_713L) {
            "Run python scripts/prepare_speech.py to prepare multilingual transcription."
        }
        check(speakerFile.asFile.isFile && speakerFile.asFile.length() == 26_530_550L) {
            "Run python scripts/prepare_speaker.py to prepare the speaker model and Android runtime."
        }
        val model = modelFile.asFile
        check(model.isFile && model.length() == 77_704_715L) {
            "Missing or incomplete speech model. Run scripts/prepare-speech.ps1 (or scripts/prepare_speech.py) before building."
        }
        check(vadFile.asFile.isFile && vadFile.asFile.length() == 885_098L) {
            "Missing speech detector. Run scripts/prepare-speech.ps1 or scripts/prepare_speech.py."
        }
    }
}
tasks.named("preBuild") { dependsOn(verifySpeechModel) }
dependencies {
    implementation(files("libs/sherpa-onnx-1.13.3.aar"))
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // Android 17 removed the reflective InputManager API used by older Espresso.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.room:room-testing:2.7.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
