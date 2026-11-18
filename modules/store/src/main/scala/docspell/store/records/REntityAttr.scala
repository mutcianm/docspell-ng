/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.records

import cats.data.NonEmptyList

import docspell.common._
import docspell.store.qb.DSL._
import docspell.store.qb._

import doobie._
import doobie.implicits._

/** One value of an entity attribute; multi-valued attributes have several rows. */
case class REntityAttr(
    id: Ident,
    entityId: Ident,
    attrDefId: Ident,
    value: String
)

object REntityAttr {
  final case class Table(alias: Option[String]) extends TableDef {
    val tableName = "entity_attr"

    val id = Column[Ident]("id", this)
    val entityId = Column[Ident]("entity_id", this)
    val attrDefId = Column[Ident]("attr_def_id", this)
    val value = Column[String]("value_", this)

    val all = NonEmptyList.of[Column[_]](id, entityId, attrDefId, value)
  }

  val T = Table(None)
  def as(alias: String): Table =
    Table(Some(alias))

  def insert(v: REntityAttr): ConnectionIO[Int] =
    DML.insert(T, T.all, fr"${v.id},${v.entityId},${v.attrDefId},${v.value}")

  def findByEntity(entityId: Ident): ConnectionIO[Vector[REntityAttr]] =
    run(select(T.all), from(T), T.entityId === entityId).query[REntityAttr].to[Vector]

  def countByAttrDef(attrDefId: Ident): ConnectionIO[Int] =
    run(select(count(T.id)), from(T), T.attrDefId === attrDefId).query[Int].unique

  def deleteByEntity(entityId: Ident): ConnectionIO[Int] =
    DML.delete(T, T.entityId === entityId)

  def deleteByAttrDef(attrDefId: Ident): ConnectionIO[Int] =
    DML.delete(T, T.attrDefId === attrDefId)
}
