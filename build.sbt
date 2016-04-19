name := """calculate-pi"""

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
	"org.scalatest" %% "scalatest" % "2.2.6" % "test",
	"com.typesafe.akka" %% "akka-actor" % "2.3.11"
)