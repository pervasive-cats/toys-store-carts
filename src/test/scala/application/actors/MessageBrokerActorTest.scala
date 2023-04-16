/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.*

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.MapHasAsJava

import io.github.pervasivecats.application.actors.commands.MessageBrokerCommand
import io.github.pervasivecats.application.actors.commands.MessageBrokerCommand.CartAssociated
import io.github.pervasivecats.application.actors.commands.MessageBrokerCommand.ItemAddedToCart
import io.github.pervasivecats.application.actors.commands.RootCommand
import io.github.pervasivecats.application.actors.commands.RootCommand.Startup

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import com.dimafeng.testcontainers.ContainerDef
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.GenericContainer.DockerImage
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.rabbitmq.client.*
import com.typesafe.config.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import spray.json.enrichString

import application.routes.entities.Response.EmptyResponse
import application.Serializers.given
import application.routes.entities.Entity.ResultResponseEntity
import carts.cart.domainevents.{CartAssociated as CartAssociatedEvent, ItemAddedToCart as ItemAddedToCartEvent}
import carts.cart.valueobjects.{CartId, Customer, Store}
import carts.cart.valueobjects.item.{CatalogItem, ItemId}

class MessageBrokerActorTest extends AnyFunSpec with TestContainerForAll with BeforeAndAfterAll {

  override val containerDef: GenericContainer.Def[GenericContainer] = GenericContainer.Def(
    dockerImage = DockerImage(Left[String, Future[String]]("rabbitmq:3.11.7")),
    exposedPorts = Seq(5672),
    env = Map(
      "RABBITMQ_DEFAULT_USER" -> "test",
      "RABBITMQ_DEFAULT_PASS" -> "test"
    ),
    waitStrategy = LogMessageWaitStrategy().withRegEx("^.*?Server startup complete.*?$")
  )

  private val testKit: ActorTestKit = ActorTestKit()
  private val rootActorProbe: TestProbe[RootCommand] = testKit.createTestProbe[RootCommand]()
  private val itemsQueue: BlockingQueue[Map[String, String]] = LinkedBlockingDeque()
  private val shoppingQueue: BlockingQueue[Map[String, String]] = LinkedBlockingDeque()

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var messageBroker: Option[ActorRef[MessageBrokerCommand]] = None

  private val customer: Customer = Customer("mario@email.com").getOrElse(fail())
  private val catalogItem: CatalogItem = CatalogItem(1).getOrElse(fail())
  private val store: Store = Store(1).getOrElse(fail())
  private val itemId: ItemId = ItemId(1).getOrElse(fail())
  private val cartId: CartId = CartId(1).getOrElse(fail())

  private def forwardToQueue(queue: BlockingQueue[Map[String, String]]): DeliverCallback =
    (_: String, message: Delivery) =>
      queue.put(
        Map(
          "exchange" -> message.getEnvelope.getExchange,
          "routingKey" -> message.getEnvelope.getRoutingKey,
          "body" -> String(message.getBody, StandardCharsets.UTF_8),
          "contentType" -> message.getProperties.getContentType,
          "correlationId" -> message.getProperties.getCorrelationId,
          "replyTo" -> message.getProperties.getReplyTo
        )
      )

  override def afterContainersStart(containers: Containers): Unit = {
    val messageBrokerConfig: Config =
      ConfigFactory
        .load()
        .getConfig("messageBroker")
        .withValue(
          "portNumber",
          ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue())
        )
    messageBroker = Some(testKit.spawn(MessageBrokerActor(rootActorProbe.ref, messageBrokerConfig)))
    val factory: ConnectionFactory = ConnectionFactory()
    factory.setUsername(messageBrokerConfig.getString("username"))
    factory.setPassword(messageBrokerConfig.getString("password"))
    factory.setVirtualHost(messageBrokerConfig.getString("virtualHost"))
    factory.setHost(messageBrokerConfig.getString("hostName"))
    factory.setPort(messageBrokerConfig.getInt("portNumber"))
    val connection: Connection = factory.newConnection()
    val channel: Channel = connection.createChannel()
    val couples: Seq[(String, String)] = Seq(
      "carts" -> "items",
      "carts" -> "shopping"
    )
    val queueArgs: Map[String, String] = Map("x-dead-letter-exchange" -> "dead_letters")
    couples.flatMap(Seq(_, _)).distinct.foreach(e => channel.exchangeDeclare(e, BuiltinExchangeType.TOPIC, true))
    couples
      .flatMap((b1, b2) => Seq(b1 + "_" + b2, b2 + "_" + b1))
      .foreach(q => channel.queueDeclare(q, true, false, false, queueArgs.asJava))
    couples
      .flatMap((b1, b2) => Seq((b1, b1 + "_" + b2, b2), (b2, b2 + "_" + b1, b1)))
      .foreach((e, q, r) => channel.queueBind(q, e, r))
    channel.basicConsume("carts_items", true, forwardToQueue(itemsQueue), (_: String) => {})
    channel.basicConsume("carts_shopping", true, forwardToQueue(shoppingQueue), (_: String) => {})
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()

  describe("A message broker actor") {
    describe("after being created") {
      it("should notify its root actor about it") {
        rootActorProbe.expectMessage(10.seconds, Startup(true))
      }
    }

    describe("after being notified that an item has been added to a cart") {
      it("should notify the message broker") {
        val event: CartAssociatedEvent = CartAssociatedEvent(cartId, store, customer)
        messageBroker.getOrElse(fail()) ! CartAssociated(event)
        val shoppingMessage: Map[String, String] = shoppingQueue.poll(10, TimeUnit.SECONDS)
        shoppingMessage("exchange") shouldBe "carts"
        shoppingMessage("routingKey") shouldBe "shopping"
        shoppingMessage("contentType") shouldBe "application/json"
        shoppingMessage(
          "correlationId"
        ) should fullyMatch regex "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
        shoppingMessage("replyTo") shouldBe "carts"
        shoppingMessage("body").parseJson.convertTo[ResultResponseEntity[CartAssociatedEvent]].result shouldBe event
      }
    }

    describe("after being notified that a cart has been associated to a customer") {
      it("should notify the message broker") {
        val event: ItemAddedToCartEvent = ItemAddedToCartEvent(customer, store, catalogItem, itemId)
        messageBroker.getOrElse(fail()) ! ItemAddedToCart(event)
        val shoppingMessage: Map[String, String] = shoppingQueue.poll(10, TimeUnit.SECONDS)
        shoppingMessage("exchange") shouldBe "carts"
        shoppingMessage("routingKey") shouldBe "shopping"
        shoppingMessage("contentType") shouldBe "application/json"
        shoppingMessage(
          "correlationId"
        ) should fullyMatch regex "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
        shoppingMessage("replyTo") shouldBe "carts"
        shoppingMessage("body").parseJson.convertTo[ResultResponseEntity[ItemAddedToCartEvent]].result shouldBe event
        val itemsMessage: Map[String, String] = itemsQueue.poll(10, TimeUnit.SECONDS)
        itemsMessage("exchange") shouldBe "carts"
        itemsMessage("routingKey") shouldBe "items"
        itemsMessage("contentType") shouldBe "application/json"
        itemsMessage(
          "correlationId"
        ) should fullyMatch regex "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
        itemsMessage("replyTo") shouldBe "carts"
        itemsMessage("body").parseJson.convertTo[ResultResponseEntity[ItemAddedToCartEvent]].result shouldBe event
      }
    }
  }
}
