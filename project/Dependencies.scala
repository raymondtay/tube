import sbt._

object Dependencies {

  // Versions
  val catsVersion = "1.0.0-RC1"
  val akkaHttpVersion = "10.0.10"

  // Libraries
  val cats = "org.typelevel" %% "cats-core" % catsVersion
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion 
  val akkaHttpTest = "com.typesafe.akka" %% "akka-http-testkit" %  akkaHttpVersion 

  // Grouping the libraries to logical units
  val generalLibs = Seq(cats, akkaHttp)

  val testLibs = Seq(akkaHttpTest).map( _ % Test )

}
