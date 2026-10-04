# OnDevice-AI-Chat
Run a large language model fully offline on an Android phone using llama.cpp through JNI.

## Project structure

```
OndeviceAI/
└── llama/                              <- Android library module
    └── src/main/
        ├── java/ai/sudo/llama/
        │   └── LlamaBridge.kt          <- Kotlin API you call
        └── cpp/
            ├── CMakeLists.txt          <- Build script
            ├── llama-jni.cpp           <- JNI bridge to llama.cpp
            └── llama.cpp/              <- llama.cpp source (you add this)
```
---

## Requirements

- Android Studio (latest)
- Android NDK (installed from SDK Manager)
- CMake 3.22.1 (installed from SDK Manager)
- An ARM64 Android phone (`arm64-v8a`)

---

## Setup

### 1. Add llama.cpp

From the `llama/src/main/cpp/` folder:

```bash
git clone https://github.com/ggml-org/llama.cpp.git
```

> If you get compile errors after updating llama.cpp, the C++ API may have changed. Use the same version the code was tested with, or adjust the function calls.

### 2. Configure Gradle

In `llama/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

### 3. Add the module to your app

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":llama"))
}
```

### 4. Get a model

Download a **GGUF** model. Small models work best on phones
