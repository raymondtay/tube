import Dependencies._

// Leverage Maven's repository and not look for the local
resolvers in ThisBuild ++= Seq(
    "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
    Resolver.sonatypeRepo("snapshots") /* for snapshots only */
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
 coverageExcludedPackages := "nugit\\.tracer\\..*;nugit\\.routes\\..*;nugit\\.tube\\.Main",
 coverageMinimum := 80,
 coverageFailOnMinimum := false
)

val slacks = ProjectRef(uri("git://github.com/raymondtay/slacks.git"), "slacks")

lazy val tube = (project in file(".")).dependsOn(slacks)
  .settings(
    commonSettings ++ codeCoverageSettings,
    libraryDependencies ++= (generalLibs ++ testLibs)
  )

enablePlugins(JavaServerAppPackaging)

concurrentRestrictions in Global := Tags.limit(Tags.ForkedTestGroup, 4) :: Nil

import Tests._
def groupByFirst(tests: Seq[TestDefinition]) =
  tests groupBy (_.name.contains("Sink")) map {
    case (true, tests) ⇒
      val options = ForkOptions().withRunJVMOptions(Vector("-D-J-Xmx3072m"))
      new Group("FlinkTests", tests, SubProcess(options))
    case (false, tests) ⇒
      val options = ForkOptions().withRunJVMOptions(Vector("-D-J-Xmx1536m"))
      new Group("NonFlinkTests", tests, SubProcess(options))
  } toSeq

testGrouping in Test := groupByFirst( (definedTests in Test).value )

testForkedParallel in Test := true

// make run command include the provided dependencies
run in Compile := Defaults.runTask(fullClasspath in Compile,
                                   mainClass in (Compile, run),
                                   runner in (Compile,run)
                                  ).evaluated

// exclude Scala library from assembly
test in assembly := {} /* tests should have been run during CI/CD */
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)
