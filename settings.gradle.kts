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

    maven {
      url = uri("http://repo-sdk.tange-ai.com/repository/maven-public/")
      isAllowInsecureProtocol = true
      credentials {
        username = providers.gradleProperty("TIRTC_PUBLIC_MAVEN_USERNAME").orElse("tange_user").get()
        password = providers.gradleProperty("TIRTC_PUBLIC_MAVEN_PASSWORD").orElse("tange_user").get()
      }
    }

    val extraMavenUrl =
      providers.gradleProperty("tirtcExampleMavenUrl")
        .orElse(providers.environmentVariable("TIRTC_EXAMPLE_MAVEN_URL"))
        .orNull
    if (!extraMavenUrl.isNullOrBlank()) {
      maven(url = uri(extraMavenUrl))
    }
  }
}

rootProject.name = "tirtc-example-android"
include(":example")

project(":example").projectDir = file("example")
