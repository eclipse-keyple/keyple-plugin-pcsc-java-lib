rootProject.name = "keyple-plugin-pcsc-java-lib"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots")
  }
}
