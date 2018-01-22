import sbt._

object Dependencies {

  // Versions
  val catsVersion       = "1.0.1"
  val akkaHttpVersion   = "10.0.10"
  val akkaVersion       = "2.4.19"
  val scalaCheckVersion = "1.13.4"
  val specs2Version     = "4.0.1"
  val circeVersion      = "0.9.0"
  val openTracingVersion = "0.30.0"
  val jaegerCoreVersion  = "0.22.0-RC2"
  val scalaLoggerVersion = "3.7.2"
  val logbackClassic     = "1.2.3"
  val fastparseVersion   = "1.0.0"
  val flinkVersion       = "1.4-SNAPSHOT"
  val shapelessVersion   = "2.3.2"
  val scoptVersion       = "3.7.0"
  val scalaTestVersion   = "3.0.4"
  val http4sVersion = "0.18.0-SNAPSHOT"
  val http4s = Seq(
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-literal" % circeVersion
  )

  // Libraries
  val cats           = "org.typelevel" %% "cats-core" % catsVersion
  val akkaHttp       = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaHttpTest   = "com.typesafe.akka" %% "akka-http-testkit" %  akkaHttpVersion
  val scalaCheckTest = "org.scalacheck" %% "scalacheck" % scalaCheckVersion
  val specs2Test     = Seq(
    "org.specs2" %% "specs2-matcher-extra" % specs2Version,
    "org.specs2" %% "specs2-core" % specs2Version
  )
  val specs2ScalaCheckTest = "org.specs2" %% "specs2-scalacheck" % specs2Version
  val akkaStream           = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaActors           = "com.typesafe.akka" %% "akka-actor"  % akkaVersion
  val fastparse            = "com.lihaoyi" %% "fastparse" % fastparseVersion
  val shapeless            = "com.chuusai" %% "shapeless" % shapelessVersion
  val scopt                = "com.github.scopt" %% "scopt" % scoptVersion

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

  val flinkTest = "org.apache.flink" % "flink-test-utils_2.11" % "1.4.0"
  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion

  // Grouping the libraries to logical units
  val generalLibs = Seq(cats, akkaHttp, akkaStream, akkaActors, shapeless, fastparse, scopt) ++ logger ++ circeJson ++ openTracing ++ flinkLibs ++ http4s

  val testLibs = Seq(akkaHttpTest, specs2ScalaCheckTest, scalaTest, flinkTest).map( _ % Test ) ++ circeJson.map(_ % Test) ++ specs2Test.map(_ % Test)

}
