/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes.entities

import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.JsNull
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.RootJsonFormat
import spray.json.deserializationError
import spray.json.enrichAny

import application.RequestProcessingFailed
import application.routes.Routes.{DeserializationFailed, RequestFailed}
import carts.cart.Repository.CartNotFound
import carts.cart.valueobjects.Customer.WrongCustomerFormat
import carts.cart.valueobjects.Store.WrongStoreFormat
import carts.cart.valueobjects.item.ItemId.WrongItemIdFormat
import carts.cart.valueobjects.CartId.WrongCartIdFormat
import carts.cart.valueobjects.item.CatalogItem.WrongCatalogItemFormat
import carts.RepositoryOperationFailed

trait Entity

object Entity {

  case class ResultResponseEntity[A](result: A) extends Entity

  given [A: JsonFormat]: RootJsonFormat[ResultResponseEntity[A]] with {

    override def read(json: JsValue): ResultResponseEntity[A] = json.asJsObject.getFields("result", "error") match {
      case Seq(result, JsNull) => ResultResponseEntity(result.convertTo[A])
      case _ => deserializationError(msg = "Json format was not valid")
    }

    override def write(response: ResultResponseEntity[A]): JsValue = JsObject(
      "result" -> response.result.toJson,
      "error" -> JsNull
    )
  }

  given [A: JsonFormat]: RootJsonFormat[Validated[A]] with {

    override def read(json: JsValue): Validated[A] = json.asJsObject.getFields("result", "error") match {
      case Seq(JsObject(_), JsNull) => Right[ValidationError, A](json.convertTo[ResultResponseEntity[A]].result)
      case Seq(JsNull, JsObject(_)) => Left[ValidationError, A](json.convertTo[ErrorResponseEntity].error)
    }

    override def write(validated: Validated[A]): JsValue = validated match {
      case Left(error) => ErrorResponseEntity(error).toJson
      case Right(value) => ResultResponseEntity(value).toJson
    }
  }

  case class ErrorResponseEntity(error: ValidationError) extends Entity

  given RootJsonFormat[ErrorResponseEntity] with {

    override def read(json: JsValue): ErrorResponseEntity = json.asJsObject.getFields("result", "error") match {
      case Seq(JsNull, error) =>
        error.asJsObject.getFields("type", "message") match {
          case Seq(JsString(tpe), JsString(message)) =>
            ErrorResponseEntity(tpe match {
              case "CartNotFound" => CartNotFound
              case "WrongItemIdFormat" => WrongItemIdFormat
              case "WrongCartIdFormat" => WrongCartIdFormat
              case "WrongStoreFormat" => WrongStoreFormat
              case "WrongCustomerFormat" => WrongCustomerFormat
              case "WrongCatalogItemFormat" => WrongCatalogItemFormat
              case "RepositoryOperationFailed" => RepositoryOperationFailed
              case "RequestProcessingFailed" => RequestProcessingFailed
              case "RequestFailed" => RequestFailed
              case "DeserializationFailed" => DeserializationFailed(message)
              case _ => deserializationError(msg = "Json format was not valid")
            })
          case _ => deserializationError(msg = "Json format was not valid")
        }
      case _ => deserializationError(msg = "Json format was not valid")
    }

    override def write(response: ErrorResponseEntity): JsValue = JsObject(
      "result" -> JsNull,
      "error" -> JsObject(
        "type" -> response.error.getClass.getSimpleName.replace("$", "").toJson,
        "message" -> response.error.message.toJson
      )
    )
  }
}
