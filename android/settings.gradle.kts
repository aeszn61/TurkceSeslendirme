pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "TurkceSeslendirme"
include(":tts-tr")
// Android SDK olmayan ortamlarda (ör. sadece Kotlin testleri) uygulama modülünü atla
if (System.getenv("SADECE_KUTUPHANE") == null) include(":app")
