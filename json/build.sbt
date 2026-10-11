import com.typesafe.sbt.packager.MappingsHelper.*

name := "smile-json"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.json")

Compile / doc / scalacOptions ++= Seq(
  "-project", "Smile - JSON",
  "-doc-root-content", (baseDirectory.value / "root-doc.txt").toString
)
Compile / doc / target := baseDirectory.value / ".." / "target" / "docs" / "json"

lazy val copyJarToStudio = taskKey[Unit]("Copy packaged jar to studio")

copyJarToStudio := Def.uncached {
  val converter = fileConverter.value
  val jarFile = converter.toPath((Compile / packageBin).value).toFile()
  val destDir = baseDirectory.value / ".." / "studio" / "scala-lib"
  IO.copyFile(jarFile, destDir / jarFile.name)
}

