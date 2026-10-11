import com.typesafe.sbt.packager.MappingsHelper.*

name := "smile-scala"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.scala")

Compile / doc / scalacOptions ++= Seq(
  "-project", "Smile - Scala",
  "-doc-root-content", (baseDirectory.value / "root-doc.txt").toString
)
Compile / doc / target := baseDirectory.value / ".." / "target" / "docs" / "scala"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "2.5.0"
)

lazy val copyJarToStudio = taskKey[Unit]("Copy packaged jar to studio")

copyJarToStudio := Def.uncached {
  val converter = fileConverter.value
  val jarFile = converter.toPath((Compile / packageBin).value).toFile()
  val destDir = baseDirectory.value / ".." / "studio" / "scala-lib"
  IO.copyFile(jarFile, destDir / jarFile.name)
}
