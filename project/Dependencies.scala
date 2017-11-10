import sbt._

object Dependencies {

  // Versions
  val catsVersion       = "1.0.0-RC1"
  val akkaHttpVersion   = "10.0.10"
  val akkaVersion       = "2.5.6"
  val scalaCheckVersion = "1.13.4"
  val specs2Version     = "4.0.1"
  val circeVersion      = "0.8.0"
  val openTracingVersion = "0.30.0"
  val jaegerCoreVersion  = "0.22.0-RC2"

  // Libraries
  val cats           = "org.typelevel" %% "cats-core" % catsVersion
  val akkaHttp       = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaHttpTest   = "com.typesafe.akka" %% "akka-http-testkit" %  akkaHttpVersion
  val scalaCheckTest = "org.scalacheck" %% "scalacheck" % scalaCheckVersion
  val specs2Test     = "org.specs2" %% "specs2-core" % specs2Version
  val specs2ScalaCheckTest = "org.specs2" %% "specs2-scalacheck" % specs2Version
  val akkaStream           = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaActors           = "com.typesafe.akka" %% "akka-actor"  % akkaVersion
  val circeJson            =  Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)

  val openTracing = Seq(
    "io.opentracing" % "opentracing-api" % openTracingVersion ,
    "io.opentracing" % "opentracing-util" % openTracingVersion,
    "com.uber.jaeger" % "jaeger-core" % jaegerCoreVersion)

  // Grouping the libraries to logical units
  val generalLibs = Seq(cats, akkaHttp, akkaStream, akkaActors) ++ circeJson ++ openTracing

  val testLibs = Seq(akkaHttpTest, specs2ScalaCheckTest, specs2Test).map( _ % Test )

}
