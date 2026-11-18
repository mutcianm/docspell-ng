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

/** An attribute of an entity type; values are validated by `ftype`. */
case class RAttrDef(
    id: Ident,
    typeId: Ident,
    name: Ident,
    label: Option[String],
    ftype: CustomFieldType,
    multi: Boolean,
    matcher: EntityMatcher,
    created: Timestamp
)

object RAttrDef {
  final case class Table(alias: Option[String]) extends TableDef {
    val tableName = "attr_def"

    val id = Column[Ident]("id", this)
    val typeId = Column[Ident]("type_id", this)
    val name = Column[Ident]("name", this)
    val label = Column[String]("label", this)
    val ftype = Column[CustomFieldType]("ftype", this)
    val multi = Column[Boolean]("multi", this)
    val matcher = Column[EntityMatcher]("matcher", this)
    val created = Column[Timestamp]("created", this)

    val all =
      NonEmptyList.of[Column[_]](id, typeId, name, label, ftype, multi, matcher, created)
  }

  val T = Table(None)
  def as(alias: String): Table =
    Table(Some(alias))

  def insert(v: RAttrDef): ConnectionIO[Int] =
    DML.insert(
      T,
      T.all,
      fr"${v.id},${v.typeId},${v.name},${v.label},${v.ftype},${v.multi},${v.matcher},${v.created}"
    )

  def findById(id: Ident): ConnectionIO[Option[RAttrDef]] =
    run(select(T.all), from(T), T.id === id).query[RAttrDef].option

  def findByType(typeId: Ident): ConnectionIO[Vector[RAttrDef]] =
    Select(select(T.all), from(T), T.typeId === typeId)
      .orderBy(T.name.asc)
      .build
      .query[RAttrDef]
      .to[Vector]

  def update(v: RAttrDef): ConnectionIO[Int] =
    DML.update(
      T,
      T.id === v.id,
      DML.set(
        T.name.setTo(v.name),
        T.label.setTo(v.label),
        T.ftype.setTo(v.ftype),
        T.multi.setTo(v.multi),
        T.matcher.setTo(v.matcher)
      )
    )

  def deleteById(id: Ident): ConnectionIO[Int] =
    DML.delete(T, T.id === id)
}
