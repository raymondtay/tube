import sbt._

object Dependencies {

  // Versions
  val catsVersion       = "0.9.0"
  val akkaHttpVersion   = "10.0.10"
  val akkaVersion       = "2.4.19"
  val scalaCheckVersion = "1.13.4"
  val specs2Version     = "4.0.1"
  val circeVersion      = "0.8.0"
  val openTracingVersion = "0.30.0"
  val jaegerCoreVersion  = "0.22.0-RC2"
  val scalaLoggerVersion = "3.7.2"
  val logbackClassic     = "1.2.3"
  val flinkVersion       = "1.4-SNAPSHOT"
  val slacksVersion      = "0.1-SNAPSHOT"

  // Libraries
  val cats           = "org.typelevel" %% "cats-core" % catsVersion
  val akkaHttp       = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaHttpTest   = "com.typesafe.akka" %% "akka-http-testkit" %  akkaHttpVersion
  val scalaCheckTest = "org.scalacheck" %% "scalacheck" % scalaCheckVersion
  val specs2Test     = "org.specs2" %% "specs2-core" % specs2Version
  val specs2ScalaCheckTest = "org.specs2" %% "specs2-scalacheck" % specs2Version
  val akkaStream           = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaActors           = "com.typesafe.akka" %% "akka-actor"  % akkaVersion
  val slacks               = "org.slacks" %% "slacks" % slacksVersion

  val circeJson            =  Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)
  val logger = Seq(
    "ch.qos.logback" % "logback-classic" % logbackClassic,
    "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggerVersion
    )
  val openTracing = Seq(
    "io.opentracing" % "opentracing-api" % openTracingVersion ,
    "io.opentracing" % "opentracing-util" % openTracingVersion,
    "com.uber.jaeger" % "jaeger-core" % jaegerCoreVersion)

  val flinkLibs = Seq(
    "org.apache.flink" % "flink-core" %                flinkVersion,
    "org.apache.flink" %% "flink-connector-cassandra" % flinkVersion,
    "org.apache.flink" %% "flink-scala" %               flinkVersion % "provided",
    "org.apache.flink" %% "flink-streaming-scala" %     flinkVersion % "provided")

  // Grouping the libraries to logical units
  val generalLibs = Seq(cats, akkaHttp, akkaStream, akkaActors, slacks) ++ logger ++ circeJson ++ openTracing ++ flinkLibs

  val testLibs = Seq(akkaHttpTest, specs2ScalaCheckTest, specs2Test).map( _ % Test )

}
