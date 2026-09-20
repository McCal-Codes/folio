pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Folio"
include(":app")
include(":baselineprofile")

// Optional isolated experiment; excluded from ordinary launcher builds.
if (providers.gradleProperty("duoProbe").isPresent) include(":discover-probe")
