/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.queries

import cats.data.{NonEmptyList => Nel}

import docspell.common._
import docspell.store.qb.DSL._
import docspell.store.qb._
import docspell.store.records._

import doobie._
import doobie.implicits._

/** Finds suggestable entities by name, alias or matcher values, for proposals. */
object QEntityLookup {
  private val e = REntity.as("e")
  private val t = REntityType.as("t")
  private val d = RAttrDef.as("d")
  private val a = REntityAttr.as("a")

  private def ofType(coll: CollectiveId, typeKey: Ident): Condition =
    e.cid === coll && t.key === typeKey && e.suggest === true

  private def withMatcher(matchers: Nel[EntityMatcher], value: String): Select =
    Select(
      select(a.entityId),
      from(a).innerJoin(d, d.id === a.attrDefId),
      d.matcher.in(matchers) && a.value.like(value)
    )

  /** Entities of the type whose name or an alias matches the `like` pattern. */
  def findLike(
      coll: CollectiveId,
      typeKey: Ident,
      nameLike: String
  ): ConnectionIO[Vector[IdRef]] =
    Select(
      select(e.id, e.name),
      from(e).innerJoin(t, t.id === e.typeId),
      ofType(coll, typeKey) &&
        (e.name.like(nameLike) || e.id.in(
          withMatcher(Nel.of(EntityMatcher.alias), nameLike)
        ))
    ).build.query[IdRef].to[Vector]

  /** Entities of the type with a value of the given matcher matching the `like` pattern. */
  def findByMatcher(
      coll: CollectiveId,
      typeKey: Ident,
      matcher: EntityMatcher,
      valueLike: String
  ): ConnectionIO[Vector[IdRef]] =
    Select(
      select(e.id, e.name),
      from(e).innerJoin(t, t.id === e.typeId),
      ofType(coll, typeKey) && e.id.in(withMatcher(Nel.of(matcher), valueLike))
    ).build.query[IdRef].to[Vector]

  /** Names of organizations, persons and equipment, most recently created first. */
  def allNames(coll: CollectiveId, maxEntries: Int): ConnectionIO[QCollective.Names] =
    Select(
      select(e.name, t.key),
      from(e).innerJoin(t, t.id === e.typeId),
      e.cid === coll && t.key.in(
        Nel.of(
          REntityType.Key.organization,
          REntityType.Key.person,
          REntityType.Key.equipment
        )
      )
    ).orderBy(e.created.desc)
      .limit(Batch.limit(maxEntries))
      .build
      .query[(String, Ident)]
      .to[Vector]
      .map(_.foldLeft(QCollective.Names.empty) { case (names, (name, key)) =>
        if (key == REntityType.Key.organization) names.copy(org = names.org :+ name)
        else if (key == REntityType.Key.person) names.copy(pers = names.pers :+ name)
        else names.copy(equip = names.equip :+ name)
      })

  def latestUpdate(coll: CollectiveId): ConnectionIO[Option[Timestamp]] =
    Select(select(max(e.updated)), from(e), e.cid === coll).build
      .query[Option[Timestamp]]
      .option
      .map(_.flatten)
}
