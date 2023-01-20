import sbt._

object Dependencies {
  lazy val scalactic: ModuleID = "org.scalactic" %% "scalactic" % "3.2.15"
  lazy val scalatest: ModuleID = "org.scalatest" %% "scalatest" % "3.2.15" % Test
  lazy val refined: ModuleID = "eu.timepit" %% "refined" % "0.10.1"
}
