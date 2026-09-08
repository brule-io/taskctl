pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}
rootProject.name = "taskctl"
include("core", "repository", "compatibility", "cli", "conformance")
include("native-tests")
include("kernel")
include("idl")
