import sbt._

object Dependencies {

  // Versions
  val catsVersion       = "1.0.0-RC1"
  val akkaHttpVersion   = "10.0.10"
  val scalaCheckVersion = "1.13.4"
  val specs2Version     = "4.0.0"

  // Libraries
  val cats           = "org.typelevel" %% "cats-core" % catsVersion
  val akkaHttp       = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion 
  val akkaHttpTest   = "com.typesafe.akka" %% "akka-http-testkit" %  akkaHttpVersion 
  val scalaCheckTest = "org.scalacheck" %% "scalacheck" % scalaCheckVersion
  val specs2Test     = "org.specs2" %% "specs2-core" % specs2Version
  val specs2ScalaCheckTest = "org.specs2" %% "specs2-scalacheck" % specs2Version

  // Grouping the libraries to logical units
  val generalLibs = Seq(cats, akkaHttp)

  val testLibs = Seq(akkaHttpTest, specs2ScalaCheckTest, specs2Test).map( _ % Test )

}
