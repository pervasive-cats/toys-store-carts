/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats

import java.nio.file.Paths

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory

import application.actors.RootActor

@main
def main(path: String): Unit =
  ActorSystem(
    RootActor(ConfigFactory.parseFile(Paths.get(path).toFile).withFallback(ConfigFactory.defaultApplication())),
    name = "root_actor"
  )
