/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application

import java.io.File
import java.time.Duration

import com.dimafeng.testcontainers.DockerComposeContainer
import com.dimafeng.testcontainers.ExposedService
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.containers.Container
import org.testcontainers.containers.wait.strategy.Wait

@SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
class DittoContainerTest extends AnyFunSpec with TestContainerForAll {

  private val port = 8005

  override val containerDef: DockerComposeContainer.Def = DockerComposeContainer.Def(
    new File("src/test/resources/dittoContainer/docker-compose.yml"),
    tailChildContainers = true,
    env = Map("DITTO_EXTERNAL_PORT" -> port.toString),
    exposedServices = Seq(
      ExposedService("policies", port, Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(300))),
      ExposedService("things", port, Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(300))),
      ExposedService("things-search", port, Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(300))),
      ExposedService("connectivity", port, Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(300))),
      ExposedService("gateway", port, Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(300)))
    )
  )

  private var policiesPort: Option[Int] = None
  private var thingsPort: Option[Int] = None
  private var thingsSearchPort: Option[Int] = None
  private var connectivityPort: Option[Int] = None
  private var gatewayPort: Option[Int] = None

  override def afterContainersStart(containers: Containers): Unit = {
    policiesPort = Some(containers.container.getServicePort("policies", port).intValue())
    thingsPort = Some(containers.container.getServicePort("things", port).intValue())
    thingsSearchPort = Some(containers.container.getServicePort("things-search", port).intValue())
    connectivityPort = Some(containers.container.getServicePort("connectivity", port).intValue())
    gatewayPort = Some(containers.container.getServicePort("gateway", port).intValue())
  }

  describe("DockerComposeContainer") {
    describe("when initialized with eclipse-ditto's docker-compose") {
      it("should start an eclipse-ditto cluster and all services are assigned ports") {
        policiesPort.getOrElse(fail()) should be > 0
        thingsPort.getOrElse(fail()) should be > 0
        thingsSearchPort.getOrElse(fail()) should be > 0
        connectivityPort.getOrElse(fail()) should be > 0
        gatewayPort.getOrElse(fail()) should be > 0
      }
    }
  }
}
