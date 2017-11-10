import Dependencies._

val commonSettings = Seq(
  name := "tube",
  description := "Data pipeline",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.12.4",
  scalacOptions ++= Seq("-Yrangepos", "-Ypartial-unification")
)

val codeCoverageSettings = Seq(
 coverageEnabled := true,
 coverageExcludedPackages := "",
 coverageMinimum := 80,
 coverageFailOnMinimum := true
)

lazy val tube = (project in file("."))
  .settings(
    commonSettings ++ codeCoverageSettings,
    libraryDependencies ++= (generalLibs ++ testLibs)
  )


