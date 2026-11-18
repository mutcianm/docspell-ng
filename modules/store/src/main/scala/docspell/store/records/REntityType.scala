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

/** A kind of entity items can be linked to. Built-in types are identified by `key`. */
case class REntityType(
    id: Ident,
    cid: CollectiveId,
    key: Option[Ident],
    name: Ident,
    label: Option[String],
    builtin: Boolean,
    recognize: Boolean,
    created: Timestamp
)

object REntityType {
  object Key {
    val person: Ident = Ident.unsafe("person")
    val organization: Ident = Ident.unsafe("organization")
    val equipment: Ident = Ident.unsafe("equipment")
  }

  final case class Table(alias: Option[String]) extends TableDef {
    val tableName = "entity_type"

    val id = Column[Ident]("id", this)
    val cid = Column[CollectiveId]("coll_id", this)
    val key = Column[Ident]("type_key", this)
    val name = Column[Ident]("name", this)
    val label = Column[String]("label", this)
    val builtin = Column[Boolean]("builtin", this)
    val recognize = Column[Boolean]("recognize", this)
    val created = Column[Timestamp]("created", this)

    val all =
      NonEmptyList.of[Column[_]](id, cid, key, name, label, builtin, recognize, created)
  }

  val T = Table(None)
  def as(alias: String): Table =
    Table(Some(alias))

  def insert(v: REntityType): ConnectionIO[Int] =
    DML.insert(
      T,
      T.all,
      fr"${v.id},${v.cid},${v.key},${v.name},${v.label},${v.builtin},${v.recognize},${v.created}"
    )

  def findById(id: Ident, coll: CollectiveId): ConnectionIO[Option[REntityType]] =
    run(select(T.all), from(T), T.id === id && T.cid === coll).query[REntityType].option

  def findByKey(key: Ident, coll: CollectiveId): ConnectionIO[Option[REntityType]] =
    run(select(T.all), from(T), T.key === key && T.cid === coll).query[REntityType].option

  def findAll(coll: CollectiveId): ConnectionIO[Vector[REntityType]] =
    Select(select(T.all), from(T), T.cid === coll)
      .orderBy(T.name.asc)
      .build
      .query[REntityType]
      .to[Vector]

  def update(v: REntityType): ConnectionIO[Int] =
    DML.update(
      T,
      T.id === v.id && T.cid === v.cid,
      DML.set(
        T.name.setTo(v.name),
        T.label.setTo(v.label),
        T.recognize.setTo(v.recognize)
      )
    )

  def deleteById(id: Ident, coll: CollectiveId): ConnectionIO[Int] =
    DML.delete(T, T.id === id && T.cid === coll)
}
