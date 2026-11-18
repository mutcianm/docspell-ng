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

/** An entity of some type, e.g. a person or an organization. */
case class REntity(
    id: Ident,
    cid: CollectiveId,
    typeId: Ident,
    name: String,
    notes: Option[String],
    suggest: Boolean,
    created: Timestamp,
    updated: Timestamp
)

object REntity {
  final case class Table(alias: Option[String]) extends TableDef {
    val tableName = "entity"

    val id = Column[Ident]("id", this)
    val cid = Column[CollectiveId]("coll_id", this)
    val typeId = Column[Ident]("type_id", this)
    val name = Column[String]("name", this)
    val notes = Column[String]("notes", this)
    val suggest = Column[Boolean]("suggest", this)
    val created = Column[Timestamp]("created", this)
    val updated = Column[Timestamp]("updated", this)

    val all =
      NonEmptyList.of[Column[_]](id, cid, typeId, name, notes, suggest, created, updated)
  }

  val T = Table(None)
  def as(alias: String): Table =
    Table(Some(alias))

  def insert(v: REntity): ConnectionIO[Int] =
    DML.insert(
      T,
      T.all,
      fr"${v.id},${v.cid},${v.typeId},${v.name},${v.notes},${v.suggest},${v.created},${v.updated}"
    )

  def findById(id: Ident, coll: CollectiveId): ConnectionIO[Option[REntity]] =
    run(select(T.all), from(T), T.id === id && T.cid === coll).query[REntity].option

  def findByType(typeId: Ident, coll: CollectiveId): ConnectionIO[Vector[REntity]] =
    Select(select(T.all), from(T), T.typeId === typeId && T.cid === coll)
      .orderBy(T.name.asc)
      .build
      .query[REntity]
      .to[Vector]

  def update(v: REntity): ConnectionIO[Int] =
    DML.update(
      T,
      T.id === v.id && T.cid === v.cid,
      DML.set(
        T.name.setTo(v.name),
        T.notes.setTo(v.notes),
        T.suggest.setTo(v.suggest),
        T.updated.setTo(v.updated)
      )
    )

  def deleteById(id: Ident, coll: CollectiveId): ConnectionIO[Int] =
    DML.delete(T, T.id === id && T.cid === coll)
}
