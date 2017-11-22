import Dependencies._

// Leverage Maven's repository and not look for the local
resolvers in ThisBuild ++= Seq(
    "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/"
)

val commonSettings = Seq(
  name := "tube",
  organization := "org.nugit",
  description := "Data pipeline",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.11",
  scalacOptions ++= Seq("-Yrangepos", "-Ypartial-unification")
)

// mask out the nugit.tracer.*, nugit.routes.* and nugit.tube.*
val codeCoverageSettings = Seq(
 coverageEnabled := true,
 coverageExcludedPackages := "nugit\\.tracer\\..*;nugit\\.routes\\..*;nugit\\.tube\\.Main",
 coverageMinimum := 80,
 coverageFailOnMinimum := true
)

val slacks = ProjectRef(uri("git://github.com/raymondtay/slacks.git"), "slacks")

lazy val tube = (project in file(".")).dependsOn(slacks)
  .settings(
    commonSettings ++ codeCoverageSettings,
    libraryDependencies ++= (generalLibs ++ testLibs)
  )

enablePlugins(JavaServerAppPackaging)

// make run command include the provided dependencies
run in Compile := Defaults.runTask(fullClasspath in Compile,
                                   mainClass in (Compile, run),
                                   runner in (Compile,run)
                                  ).evaluated

// exclude Scala library from assembly
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)
