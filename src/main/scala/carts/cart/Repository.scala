/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts.cart

import javax.sql.DataSource

import scala.util.Try

import io.github.pervasivecats.Validated
import io.github.pervasivecats.ValidationError

import com.typesafe.config.Config
import eu.timepit.refined.auto.autoUnwrap
import eu.timepit.refined.auto.given
import io.getquill.*

import carts.cart.valueobjects.{CartId, Customer, Store}
import carts.cart.valueobjects.Customer.WrongCustomerFormat
import carts.cart.entities.{AssociatedCart, Cart, LockedCart, UnlockedCart}
import AnyOps.*
import carts.RepositoryOperationFailed

trait Repository {

  def findById(cartId: CartId, store: Store): Validated[Cart]

  def findByStore(store: Store): Validated[Set[Validated[Cart]]]

  def add(store: Store): Validated[LockedCart]

  def update(cart: Cart): Validated[Unit]

  def remove(cart: Cart): Validated[Unit]
}

object Repository {

  case object CartNotFound extends ValidationError {

    override val message: String = "The queried cart was not found"
  }

  private class PostgresRepository(ctx: PostgresJdbcContext[SnakeCase]) extends Repository {

    import ctx.*

    private case class Carts(cartId: Long, store: Long, movable: Boolean, customer: Option[String])

    private def protectFromException[A](f: => Validated[A]): Validated[A] = {
      Try(f).getOrElse(Left[ValidationError, A](RepositoryOperationFailed))
    }

    private def queryById(cartId: CartId, store: Store) = quote {
      query[Carts].filter(c => c.cartId === lift[Long](cartId.value) && c.store === lift[Long](store.value))
    }

    private def validateCart(cart: Carts): Validated[Cart] =
      cart
        .customer
        .fold(
          for {
            cartId <- CartId(cart.cartId)
            store <- Store(cart.store)
          } yield
            if cart.movable
            then UnlockedCart(cartId, store)
            else LockedCart(cartId, store)
        )(email =>
          for {
            customer <- Customer(email)
            cartId <- CartId(cart.cartId)
            store <- Store(cart.store)
          } yield AssociatedCart(cartId, store, customer)
        )

    override def findById(cartId: CartId, store: Store): Validated[Cart] = protectFromException {
      ctx
        .run(queryById(cartId, store))
        .map(validateCart)
        .headOption
        .getOrElse(Left[ValidationError, Cart](CartNotFound))
    }

    override def findByStore(store: Store): Validated[Set[Validated[Cart]]] =
      Try(
        ctx
          .run(query[Carts].filter(_.store === lift[Long](store.value)))
          .map(validateCart)
          .toSet
      )
        .toEither
        .map(Right[ValidationError, Set[Validated[Cart]]])
        .getOrElse(Left[ValidationError, Set[Validated[Cart]]](RepositoryOperationFailed))

    override def add(store: Store): Validated[LockedCart] = protectFromException {
      ctx.transaction {
        val nextId: Long =
          ctx
            .run(
              query[Carts]
                .filter(_.store === lift[Long](store.value))
                .map(_.cartId)
                .max
            )
            .fold(0L)(_ + 1)
        if (
          ctx.run(
            query[Carts]
              .insert(
                _.cartId -> lift[Long](nextId),
                _.store -> lift[Long](store.value),
                _.movable -> false,
                _.customer -> None
              )
          )
          !==
          1L
        )
          Left[ValidationError, LockedCart](RepositoryOperationFailed)
        else
          CartId(nextId).map(LockedCart(_, store))
      }
    }

    override def update(cart: Cart): Validated[Unit] = protectFromException {
      val res = cart match
        case cart: AssociatedCart =>
          ctx.run(
            queryById(cart.cartId, cart.store).update(
              _.movable -> lift[Boolean](cart.movable),
              _.customer -> Some(lift[String](cart.customer.value))
            )
          )
        case _ =>
          ctx.run(
            queryById(cart.cartId, cart.store)
              .update(
                _.movable -> lift[Boolean](cart.movable),
                _.customer -> None
              )
          )
      if (res !== 1L)
        Left[ValidationError, Unit](RepositoryOperationFailed)
      else
        Right[ValidationError, Unit](())
    }

    override def remove(cart: Cart): Validated[Unit] = protectFromException {
      if (ctx.run(queryById(cart.cartId, cart.store).delete) !== 1L)
        Left[ValidationError, Unit](RepositoryOperationFailed)
      else
        Right[ValidationError, Unit](())
    }
  }

  def apply(dataSource: DataSource): Repository = PostgresRepository(PostgresJdbcContext[SnakeCase](SnakeCase, dataSource))
}
