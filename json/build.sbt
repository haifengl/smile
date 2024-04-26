name := "smile-json"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.json")

Compile / doc / scalacOptions ++= Seq(
  "-project", "Smile - JSON",
  "-doc-root-content", baseDirectory.value + "/root-doc.txt"
)
Compile / doc / target := baseDirectory.value / "../doc/api/json"
