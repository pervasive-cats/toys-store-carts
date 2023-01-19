import sbt._

object Dependencies {
  lazy val scalactic: ModuleID = "org.scalactic" %% "scalactic" % "3.2.15"
  lazy val scalatest: ModuleID = "org.scalatest" %% "scalatest" % "3.2.15" % Test
}
