name := "smile-json"

scalacOptions in (Compile, doc) ++= Seq(
  "-doc-root-content", baseDirectory.value + "/root-doc.txt",
  "-doc-title", "Smile - JSON"
)
target in Compile in doc := baseDirectory.value / "../doc/api/json"
