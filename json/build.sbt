name := "smile-json"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.json")

Compile / doc / scalacOptions ++= Seq(
  "-doc-root-content", baseDirectory.value + "/root-doc.txt",
  "-doc-title", "Smile - JSON"
)
Compile / doc / target := baseDirectory.value / "../doc/api/json"
