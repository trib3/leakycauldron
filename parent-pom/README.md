parent-pom
==========
Pom to be used as a parent pom for modules and downstream consumers of the Leaky Cauldron.
Configures consistent and coherent versions of libraries and plugins with applicable exclusions.
Applies the following to all projects:
* [dokka](https://github.com/Kotlin/dokka) for documentation generation
* [ktlint](https://github.com/pinterest/ktlint) for code formatting
* [detekt](https://arturbosch.github.io/detekt) for static analysis
* [dependency plugin](https://maven.apache.org/plugins/maven-dependency-plugin) 
  for dependency analysis
* [dependency scope plugin](https://github.com/hubspot/dependency-scope-maven-plugin)
  for more dependency analysis
* [enforcer plugin](https://maven.apache.org/enforcer/maven-enforcer-plugin)
  for more dependency analysis
* [JaCoCo](https://github.com/jacoco/jacoco) for test coverage
* [TestNG](https://github.com/cbeust/testng) for testing

The following properties will set profiles that disable some enforcement
for faster one time builds:
* `-Dfast`: skips dokka generation and source jar generation
* `-Dfaster`: additionally skips enforcer, ktlint, detekt, and dependency analysis checks
* `-Dfastest`: additionally skips tests
