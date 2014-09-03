import scoverage.ScoverageSbtPlugin

val VERSION = "0.1-SNAPSHOT"

val SCALA_VERSION = "2.10.4"

val ORGANIZATION = "jp.seraphr.event"

val COMMON_DEPENDENCIES = Seq(
    "org.slf4j" % "slf4j-api" % "[1.7.0,)",
    "org.scalatest" %% "scalatest" % "2.0" % "test",
    "org.scalacheck" %% "scalacheck" % "1.10.1" % "test"
)

val COMMON_SETTINGS = Seq(
  organization := ORGANIZATION,
  version := VERSION,
  scalaVersion := SCALA_VERSION,
  testOptions in ScoverageTest += Tests.Argument("-oS", "-u", "target/junit"),
  EclipseKeys.withSource := true,
  EclipseKeys.withJavadoc := true,
  EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource,
  libraryDependencies ++= COMMON_DEPENDENCIES
) ++ ScoverageSbtPlugin.instrumentSettings


// sub projects
lazy val event = Project(
  id = "frp-event",
  base = file("./"),
  settings = Defaults.defaultSettings ++ COMMON_SETTINGS ++ Seq(
    name := "frp-event"
  )
)
