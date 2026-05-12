plugins {
  java
  application
}

java {
  sourceCompatibility = JavaVersion.toVersion(rootProject.findProperty("javaSourceLevel") as String)
  targetCompatibility = JavaVersion.toVersion(rootProject.findProperty("javaTargetLevel") as String)
}

application { mainClass.set("org.eclipse.keyple.plugin.pcsc.validation.ValidationApp") }

dependencies {
  // The PC/SC plugin under test (fat-jar includes jnasmartcardio)
  implementation(rootProject)

  implementation(platform("org.eclipse.keyple:keyple-java-bom:2026.03.19"))

  // Keyple service layer
  implementation("org.eclipse.keyple:keyple-service-java-lib:4.0.0-SNAPSHOT") { isChanging = true }

  // Keypop reader API (CardReader, ObservableCardReader, CardReaderEvent, …)
  implementation("org.eclipse.keypop:keypop-reader-java-api:3.0.0-SNAPSHOT") { isChanging = true }

  // Keyple common API (KeyplePluginExtension, KeypleReaderExtension)
  implementation("org.eclipse.keyple:keyple-common-java-api")

  // Plugin SPI API (exception types: CardIOException, ReaderIOException, …)
  implementation("org.eclipse.keyple:keyple-plugin-java-api:3.0.0-SNAPSHOT") { isChanging = true }

  // Utility library (HexUtil)
  implementation("org.eclipse.keyple:keyple-util-java-lib")

  // SLF4J simple logger for console output
  runtimeOnly("org.slf4j:slf4j-simple:1.7.36")
}

// Produce a runnable fat-jar so the app can be launched without Gradle
tasks.jar {
  manifest { attributes("Main-Class" to "org.eclipse.keyple.plugin.pcsc.validation.ValidationApp") }
  duplicatesStrategy = DuplicatesStrategy.WARN
  from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
