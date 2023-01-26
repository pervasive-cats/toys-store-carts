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

  def findById(cartId: CartId, store: Store): Validated[Cart]

  def findByStore(store: Store): Validated[Set[Cart]]

  def add(cart: Cart): Validated[Unit]

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

  @SuppressWarnings(Array("scalafix:DisableSyntax.magicBooleans"))
  private class PostgresRepository(ctx: PostgresJdbcContext[SnakeCase]) extends Repository {

    import ctx.*

    private case class Carts(cartId: Long, store: Long, isMovable: Boolean, customer: Option[String])

    private def protectFromException[A](f: => Validated[A]): Validated[A] = {
      Try(f).getOrElse(Left[ValidationError, A](OperationFailed))
    }

    private def queryById(cartId: CartId, store: Store) = quote {
      querySchema[Carts](entity = "carts").filter(c =>
        c.cartId === lift[Long](cartId.value) && c.store === lift[Long](store.value)
      )
    }

    override def findById(cartId: CartId, store: Store): Validated[Cart] = protectFromException {
      ctx
        .run(queryById(cartId, store))
        .map(c => {
          c.customer match {
            case Some(email) => for customer <- Customer(email) yield AssociatedCart(cartId, store, customer)
            case None =>
              for {
                cartId <- CartId(c.cartId)
                store <- Store(c.store)
              } yield (cartId, store, c.isMovable) match {
                case (cartId, store, true) => UnlockedCart(cartId, store)
                case (cartId, store, false) => LockedCart(cartId, store)
              }
          }
        })
        .headOption
        .getOrElse(Left[ValidationError, Cart](CartNotFound))
    }

    def findByStore(store: Store): Validated[Set[Cart]] = protectFromException {
      Right[ValidationError, Set[Cart]](
        ctx
          .run(query[Carts].filter(_.store === lift[Long](store.value)))
          .map(c => {
            c.customer match {
              case Some(email) =>
                for {
                  customer <- Customer(email)
                  cartId <- CartId(c.cartId)
                  store <- Store(c.store)
                } yield AssociatedCart(cartId, store, customer)
              case None =>
                for {
                  cartId <- CartId(c.cartId)
                  store <- Store(c.store)
                } yield (cartId, store, c.isMovable) match {
                  case (cartId, store, true) => UnlockedCart(cartId, store)
                  case (cartId, store, false) => LockedCart(cartId, store)
                }
            }
          })
          .collect { case Right(value) => value }
          .toSet
      )
    }

    override def add(cart: Cart): Validated[Unit] = protectFromException {
      ctx.transaction {
        if (ctx.run(queryById(cart.cartId, cart.store).nonEmpty))
          Left[ValidationError, Unit](CartAlreadyPresent)
        else if (
          ctx.run(
            query[Carts]
              .insertValue(
                lift(
                  Carts(
                    cart.cartId.value,
                    cart.store.value,
                    cart.isMovable,
                    None
                  )
                )
              )
          ) !== 1L
        )
          Left[ValidationError, Unit](OperationFailed)
        else
          Right[ValidationError, Unit](())
      }
    }

    def update(cart: Cart): Validated[Unit] = protectFromException {
      cart match {
        case cart: AssociatedCart =>
          if (
            ctx.run(
              queryById(cart.cartId, cart.store).update(
                _.cartId -> lift[Long](cart.cartId.value),
                _.store -> lift[Long](cart.store.value),
                _.isMovable -> lift[Boolean](cart.isMovable),
                _.customer -> Some(lift[String](cart.customer.value))
              )
            ) !== 1L
          )
            Left[ValidationError, Unit](OperationFailed)
          else
            Right[ValidationError, Unit](())
        case _ =>
          if (
            ctx.run(
              queryById(cart.cartId, cart.store)
                .update(
                  _.cartId -> lift[Long](cart.cartId.value),
                  _.store -> lift[Long](cart.store.value),
                  _.isMovable -> lift[Boolean](cart.isMovable),
                  _.customer -> None
                )
            ) !== 1L
          )
            Left[ValidationError, Unit](OperationFailed)
          else
            Right[ValidationError, Unit](())
      }
    }

    override def remove(cart: Cart): Validated[Unit] = protectFromException {
      ctx.transaction {
        if (ctx.run(queryById(cart.cartId, cart.store).delete) !== 1L)
          Left[ValidationError, Unit](OperationFailed)
        else
          Right[ValidationError, Unit](())
      }
    }
  }

  def apply(config: Config): Repository = PostgresRepository(PostgresJdbcContext[SnakeCase](SnakeCase, config))
}
