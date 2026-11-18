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

/** How an item relates to an entity. Built-in roles are identified by `key`. */
case class REntityRole(
    id: Ident,
    cid: CollectiveId,
    key: Option[Ident],
    name: Ident,
    label: Option[String],
    builtin: Boolean,
    multi: Boolean,
    learn: Boolean,
    created: Timestamp
)

object REntityRole {
  object Key {
    val sender: Ident = Ident.unsafe("sender")
    val recipient: Ident = Ident.unsafe("recipient")
    val concerning: Ident = Ident.unsafe("concerning")
  }

  final case class Table(alias: Option[String]) extends TableDef {
    val tableName = "entity_role"

    val id = Column[Ident]("id", this)
    val cid = Column[CollectiveId]("coll_id", this)
    val key = Column[Ident]("role_key", this)
    val name = Column[Ident]("name", this)
    val label = Column[String]("label", this)
    val builtin = Column[Boolean]("builtin", this)
    val multi = Column[Boolean]("multi", this)
    val learn = Column[Boolean]("learn", this)
    val created = Column[Timestamp]("created", this)

    val all =
      NonEmptyList
        .of[Column[_]](id, cid, key, name, label, builtin, multi, learn, created)
  }

  val T = Table(None)
  def as(alias: String): Table =
    Table(Some(alias))

  def insert(v: REntityRole): ConnectionIO[Int] =
    DML.insert(
      T,
      T.all,
      fr"${v.id},${v.cid},${v.key},${v.name},${v.label},${v.builtin},${v.multi},${v.learn},${v.created}"
    )

  def findById(id: Ident, coll: CollectiveId): ConnectionIO[Option[REntityRole]] =
    run(select(T.all), from(T), T.id === id && T.cid === coll).query[REntityRole].option

  def findByKey(key: Ident, coll: CollectiveId): ConnectionIO[Option[REntityRole]] =
    run(select(T.all), from(T), T.key === key && T.cid === coll).query[REntityRole].option

  def findAll(coll: CollectiveId): ConnectionIO[Vector[REntityRole]] =
    Select(select(T.all), from(T), T.cid === coll)
      .orderBy(T.name.asc)
      .build
      .query[REntityRole]
      .to[Vector]

  def update(v: REntityRole): ConnectionIO[Int] =
    DML.update(
      T,
      T.id === v.id && T.cid === v.cid,
      DML.set(
        T.name.setTo(v.name),
        T.label.setTo(v.label),
        T.multi.setTo(v.multi),
        T.learn.setTo(v.learn)
      )
    )

  def deleteById(id: Ident, coll: CollectiveId): ConnectionIO[Int] =
    DML.delete(T, T.id === id && T.cid === coll)
}
