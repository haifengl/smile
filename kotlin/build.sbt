name := "smile-kotlin"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.kotlin")

import kotlin.Keys._
kotlinLib("stdlib")

kotlinVersion := "2.2.21"
kotlincJvmTarget := "21"
