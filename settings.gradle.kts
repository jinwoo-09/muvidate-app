pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "MuviDate"

include(":app")
include(":capacitor-android")
project(":capacitor-android").projectDir = file("node_modules/@capacitor/android/capacitor")
include(":capacitor-app")
project(":capacitor-app").projectDir = file("node_modules/@capacitor/app/android")
include(":capacitor-browser")
project(":capacitor-browser").projectDir = file("node_modules/@capacitor/browser/android")
include(":capacitor-keyboard")
project(":capacitor-keyboard").projectDir = file("node_modules/@capacitor/keyboard/android")
include(":capacitor-network")
project(":capacitor-network").projectDir = file("node_modules/@capacitor/network/android")
include(":capacitor-screen-orientation")
project(":capacitor-screen-orientation").projectDir = file("node_modules/@capacitor/screen-orientation/android")
include(":capacitor-splash-screen")
project(":capacitor-splash-screen").projectDir = file("node_modules/@capacitor/splash-screen/android")
include(":capacitor-status-bar")
project(":capacitor-status-bar").projectDir = file("node_modules/@capacitor/status-bar/android")
include(":capacitor-cordova-android-plugins")
project(":capacitor-cordova-android-plugins").projectDir = file("android/capacitor-cordova-android-plugins")
