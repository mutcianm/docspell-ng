/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.common

import cats.data.NonEmptyList

import io.circe.Decoder
import io.circe.Encoder

/** Marks entity attribute values that are searched for in document text and mail headers. */
sealed abstract class EntityMatcher(val name: String)

object EntityMatcher {

  case object NoMatch extends EntityMatcher("none")
  case object Alias extends EntityMatcher("alias")
  case object Email extends EntityMatcher("email")
  case object Website extends EntityMatcher("website")

  def none: EntityMatcher = NoMatch
  def alias: EntityMatcher = Alias
  def email: EntityMatcher = Email
  def website: EntityMatcher = Website

  val all: NonEmptyList[EntityMatcher] =
    NonEmptyList.of(none, alias, email, website)

  def fromString(str: String): Either[String, EntityMatcher] =
    all.find(_.name.equalsIgnoreCase(str)).toRight(s"Unknown entity matcher: $str")

  def unsafeFromString(str: String): EntityMatcher =
    fromString(str).fold(sys.error, identity)

  implicit val jsonDecoder: Decoder[EntityMatcher] =
    Decoder.decodeString.emap(fromString)

  implicit val jsonEncoder: Encoder[EntityMatcher] =
    Encoder.encodeString.contramap(_.name)
}
