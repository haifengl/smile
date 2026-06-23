name := "smile-kotlin"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.kotlin")

// Exclude any gradle kts scripts from being picked up as sources
unmanagedSources / excludeFilter := (unmanagedSources / excludeFilter).value || "*.gradle.kts"

import kotlin.Keys._
kotlinLib("stdlib")

kotlinVersion := "2.4.0"
kotlincJvmTarget := "25"

