/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.records

import cats.implicits._

import docspell.common._

import doobie._

/** The entity types and roles every collective starts with. Mirrors the seed in the
  * V1.40.0 migration, which creates them for collectives that existed before.
  */
object EntityBuiltins {
  private val contactAttrs: List[(String, String, Boolean, EntityMatcher)] = List(
    ("street", "Street", false, EntityMatcher.none),
    ("zip", "Zip code", false, EntityMatcher.none),
    ("city", "City", false, EntityMatcher.none),
    ("country", "Country", false, EntityMatcher.none),
    ("phone", "Phone", true, EntityMatcher.none),
    ("mobile", "Mobile", true, EntityMatcher.none),
    ("fax", "Fax", true, EntityMatcher.none),
    ("email", "E-Mail", true, EntityMatcher.email),
    ("website", "Website", true, EntityMatcher.website)
  )

  val attrs: Map[Ident, List[(String, String, Boolean, EntityMatcher)]] = Map(
    REntityType.Key.person -> contactAttrs,
    REntityType.Key.organization ->
      (contactAttrs :+ (("shortname", "Short name", true, EntityMatcher.alias))),
    REntityType.Key.equipment -> Nil
  )

  private val types: List[(Ident, String, Boolean)] = List(
    (REntityType.Key.person, "Person", true),
    (REntityType.Key.organization, "Organization", true),
    (REntityType.Key.equipment, "Equipment", false)
  )

  private val roles: List[(Ident, String, List[Ident])] = List(
    (
      REntityRole.Key.sender,
      "Sender",
      List(REntityType.Key.person, REntityType.Key.organization)
    ),
    (
      REntityRole.Key.recipient,
      "Recipient",
      List(REntityType.Key.person, REntityType.Key.organization)
    ),
    (REntityRole.Key.concerning, "Concerning", Nil)
  )

  def create(cid: CollectiveId, now: Timestamp): ConnectionIO[Unit] =
    for {
      typeIds <- types.traverse { case (key, label, builtin) =>
        for {
          id <- Ident.randomId[ConnectionIO]
          _ <- REntityType.insert(
            REntityType(id, cid, key.some, key, label.some, builtin, true, now)
          )
          _ <- attrs(key).traverse_ { case (name, attrLabel, multi, matcher) =>
            Ident.randomId[ConnectionIO].flatMap { aid =>
              RAttrDef.insert(
                RAttrDef(
                  aid,
                  id,
                  Ident.unsafe(name),
                  attrLabel.some,
                  CustomFieldType.text,
                  multi,
                  matcher,
                  now
                )
              )
            }
          }
        } yield key -> id
      }
      _ <- roles.traverse_ { case (key, label, targets) =>
        for {
          id <- Ident.randomId[ConnectionIO]
          _ <- REntityRole.insert(
            REntityRole(id, cid, key.some, key, label.some, true, true, true, now)
          )
          _ <- targets.traverse_(t =>
            REntityRoleTarget.insert(REntityRoleTarget(id, typeIds.toMap.apply(t)))
          )
        } yield ()
      }
    } yield ()
}
