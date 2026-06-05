name := "smile-kotlin"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.kotlin")

unmanagedSources / excludeFilter := HiddenFileFilter || "build.gradle.kts"

import kotlin.Keys._
kotlinLib("stdlib")

kotlinVersion := "2.4.0"
kotlincJvmTarget := "25"

