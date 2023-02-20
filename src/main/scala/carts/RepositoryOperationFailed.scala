/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package carts

case object RepositoryOperationFailed extends ValidationError {

  override val message: String = "The operation on the repository was not correctly performed"
}
