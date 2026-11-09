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
        versionCode = 47
        versionName = "0.29.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    buildTypes {
        create("compactTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".compacttest"
            matchingFallbacks += "debug"
        }
        create("benchmark") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".benchmark"
            matchingFallbacks += "debug"
            externalNativeBuild { cmake { arguments += "-DSIDEKICK_BENCHMARK=ON" } }
        }
    }
    testBuildType = if (providers.gradleProperty("compactTest").isPresent) "compactTest" else if (providers.gradleProperty("speechBenchmark").isPresent) "benchmark" else "debug"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    androidResources { noCompress += "bin" }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".deps/language-fixtures"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".deps/robustness-fixtures"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".deps/quality-fixtures"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".deps/speaker-evaluation"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".deps/korean-evaluation/assets"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".deps/asr-commands"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".deps/quality-small"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".deps/moonshine-ar/assets"))
    sourceSets.getByName("main").assets.setSrcDirs(listOf(layout.buildDirectory.dir("generated/compactAssets")))
    sourceSets.getByName("benchmark").assets.srcDir("src/main/assets")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
// Normal builds require only bundled models; optional language packs are benchmark inputs.
val bundledModels = mapOf(
    "diarization.int8.onnx" to 1_540_506L,
    "ggml-tiny.en.bin" to 77_704_715L,
    "ggml-silero-v5.1.2.bin" to 885_098L,
    "voxceleb_resnet34_LM.onnx" to 26_530_550L,
    "campplus-advanced.onnx" to 28_281_164L,
)
val optionalModels = mapOf(
    "ggml-tiny.bin" to 77_691_713L,
    "ggml-base.bin" to 147_951_465L,
    "sensevoice-ko.int8.onnx" to 239_233_841L,
    "sensevoice-tokens.txt" to 315_894L,
    "omni-model.int8.onnx" to 365_352_120L,
    "omni-tokens.txt" to 86_423L,
)
val verifySpeechModel by tasks.registering {
    inputs.files(bundledModels.keys.map { layout.projectDirectory.file("src/main/assets/models/$it") })
    doLast {
        bundledModels.forEach { (name, bytes) ->
            check(layout.projectDirectory.file("src/main/assets/models/$name").asFile.length() == bytes) {
                "Missing or incomplete bundled model $name. Run prepare_speech.py --compact, prepare_speaker.py and prepare_diarization.py."
            }
        }
    }
}
val verifyOptionalSpeechModels by tasks.registering {
    inputs.files(optionalModels.keys.map { layout.projectDirectory.file("src/main/assets/models/$it") })
    doLast {
        optionalModels.forEach { (name, bytes) ->
            check(layout.projectDirectory.file("src/main/assets/models/$name").asFile.length() == bytes) {
                "Missing or incomplete benchmark model $name. Run prepare_speech.py, prepare_sensevoice.py and prepare_omnilingual.py."
            }
        }
    }
}
tasks.matching { it.name == "preBenchmarkBuild" }.configureEach { dependsOn(verifyOptionalSpeechModels) }
val prepareCompactAssets by tasks.registering(Sync::class) {
    from("src/main/assets")
    into(layout.buildDirectory.dir("generated/compactAssets"))
    exclude("models/ggml-tiny.bin", "models/ggml-base.bin", "models/sensevoice-*", "models/omni-*")
}
tasks.named("preBuild") { dependsOn(verifySpeechModel, prepareCompactAssets) }
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
