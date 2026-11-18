/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.queries

import cats.data.{NonEmptyList => Nel, OptionT}
import cats.implicits._

import docspell.common._
import docspell.store.qb.DSL._
import docspell.store.qb._
import docspell.store.records._

import doobie._
import doobie.implicits._

object QEntity {
  private val e = REntity.as("e")
  private val a = REntityAttr.as("a")
  private val d = RAttrDef.as("d")
  private val ie = RItemEntity.as("ie")
  private val r = REntityRole.as("r")

  case class AttrValue(attr: RAttrDef, value: REntityAttr)
  case class EntityData(entity: REntity, attrs: Vector[AttrValue])
  case class ItemLink(link: RItemEntity, role: REntityRole, entity: REntity)

  def findEntities(
      coll: CollectiveId,
      typeId: Option[Ident],
      nameQuery: Option[String]
  ): ConnectionIO[Vector[EntityData]] = {
    val cond = e.cid === coll &&? typeId.map(e.typeId === _) &&?
      nameQuery.map(_.toLowerCase).map(q => e.name.like(s"%$q%"))
    for {
      entities <- Select(select(e.all), from(e), cond)
        .orderBy(e.name.asc)
        .build
        .query[REntity]
        .to[Vector]
      attrs <- findAttrs(entities.map(_.id))
    } yield entities.map(en => EntityData(en, attrs.getOrElse(en.id, Vector.empty)))
  }

  def findEntity(coll: CollectiveId, id: Ident): ConnectionIO[Option[EntityData]] =
    REntity
      .findById(id, coll)
      .flatMap(
        _.traverse(en =>
          findAttrs(Vector(en.id))
            .map(m => EntityData(en, m.getOrElse(en.id, Vector.empty)))
        )
      )

  private def findAttrs(ids: Vector[Ident]): ConnectionIO[Map[Ident, Vector[AttrValue]]] =
    Nel.fromFoldable(ids) match {
      case None => Map.empty[Ident, Vector[AttrValue]].pure[ConnectionIO]
      case Some(nel) =>
        Select(
          select(d.all, a.all),
          from(a).innerJoin(d, d.id === a.attrDefId),
          a.entityId.in(nel)
        ).orderBy(d.name.asc)
          .build
          .query[AttrValue]
          .to[Vector]
          .map(_.groupBy(_.value.entityId))
    }

  def findItemLinks(itemId: Ident, coll: CollectiveId): ConnectionIO[Vector[ItemLink]] =
    Select(
      select(ie.all, r.all, e.all),
      from(ie)
        .innerJoin(r, r.id === ie.roleId)
        .innerJoin(e, e.id === ie.entityId),
      ie.itemId === itemId && e.cid === coll
    ).orderBy(r.name.asc, e.name.asc).build.query[ItemLink].to[Vector]

  /** Links the entity under the role with the given key, replacing links of that role to
    * entities of the same type. Returns 0 if the role or entity does not exist.
    */
  def replaceLinkOfType(
      itemId: Ident,
      coll: CollectiveId,
      roleKey: Ident,
      entityId: Ident
  ): ConnectionIO[Int] =
    (for {
      role <- OptionT(REntityRole.findByKey(roleKey, coll))
      entity <- OptionT(REntity.findById(entityId, coll))
      sameType = Select(select(e.id), from(e), e.typeId === entity.typeId)
      _ <- OptionT.liftF(
        DML.delete(
          RItemEntity.T,
          RItemEntity.T.itemId === itemId && RItemEntity.T.roleId === role.id &&
            RItemEntity.T.entityId.in(sameType)
        )
      )
      id <- OptionT.liftF(Ident.randomId[ConnectionIO])
      n <- OptionT.liftF(RItemEntity.insert(RItemEntity(id, itemId, entityId, role.id)))
      _ <- OptionT.liftF(refreshItemColumns(itemId, coll))
    } yield n).getOrElse(0)

