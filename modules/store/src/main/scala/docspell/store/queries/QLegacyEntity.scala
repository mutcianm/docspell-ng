/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.queries

import cats.implicits._

import docspell.common._
import docspell.store.records._

import doobie._

/** Read-only views of entities in the shape of the former person, organization, equipment
  * and contact records, for the compatibility API (#24).
  */
object QLegacyEntity {
  private val contactKinds: Map[String, ContactKind] = Map(
    "phone" -> ContactKind.Phone,
    "mobile" -> ContactKind.Mobile,
    "fax" -> ContactKind.Fax,
    "email" -> ContactKind.Email,
    "website" -> ContactKind.Website
  )

  private def entities(
      coll: CollectiveId,
      typeKey: Ident,
      nameQuery: Option[String]
  ): ConnectionIO[Vector[QEntity.EntityData]] =
    REntityType
      .findByKey(typeKey, coll)
      .flatMap(_.traverse(t => QEntity.findEntities(coll, Some(t.id), nameQuery)))
      .map(_.getOrElse(Vector.empty))

  private def attr(d: QEntity.EntityData, name: String): Option[String] =
    d.attrs.find(_.attr.name.id == name).map(_.value.value)

  private def contacts(
      d: QEntity.EntityData,
      person: Boolean
  ): Vector[RContact] =
    d.attrs.flatMap { a =>
      contactKinds.get(a.attr.name.id).map { kind =>
        val id = d.entity.id.some
        RContact(
          a.value.id,
          a.value.value,
          kind,
          if (person) id else None,
          if (person) None else id,
          d.entity.created
        )
      }
    }

  private def address(d: QEntity.EntityData, f: String) = attr(d, f).getOrElse("")

  def organizations(
      coll: CollectiveId,
      nameQuery: Option[String]
  ): ConnectionIO[Vector[(ROrganization, Vector[RContact])]] =
    entities(coll, REntityType.Key.organization, nameQuery).map(_.map { d =>
      val e = d.entity
      val org = ROrganization(
        e.id,
        e.cid,
        e.name,
        address(d, "street"),
        address(d, "zip"),
        address(d, "city"),
        address(d, "country"),
        e.notes,
        e.created,
        e.updated,
        attr(d, "shortname"),
        if (e.suggest) OrgUse.Correspondent else OrgUse.Disabled
      )
      (org, contacts(d, person = false))
    })

  def persons(
      coll: CollectiveId,
      nameQuery: Option[String]
  ): ConnectionIO[Vector[(RPerson, Vector[RContact])]] =
    entities(coll, REntityType.Key.person, nameQuery).map(_.map { d =>
      val e = d.entity
      val person = RPerson(
        e.id,
        e.cid,
        e.name,
        address(d, "street"),
        address(d, "zip"),
        address(d, "city"),
        address(d, "country"),
        e.notes,
        e.created,
        e.updated,
        None,
        if (e.suggest) PersonUse.Both else PersonUse.Disabled
      )
      (person, contacts(d, person = true))
    })

  def equipments(
      coll: CollectiveId,
      nameQuery: Option[String]
  ): ConnectionIO[Vector[REquipment]] =
    entities(coll, REntityType.Key.equipment, nameQuery).map(_.map { d =>
      val e = d.entity
      REquipment(
        e.id,
        e.cid,
        e.name,
        e.created,
        e.updated,
        e.notes,
        if (e.suggest) EquipmentUse.Concerning else EquipmentUse.Disabled
      )
    })

  def contactsOf(
      coll: CollectiveId,
      valueQuery: Option[String],
      kind: Option[ContactKind]
  ): ConnectionIO[Vector[RContact]] =
    for {
      orgs <- organizations(coll, None)
      pers <- persons(coll, None)
    } yield (orgs.flatMap(_._2) ++ pers.flatMap(_._2))
      .filter(c => kind.forall(_ == c.kind))
      .filter(c => valueQuery.forall(q => c.value.toLowerCase.contains(q.toLowerCase)))
      .sortBy(_.value)
}
