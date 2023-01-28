/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart

import scala.util.Try

import com.typesafe.config.Config
import eu.timepit.refined.auto.autoUnwrap
import eu.timepit.refined.auto.given
import io.getquill.*

import carts.cart.valueobjects.{CartId, Customer, Store}
import carts.cart.valueobjects.Customer.WrongCustomerFormat
import carts.{Validated, ValidationError}
import carts.cart.entities.{AssociatedCart, Cart, LockedCart, UnlockedCart}
import AnyOps.*

trait Repository {

  def findById(cartId: CartId): Validated[Cart]

  def findByStore(store: Store): Validated[Set[Cart]]

  def add(store: Store): Validated[LockedCart]

  def update(cart: Cart): Validated[Unit]

  def remove(cart: Cart): Validated[Unit]
}

object Repository {

  case object OperationFailed extends ValidationError {

    override val message: String = "The operation on the given cart has failed"
  }

  case object CartAlreadyPresent extends ValidationError {

    override val message: String = "The cart is already present"
  }

  case object CartNotFound extends ValidationError {

    override val message: String = "The queried cart was not found"
  }

  private class PostgresRepository(ctx: PostgresJdbcContext[SnakeCase]) extends Repository {

    import ctx.*

    private case class Carts(cartId: Long, store: Long, movable: Boolean, customer: Option[String])

    private def protectFromException[A](f: => Validated[A]): Validated[A] = {
      Try(f).getOrElse(Left[ValidationError, A](OperationFailed))
    }

    private def queryById(cartId: CartId) = quote {
      query[Carts].filter(c => c.cartId === lift[Long](cartId.value))
    }

    private def validateCart(cart: Carts): Validated[Cart] =
      cart
        .customer
        .fold(
          for {
            cartId <- CartId(cart.cartId)
            store <- Store(cart.store)
          } yield (cartId, store, cart.movable) match {
            case (cartId, store, true) => UnlockedCart(cartId, store)
            case (cartId, store, false) => LockedCart(cartId, store)
          }
        )(email =>
          for {
            customer <- Customer(email)
            cartId <- CartId(cart.cartId)
            store <- Store(cart.store)
          } yield AssociatedCart(cartId, store, customer)
        )

    override def findById(cartId: CartId): Validated[Cart] = protectFromException {
      ctx
        .run(queryById(cartId))
        .map(validateCart)
        .headOption
        .getOrElse(Left[ValidationError, Cart](CartNotFound))
    }

    def findByStore(store: Store): Validated[Set[Cart]] = Try(
      ctx
        .run(query[Carts].filter(_.store === lift[Long](store.value)))
        .map(validateCart)
        .collect { case Right(value) => value }
        .toSet
    ).toEither.map(Right[ValidationError, Set[Cart]]).getOrElse(Left[ValidationError, Set[Cart]](OperationFailed))

    override def add(store: Store): Validated[LockedCart] = protectFromException {
      val cartId: Long = ctx.run(
        query[Carts]
          .insert(
            _.store -> lift[Long](store.value),
            _.movable -> false,
            _.customer -> None
          )
          .returningGenerated(_.cartId)
      )
      CartId(cartId).map(LockedCart(_, store))
    }

    def update(cart: Cart): Validated[Unit] = protectFromException {
      val res = cart match
        case cart: AssociatedCart =>
          ctx.run(
            queryById(cart.cartId).update(
              _.store -> lift[Long](cart.store.value),
              _.movable -> lift[Boolean](cart.movable),
              _.customer -> Some(lift[String](cart.customer.value))
            )
          )
        case _ =>
          ctx.run(
            queryById(cart.cartId)
              .update(
                _.store -> lift[Long](cart.store.value),
                _.movable -> lift[Boolean](cart.movable),
                _.customer -> None
              )
          )
      if (res !== 1L)
        Left[ValidationError, Unit](OperationFailed)
      else
        Right[ValidationError, Unit](())
    }

    override def remove(cart: Cart): Validated[Unit] = protectFromException {
      ctx.transaction {
        if (ctx.run(queryById(cart.cartId).delete) !== 1L)
          Left[ValidationError, Unit](OperationFailed)
        else
          Right[ValidationError, Unit](())
      }
    }
  }

  def apply(config: Config): Repository = PostgresRepository(PostgresJdbcContext[SnakeCase](SnakeCase, config))
}
