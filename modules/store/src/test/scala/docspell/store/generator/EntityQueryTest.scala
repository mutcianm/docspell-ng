/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.generator

import java.time.LocalDate

import cats.effect._
import cats.implicits._

import docspell.common._
import docspell.query.ItemQueryParser
import docspell.store.qb.DSL._
import docspell.store.qb.Select
import docspell.store.qb.generator.{ItemQueryGenerator, Tables}
import docspell.store.records._
import docspell.store.{DatabaseTest, Store}

import doobie.implicits._

/** Runs role and entity attribute queries against a database. */
class EntityQueryTest extends DatabaseTest {

  override def munitFixtures = h2Memory

  private val tables = Tables(
    RItem.as("i"),
    ROrganization.as("co"),
    RPerson.as("cp"),
    RPerson.as("np"),
    REquipment.as("ne"),
    RFolder.as("f"),
    RAttachment.as("a"),
    RAttachmentMeta.as("m")
  )
  private val ts = Timestamp.Epoch
  private def id(s: String) = Ident.unsafe(s)

  private def prepare(store: Store[IO]): IO[CollectiveId] =
    store.transact(for {
      cid <- RCollective.insert(
        RCollective(
          CollectiveId.unknown,
          id("eq-coll"),
          CollectiveState.Active,
          Language.English,
          true,
          ts
        )
      )
      org <- REntityType.findByKey(REntityType.Key.organization, cid).map(_.get)
      email <- RAttrDef.findByType(org.id).map(_.find(_.name.id == "email").get)
      sender <- REntityRole.findByKey(REntityRole.Key.sender, cid).map(_.get)
      _ <- REntity.insert(REntity(id("acme"), cid, org.id, "Acme", None, true, ts, ts))
      _ <- REntityAttr.insert(
        REntityAttr(id("acme-mail"), id("acme"), email.id, "info@acme.test")
      )
      _ <- RItem.insert(
        RItem(
          id("i1"),
          cid,
          "i1",
          None,
          "test",
          Direction.Incoming,
          ItemState.Created,
          None,
          None,
          None,
          None,
          None,
          ts,
          ts,
          None,
          None
        )
      )
      _ <- RItem.insert(
        RItem(
          id("i2"),
          cid,
          "i2",
          None,
          "test",
          Direction.Incoming,
          ItemState.Created,
          None,
          None,
          None,
          None,
          None,
          ts,
          ts,
          None,
          None
        )
      )
      _ <- RItemEntity.insert(RItemEntity(id("l1"), id("i1"), id("acme"), sender.id))
    } yield cid)

  private def find(
      store: Store[IO],
      cid: CollectiveId,
      query: String
  ): IO[Set[String]] = {
    val cond = ItemQueryGenerator(LocalDate.of(2021, 1, 1), tables, cid)(
      ItemQueryParser.parseUnsafe(query)
    )
    store
      .transact(
        Select(
          select(tables.item.id),
          from(tables.item),
          tables.item.cid === cid && cond
        ).build
          .query[Ident]
          .to[List]
      )
      .map(_.map(_.id).toSet)
  }

  test("role and attribute queries") {
    val store = h2Store()
    for {
      cid <- prepare(store)
      results <- List(
        "sender:acm*",
        "Sender:ACME",
        "sender=Acme",
        "sender=ACME",
        "sender:bob",
        "recipient:acme",
        "unknownrole:acme",
        "sender.id=acme",
        "sender.id~=x,acme",
        "!sender:acme",
        "organization.email:*@acme.test",
        "organization.email=other@acme.test",
        "person.email:*"
      ).traverse(q => find(store, cid, q).map(q -> _))
    } yield assertEquals(
      results.toMap,
      Map(
        "sender:acm*" -> Set("i1"),
        "Sender:ACME" -> Set("i1"),
        "sender=Acme" -> Set("i1"),
        "sender=ACME" -> Set.empty[String],
        "sender:bob" -> Set.empty[String],
        "recipient:acme" -> Set.empty[String],
        "unknownrole:acme" -> Set.empty[String],
        "sender.id=acme" -> Set("i1"),
        "sender.id~=x,acme" -> Set("i1"),
        "!sender:acme" -> Set("i2"),
        "organization.email:*@acme.test" -> Set("i1"),
        "organization.email=other@acme.test" -> Set.empty[String],
        "person.email:*" -> Set.empty[String]
      )
    )
  }
}
