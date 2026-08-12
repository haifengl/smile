import org.jetbrains.sbt.kotlin.Keys.*

name := "smile-kotlin"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.kotlin")

// Exclude any gradle kts scripts from being picked up as sources
unmanagedSources / excludeFilter := (unmanagedSources / excludeFilter).value || "*.gradle.kts"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "2.4.10"
kotlincJvmTarget := "25"

