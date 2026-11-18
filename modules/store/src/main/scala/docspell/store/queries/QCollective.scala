/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.queries

import fs2.Stream

import docspell.common._
import docspell.store.qb.DSL._
import docspell.store.qb._
import docspell.store.records._

import doobie._
import doobie.implicits._

object QCollective {
  private val ti = RTagItem.as("ti")
  private val t = RTag.as("t")
  private val i = RItem.as("i")

  case class Names(org: Vector[String], pers: Vector[String], equip: Vector[String])
  object Names {
    val empty = Names(Vector.empty, Vector.empty, Vector.empty)
  }

  case class InsightData(
      incoming: Int,
      outgoing: Int,
      deleted: Int,
      bytes: Long,
      tags: List[TagCount]
  )

  def getInsights(coll: CollectiveId): ConnectionIO[InsightData] = {
    val q0 = Select(
      count(i.id).s,
      from(i),
      i.cid === coll && i.incoming === Direction.incoming && i.state.in(
        ItemState.validStates
      )
    ).build.query[Int].unique
    val q1 = Select(
      count(i.id).s,
      from(i),
      i.cid === coll && i.incoming === Direction.outgoing && i.state.in(
        ItemState.validStates
      )
    ).build.query[Int].unique
    val q2 = Select(
      count(i.id).s,
      from(i),
      i.cid === coll && i.state === ItemState.Deleted
    ).build.query[Int].unique

    val fileSize = sql"""
      select sum(length) from (
      with attachs as
            (select a.attachid as aid, a.filemetaid as fid
             from attachment a
             inner join item i on a.itemid = i.itemid
             where i.coll_id = $coll)
         select a.fid,m.length from attachs a
         inner join filemeta m on m.file_id = a.fid
         union distinct
         select a.file_id,m.length from attachment_source a
         inner join filemeta m on m.file_id = a.file_id where a.id in (select aid from attachs)
         union distinct
         select p.file_id,m.length from attachment_preview p
         inner join filemeta m on m.file_id = p.file_id where p.id in (select aid from attachs)
         union distinct
         select a.file_id,m.length from attachment_archive a
         inner join filemeta m on m.file_id = a.file_id where a.id in (select aid from attachs)
      ) as t""".query[Option[Long]].unique

    for {
      incoming <- q0
      outgoing <- q1
      size <- fileSize
      tags <- tagCloud(coll)
      deleted <- q2
    } yield InsightData(incoming, outgoing, deleted, size.getOrElse(0L), tags)
  }

  def tagCloud(coll: CollectiveId): ConnectionIO[List[TagCount]] = {
    val sql =
      Select(
        select(t.all).append(count(ti.itemId).s),
        from(ti).innerJoin(t, ti.tagId === t.tid).innerJoin(i, i.id === ti.itemId),
        t.cid === coll && i.state.in(ItemState.validStates)
      ).groupBy(t.name, t.tid, t.category)

    sql.build.query[TagCount].to[List]
  }

  def getContacts(
      coll: CollectiveId,
      query: Option[String],
      kind: Option[ContactKind]
  ): Stream[ConnectionIO, RContact] =
    Stream.evals(QLegacyEntity.contactsOf(coll, query, kind))
}
