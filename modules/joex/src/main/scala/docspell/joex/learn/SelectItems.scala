/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.joex.learn

import cats.data.{NonEmptyList => Nel}
import fs2.{Pipe, Stream}

import docspell.analysis.classifier.TextClassifier.Data
import docspell.common._
import docspell.store.Store
import docspell.store.qb.Batch
import docspell.store.queries.{QItem, TextAndTag}
import docspell.store.records.{REntityRole, REntityType}

import doobie._

object SelectItems {
  val pageSep = LearnClassifierTask.pageSep
  val noClass = LearnClassifierTask.noClass

  def forCategory[F[_]](store: Store[F], collective: CollectiveId)(
      maxItems: Int,
      category: String,
      maxTextLen: Int
  ): Stream[F, Data] = {
    val connStream =
      allItems(collective, maxItems)
        .evalMap(item =>
          QItem.resolveTextAndTag(collective, item, category, maxTextLen, pageSep)
        )
        .through(mkData)
    store.transact(connStream)
  }

  private val correspondent = (d: Direction) => Nel.of(REntityRole.Key.correspondent(d))
  private val own = (d: Direction) => Nel.of(REntityRole.Key.own(d))
  private val concerning = (_: Direction) => Nel.of(REntityRole.Key.concerning)

  def forCorrOrg[F[_]](
      store: Store[F],
      collective: CollectiveId,
      maxItems: Int,
      maxTextLen: Int
  ): Stream[F, Data] =
    forLinkedEntity(store, collective, maxItems, maxTextLen)(
      correspondent,
      REntityType.Key.organization
    )

  def forCorrPerson[F[_]](
      store: Store[F],
      collective: CollectiveId,
      maxItems: Int,
      maxTextLen: Int
  ): Stream[F, Data] =
    forLinkedEntity(store, collective, maxItems, maxTextLen)(
      correspondent,
      REntityType.Key.person
    )

  def forConcPerson[F[_]](
      store: Store[F],
      collective: CollectiveId,
      maxItems: Int,
      maxTextLen: Int
  ): Stream[F, Data] =
    forLinkedEntity(store, collective, maxItems, maxTextLen)(
      own,
      REntityType.Key.person
    )

  def forConcEquip[F[_]](
      store: Store[F],
      collective: CollectiveId,
      maxItems: Int,
      maxTextLen: Int
  ): Stream[F, Data] =
    forLinkedEntity(store, collective, maxItems, maxTextLen)(
      concerning,
      REntityType.Key.equipment
    )

  private def forLinkedEntity[F[_]](
      store: Store[F],
      collective: CollectiveId,
      maxItems: Int,
      maxTextLen: Int
  )(roleKeys: Direction => Nel[Ident], typeKey: Ident): Stream[F, Data] = {
    val connStream =
      allItems(collective, maxItems)
        .evalMap(item =>
          QItem.resolveTextAndLinkedEntity(
            collective,
            item,
            roleKeys,
            typeKey,
            maxTextLen,
            pageSep
          )
        )
        .through(mkData)
    store.transact(connStream)
  }

  private def allItems(
      collective: CollectiveId,
      max: Int
  ): Stream[ConnectionIO, Ident] = {
    val limit = if (max <= 0) Batch.all else Batch.limit(max)
    QItem.findAllNewesFirst(collective, 10, limit)
  }

  private def mkData[F[_]]: Pipe[F, TextAndTag, Data] =
    _.map(tt => Data(tt.tag.map(_.name).getOrElse(noClass), tt.itemId.id, tt.text.trim))
      .filter(_.text.nonEmpty)
}
