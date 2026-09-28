import org.jetbrains.sbt.kotlin.Keys.*

name := "smile-kotlin"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.kotlin")

// Exclude any gradle kts scripts from being picked up as sources
unmanagedSources / excludeFilter := (unmanagedSources / excludeFilter).value || "*.gradle.kts"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "2.4.20"
kotlincJvmTarget := "25"

// The Kotlin scripting host API used by smile.studio.kernel.ScriptRunnerBridge.
// It is only needed by hosts that embed the Kotlin scripting engine (Studio),
// not by consumers of the smile-kotlin API, so it is marked Provided to keep
// it off the published POM. A host must put the same artifacts on its runtime
// classpath. Keep the version in sync with `kotlin` in gradle/libs.versions.toml.
libraryDependencies ++= Seq(
  "org.jetbrains.kotlin" % "kotlin-scripting-jvm-host" % kotlinVersion.value % Provided,
  "org.jetbrains.kotlin" % "kotlin-scripting-common"   % kotlinVersion.value % Provided,
  "org.jetbrains.kotlin" % "kotlin-scripting-jvm"      % kotlinVersion.value % Provided,
  "org.jetbrains.kotlin" % "kotlin-compiler-embeddable" % kotlinVersion.value % Provided
)

