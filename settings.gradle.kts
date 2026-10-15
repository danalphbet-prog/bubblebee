pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "QwenLocalChat"
include(":app")

val llamaLib = file("third_party/llama.cpp/examples/llama.android/lib")
if (!llamaLib.exists()) {
    throw GradleException(
        "llama.cpp nao encontrado. Rode ./scripts/fetch-llama.sh antes do Gradle. " +
        "No GitHub Actions isso acontece automaticamente."
    )
}
include(":llama")
project(":llama").projectDir = llamaLib
