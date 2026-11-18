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

/** Restricts a role to entity types; a role without targets accepts any type. */
case class REntityRoleTarget(roleId: Ident, typeId: Ident)

object REntityRoleTarget {
  final case class Table(alias: Option[String]) extends TableDef {
    val tableName = "entity_role_target"

    val roleId = Column[Ident]("role_id", this)
    val typeId = Column[Ident]("type_id", this)

    val all = NonEmptyList.of[Column[_]](roleId, typeId)
  }

  val T = Table(None)
  def as(alias: String): Table =
    Table(Some(alias))

  def insert(v: REntityRoleTarget): ConnectionIO[Int] =
    DML.insert(T, T.all, fr"${v.roleId},${v.typeId}")

  def findByRole(roleId: Ident): ConnectionIO[Vector[Ident]] =
    run(select(T.typeId), from(T), T.roleId === roleId).query[Ident].to[Vector]

  def deleteByRole(roleId: Ident): ConnectionIO[Int] =
    DML.delete(T, T.roleId === roleId)

  def deleteByType(typeId: Ident): ConnectionIO[Int] =
    DML.delete(T, T.typeId === typeId)
}
