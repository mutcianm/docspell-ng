/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.queries

import cats.data.{NonEmptyList => Nel}
import cats.effect._
import cats.implicits._

import docspell.common._
import docspell.store.records._
import docspell.store.{DatabaseTest, Store}

import doobie._

class QEntityLookupTest extends DatabaseTest {

  override def munitFixtures = h2Memory

  private val ts = Timestamp.Epoch
  private def id(s: String) = Ident.unsafe(s)

  private def attr(typeId: Ident, name: String): ConnectionIO[Ident] =
    RAttrDef.findByType(typeId).map(_.find(_.name.id == name).get.id)

  private def prepare(store: Store[IO]): IO[CollectiveId] =
    store.transact(for {
      cid <- RCollective.insert(
        RCollective(
          CollectiveId.unknown,
          id("lookup"),
          CollectiveState.Active,
          Language.English,
          true,
          ts
        )
      )
      org <- REntityType.findByKey(REntityType.Key.organization, cid).map(_.get.id)
      pers <- REntityType.findByKey(REntityType.Key.person, cid).map(_.get.id)
      short <- attr(org, "shortname")
      mail <- attr(org, "email")
      _ <- REntity.insert(
        REntity(id("acme"), cid, org, "Acme Corporation", None, true, ts, ts)
      )
      _ <- REntity.insert(REntity(id("old"), cid, org, "Acme Old", None, false, ts, ts))
      _ <- REntity.insert(
        REntity(id("bob"), cid, pers, "Bob", None, true, ts, Timestamp.ofMillis(5000))
      )
      _ <- REntityAttr.insert(REntityAttr(id("a1"), id("acme"), short, "ACM"))
      _ <- REntityAttr.insert(REntityAttr(id("a2"), id("acme"), mail, "info@acme.test"))
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
    } yield cid)

  test("lookups respect type, aliases, matchers and the suggest flag") {
    val store = h2Store()
    for {
      cid <- prepare(store)
      byName <- store.transact(
        QEntityLookup.findLike(cid, REntityType.Key.organization, "%acme%")
      )
      byAlias <- store.transact(
        QEntityLookup.findLike(cid, REntityType.Key.organization, "acm")
      )
      otherType <- store.transact(
        QEntityLookup.findLike(cid, REntityType.Key.person, "%acme%")
      )
      byMail <- store.transact(
        QEntityLookup.findByMatcher(
          cid,
          REntityType.Key.organization,
          EntityMatcher.email,
          "info@acme.test"
        )
      )
      wrongMatcher <- store.transact(
        QEntityLookup.findByMatcher(
          cid,
          REntityType.Key.organization,
          EntityMatcher.website,
          "info@acme.test"
        )
      )
      names <- store.transact(QEntityLookup.allNames(cid, 100))
      latest <- store.transact(QEntityLookup.latestUpdate(cid))
    } yield {
      assertEquals(byName.map(_.id.id), Vector("acme"))
      assertEquals(byAlias.map(_.id.id), Vector("acme"))
      assertEquals(otherType, Vector.empty)
      assertEquals(byMail.map(_.id.id), Vector("acme"))
      assertEquals(wrongMatcher, Vector.empty)
      assertEquals(names.org.toSet, Set("Acme Corporation", "Acme Old"))
      assertEquals(names.pers, Vector("Bob"))
      assertEquals(latest, Some(Timestamp.ofMillis(5000)))
    }
  }

  test("replacing a link keeps links of other types and roles") {
    val store = h2Store()
    for {
      cid <- store.transact(
        RCollective.insert(
          RCollective(
            CollectiveId.unknown,
            id("lookup2"),
            CollectiveState.Active,
            Language.English,
            true,
            ts
          )
        )
      )
      _ <- store.transact(for {
        org <- REntityType.findByKey(REntityType.Key.organization, cid).map(_.get.id)
        pers <- REntityType.findByKey(REntityType.Key.person, cid).map(_.get.id)
        _ <- REntity.insert(REntity(id("o1"), cid, org, "Org One", None, true, ts, ts))
        _ <- REntity.insert(REntity(id("o2"), cid, org, "Org Two", None, true, ts, ts))
        _ <- REntity.insert(REntity(id("p1"), cid, pers, "Pers", None, true, ts, ts))
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
        _ <- QEntity.replaceLinkOfType(id("i2"), cid, REntityRole.Key.sender, id("o1"))
        _ <- QEntity.replaceLinkOfType(id("i2"), cid, REntityRole.Key.sender, id("p1"))
        _ <- QEntity.replaceLinkOfType(
          id("i2"),
          cid,
          REntityRole.Key.concerning,
          id("p1")
        )
        _ <- QEntity.replaceLinkOfType(id("i2"), cid, REntityRole.Key.sender, id("o2"))
      } yield ())
      links <- store.transact(QEntity.findItemLinks(id("i2"), cid))
      corrOrg <- store.transact(
        QEntity.firstLinked(
          id("i2"),
          cid,
          Nel.of(REntityRole.Key.sender, REntityRole.Key.recipient),
          REntityType.Key.organization
        )
      )
      concOrg <- store.transact(
        QEntity.firstLinked(
          id("i2"),
          cid,
          Nel.of(REntityRole.Key.concerning),
          REntityType.Key.organization
        )
      )
    } yield {
      assertEquals(
        links.map(l => l.role.name.id -> l.entity.id.id).toSet,
        Set("sender" -> "o2", "sender" -> "p1", "concerning" -> "p1")
      )
      assertEquals(corrOrg.map(_.id.id), Some("o2"))
      assertEquals(concOrg, None)
    }
  }

  test("copying links keeps a single-valued role of the target") {
    val store = h2Store()
    for {
      cid <- store.transact(
        RCollective.insert(
          RCollective(
            CollectiveId.unknown,
            id("lookup3"),
            CollectiveState.Active,
            Language.English,
            true,
            ts
          )
        )
      )
      names <- store.transact(for {
        org <- REntityType.findByKey(REntityType.Key.organization, cid).map(_.get.id)
        sender <- REntityRole.findByKey(REntityRole.Key.sender, cid).map(_.get)
        _ <- REntityRole.update(sender.copy(multi = false))
        _ <- List("a", "b", "c").traverse_(n =>
          REntity.insert(REntity(id(n), cid, org, n.toUpperCase, None, true, ts, ts))
        )
        _ <- List("t", "s1", "s2").traverse_(i =>
          RItem.insert(
            RItem(
              id(i),
              cid,
              i,
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
        )
        _ <- RItemEntity.insert(RItemEntity(id("l1"), id("s1"), id("b"), sender.id))
        _ <- RItemEntity.insert(RItemEntity(id("l2"), id("s2"), id("c"), sender.id))
        _ <- QEntity.copyLinks(id("s1"), id("t"), cid)
        _ <- QEntity.copyLinks(id("s2"), id("t"), cid)
        links <- QEntity.findItemLinks(id("t"), cid)
      } yield links.map(_.entity.name))
    } yield assertEquals(names, Vector("B"))
  }
}
