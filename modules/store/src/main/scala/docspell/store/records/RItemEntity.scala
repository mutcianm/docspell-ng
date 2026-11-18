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

/** Links an item to an entity under a role. */
case class RItemEntity(id: Ident, itemId: Ident, entityId: Ident, roleId: Ident)

object RItemEntity {
  final case class Table(alias: Option[String]) extends TableDef {
    val tableName = "item_entity"

    val id = Column[Ident]("id", this)
    val itemId = Column[Ident]("item_id", this)
    val entityId = Column[Ident]("entity_id", this)
    val roleId = Column[Ident]("role_id", this)

    val all = NonEmptyList.of[Column[_]](id, itemId, entityId, roleId)
  }

  val T = Table(None)
  def as(alias: String): Table =
    Table(Some(alias))

  def insert(v: RItemEntity): ConnectionIO[Int] =
    DML.insert(T, T.all, fr"${v.id},${v.itemId},${v.entityId},${v.roleId}")

  def findByItem(itemId: Ident): ConnectionIO[Vector[RItemEntity]] =
    run(select(T.all), from(T), T.itemId === itemId).query[RItemEntity].to[Vector]

  def findByEntity(entityId: Ident): ConnectionIO[Vector[RItemEntity]] =
    run(select(T.all), from(T), T.entityId === entityId).query[RItemEntity].to[Vector]

  def findByRole(roleId: Ident): ConnectionIO[Vector[RItemEntity]] =
    run(select(T.all), from(T), T.roleId === roleId).query[RItemEntity].to[Vector]

  def deleteByItemAndRole(itemId: Ident, roleId: Ident): ConnectionIO[Int] =
    DML.delete(T, T.itemId === itemId && T.roleId === roleId)

  def deleteByItem(itemId: Ident): ConnectionIO[Int] =
    DML.delete(T, T.itemId === itemId)

  def deleteByEntity(entityId: Ident): ConnectionIO[Int] =
    DML.delete(T, T.entityId === entityId)

  def deleteByRole(roleId: Ident): ConnectionIO[Int] =
    DML.delete(T, T.roleId === roleId)
}
