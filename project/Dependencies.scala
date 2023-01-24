import sbt._

object Dependencies {
  lazy val scalactic: ModuleID = "org.scalactic" %% "scalactic" % "3.2.15"
  lazy val scalatest: ModuleID = "org.scalatest" %% "scalatest" % "3.2.15" % Test
  lazy val refined: ModuleID = "eu.timepit" %% "refined" % "0.10.1"
  lazy val postgresql: ModuleID = "org.postgresql" % "postgresql" % "42.5.1"
  lazy val quill: ModuleID = "io.getquill" %% "quill-jdbc" % "4.6.0"
  lazy val testContainers: ModuleID = "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.40.12" % Test
  lazy val testContainersPostgresql: ModuleID = "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.40.12" % Test
}
