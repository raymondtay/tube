// http://www.scalastyle.org/sbt.html 
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
// https://github.com/scoverage/sbt-scoverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
// https://github.com/scoverage/sbt-coveralls to push code coverage to
// coveralls
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.5")
// for autoplugins
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.1")
// for viewing the dependencies
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")
