/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors.commands

sealed trait RootCommand

object RootCommand {

  final case class Startup(success: Boolean) extends RootCommand
}
