plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// GPU offload is compiled in only when a Vulkan SDK is pointed at: llama.cpp's
// Vulkan backend compiles its compute shaders to SPIR-V at build time, which
// needs glslc and a host compiler that the Android NDK does not provide.
// Point VULKAN_SDK at an SDK root (bin/glslc, include/vulkan) to enable it;
// without it the app builds CPU-only and never offers the offload switch.
val vulkanSdk: String? = System.getenv("VULKAN_SDK")?.trim()?.takeIf { it.isNotEmpty() }
val vulkanGlslc: File? = vulkanSdk?.let { root ->
    listOf("bin/glslc.exe", "bin/glslc").map { File(root, it) }.firstOrNull { it.isFile }
}
if (vulkanSdk != null && vulkanGlslc == null) {
    throw GradleException("VULKAN_SDK=$vulkanSdk has no bin/glslc executable")
}

android {
    namespace = "net.xiondigital.anlasoz"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.xiondigital.anlasoz"
        // Vulkan 1.1 arrived with Android 9: the NDK stubs below API 28 carry
        // only the 1.0 entry points, and llama.cpp's Vulkan backend needs 1.1.
        // Only a GPU build has that requirement, so a CPU-only build keeps
        // running on Android 8.0.
        minSdk = if (vulkanSdk != null && vulkanGlslc != null) 28 else 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
        resourceConfigurations += listOf("en", "tr")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // On-device inference runs llama.cpp through JNI. Phones are arm64;
        // other ABIs would triple the APK for nobody's benefit.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
                arguments += if (vulkanSdk != null && vulkanGlslc != null) {
                    listOf(
                        "-DANLASOZ_VULKAN=ON",
                        "-DVulkan_INCLUDE_DIR=${File(vulkanSdk, "include").path}",
                        "-DVulkan_GLSLC_EXECUTABLE=${vulkanGlslc.path}",
                        "-DCMAKE_PREFIX_PATH=$vulkanSdk",
                    )
                } else {
                    listOf("-DANLASOZ_VULKAN=OFF")
                }
            }
        }
    }

    ndkVersion = "27.2.12479018"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
            )
        }
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
