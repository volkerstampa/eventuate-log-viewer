organization := "com.github.volkerstampa"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.7"

javacOptions += "-Xlint:unchecked"

scalacOptions ++= Seq("-deprecation", "-feature")

fork in run := true

resolvers ++= Seq(
  "OJO Snapshots" at "https://oss.jfrog.org/oss-snapshot-local"
)


libraryDependencies ++= Seq(
  "com.rbmhtechnology" %% "eventuate" % "0.5-SNAPSHOT",
  "com.beust" % "jcommander" % "1.48"
)

enablePlugins(JavaAppPackaging)

executableScriptName := "SimpleLogViewer"

scriptClasspath += "../ext/*"