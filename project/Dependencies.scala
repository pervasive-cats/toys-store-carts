import sbt._

object Dependencies {

  lazy val scalactic: ModuleID = "org.scalactic" %% "scalactic" % "3.2.15"

  lazy val scalatest: ModuleID = "org.scalatest" %% "scalatest" % "3.2.15" % Test

  lazy val refined: ModuleID = "eu.timepit" %% "refined" % "0.10.3"

  lazy val postgresql: ModuleID = "org.postgresql" % "postgresql" % "42.6.0"

  lazy val quill: ModuleID = "io.getquill" %% "quill-jdbc" % "4.6.0.1"

  lazy val testContainers: ModuleID = "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.40.14" % Test

  lazy val testContainersPostgresql: ModuleID = "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.40.14" % Test

  lazy val akka: ModuleID = "com.typesafe.akka" %% "akka-actor-typed" % "2.8.0"

  lazy val akkaStream: ModuleID = "com.typesafe.akka" %% "akka-stream" % "2.8.0"

  lazy val akkaHttp: ModuleID = "com.typesafe.akka" %% "akka-http" % "10.5.0"

  lazy val akkaHttpSprayJson: ModuleID = "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.0"

  lazy val akkaTestKit: ModuleID = "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.8.0" % Test

  lazy val rabbitMQ: ModuleID = "com.rabbitmq" % "amqp-client" % "5.17.0"

  lazy val akkaStreamTestkit: ModuleID = "com.typesafe.akka" %% "akka-stream-testkit" % "2.8.0" % Test

  lazy val akkaHttpTestkit: ModuleID = "com.typesafe.akka" %% "akka-http-testkit" % "10.5.0" % Test
  
  lazy val ditto: ModuleID = "org.eclipse.ditto" % "ditto-client" % "3.2.0"
}