  /** Sets or clears the item's link to an entity of the built-in type under the built-in
    * role, replacing other links of that role to entities of the type. An entity of
    * another type is ignored. Used by single-valued legacy inputs (addon commands).
    */
  def setLinkOfType(
      itemId: Ident,
      coll: CollectiveId,
      roleKey: Ident,
      typeKey: Ident,
      entityId: Option[Ident]
  ): ConnectionIO[Int] =
    (for {
      role <- OptionT(REntityRole.findByKey(roleKey, coll))
      etype <- OptionT(REntityType.findByKey(typeKey, coll))
      sameType = Select(select(e.id), from(e), e.typeId === etype.id)
      _ <- OptionT.liftF(
        DML.delete(
          RItemEntity.T,
          RItemEntity.T.itemId === itemId && RItemEntity.T.roleId === role.id &&
            RItemEntity.T.entityId.in(sameType)
        )
      )
      valid <- OptionT.liftF(
        entityId.flatTraverse(id =>
          REntity.findById(id, coll).map(_.filter(_.typeId == etype.id))
        )
      )
      n <- OptionT.liftF(valid.traverse { en =>
        Ident
          .randomId[ConnectionIO]
          .flatMap(id => RItemEntity.insert(RItemEntity(id, itemId, en.id, role.id)))
      })
      _ <- OptionT.liftF(refreshItemColumns(itemId, coll))
    } yield n.getOrElse(0)).getOrElse(0)

  /** Copies the links of one item onto another, skipping links it already has, and
    * refreshes the target's columns. Used when merging items.
    */
  def copyLinks(from: Ident, to: Ident, coll: CollectiveId): ConnectionIO[Int] =
    for {
      source <- RItemEntity.findByItem(from)
      existing <- RItemEntity.findByItem(to).map(_.map(l => (l.entityId, l.roleId)).toSet)
      missing = source.filterNot(l => existing.contains((l.entityId, l.roleId)))
      n <- missing.traverse(l =>
        Ident
          .randomId[ConnectionIO]
          .flatMap(id => RItemEntity.insert(l.copy(id = id, itemId = to)))
      )
      _ <- refreshItemColumns(to, coll)
    } yield n.sum

  def firstLinked(
      itemId: Ident,
      coll: CollectiveId,
      roleKeys: Nel[Ident],
      typeKey: Ident
  ): ConnectionIO[Option[IdRef]] = {
    val t = REntityType.as("t")
    Select(
      select(e.id, e.name),
      from(ie)
        .innerJoin(r, r.id === ie.roleId)
        .innerJoin(e, e.id === ie.entityId)
        .innerJoin(t, t.id === e.typeId),
      ie.itemId === itemId && r.cid === coll && r.key.in(roleKeys) && t.key === typeKey
    ).orderBy(e.name.asc).limit(1).build.query[IdRef].option
  }

  /** Sets the item's corr/conc columns from its links: the first organization and person
    * linked as sender (incoming) or recipient (outgoing), and the first person and
    * equipment linked as concerning. Old read paths and the API's single-valued fields
    * use these columns.
    */
  def refreshItemColumns(itemId: Ident, coll: CollectiveId): ConnectionIO[Int] =
    OptionT(RItem.findByIdAndCollective(itemId, coll))
      .semiflatMap { item =>
        val corr = Nel.of(
          if (item.direction == Direction.Incoming) REntityRole.Key.sender
          else REntityRole.Key.recipient
        )
        val conc = Nel.of(REntityRole.Key.concerning)
        for {
          org <- firstLinked(itemId, coll, corr, REntityType.Key.organization)
          person <- firstLinked(itemId, coll, corr, REntityType.Key.person)
          concPerson <- firstLinked(itemId, coll, conc, REntityType.Key.person)
          concEquip <- firstLinked(itemId, coll, conc, REntityType.Key.equipment)
          n <- DML.update(
            RItem.T,
            RItem.T.id === itemId,
            DML.set(
              RItem.T.corrOrg.setTo(org.map(_.id)),
              RItem.T.corrPerson.setTo(person.map(_.id)),
              RItem.T.concPerson.setTo(concPerson.map(_.id)),
              RItem.T.concEquipment.setTo(concEquip.map(_.id))
            )
          )
        } yield n
      }
      .getOrElse(0)

  def countEntitiesOfType(typeId: Ident): ConnectionIO[Int] =
    run(select(count(e.id)), from(e), e.typeId === typeId).query[Int].unique

  def countLinksOfRole(roleId: Ident): ConnectionIO[Int] =
    run(select(count(ie.id)), from(ie), ie.roleId === roleId).query[Int].unique
}
