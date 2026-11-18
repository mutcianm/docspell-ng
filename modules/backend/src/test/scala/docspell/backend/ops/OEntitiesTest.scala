/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.backend.ops

import cats.effect._

import docspell.backend.ops.OEntities._
import docspell.common._
import docspell.store.records._
import docspell.store.{DatabaseTest, Store}

class OEntitiesTest extends DatabaseTest {

  override def munitFixtures = h2Memory

  private def id(s: String) = Ident.unsafe(s)

  private def prepare(store: Store[IO]): IO[(CollectiveId, Ident)] =
    for {
      name <- Ident.randomId[IO]
      cid <- store.transact(
        RCollective.insert(
          RCollective(
            CollectiveId.unknown,
            name,
            CollectiveState.Active,
            Language.English,
            true,
            Timestamp.Epoch
          )
        )
      )
      itemId <- Ident.randomId[IO]
      item <- RItem
        .newItem[IO](cid, "item", "test", Direction.Incoming, ItemState.Created)
      _ <- store.transact(RItem.insert(item.copy(id = itemId)))
    } yield (cid, itemId)

  private def created(r: Result): Ident = r match {
    case Result.Success(id) => id
    case other              => fail(s"expected success, got $other")
  }

  private def assertInvalid(r: Result): Unit =
    assert(r.isInstanceOf[Result.Invalid], s"expected invalid, got $r")

  test("built-in types and roles cannot be renamed or deleted") {
    val store = h2Store()
    OEntities(store).use { ops =>
      for {
        fx <- prepare(store)
        cid = fx._1
        types <- ops.findTypes(cid)
        person = types
          .find(_.entityType.key.contains(REntityType.Key.person))
          .get
          .entityType
        equip = types
          .find(_.entityType.key.contains(REntityType.Key.equipment))
          .get
          .entityType
        rename <- ops.updateType(cid, person.id, NewType(id("human"), None, true))
        relabel <- ops.updateType(
          cid,
          person.id,
          NewType(person.name, Some("Human"), true)
        )
        delPerson <- ops.deleteType(cid, person.id)
        delEquip <- ops.deleteType(cid, equip.id)
        sender <- ops
          .findRoles(cid)
          .map(_.find(_.role.key.contains(REntityRole.Key.sender)).get.role)
        delSender <- ops.deleteRole(cid, sender.id)
      } yield {
        assertInvalid(rename)
        created(relabel)
        assertInvalid(delPerson)
        created(delEquip)
        assertInvalid(delSender)
      }
    }
  }

  test("names must be usable in queries") {
    val store = h2Store()
    OEntities(store).use { ops =>
      for {
        fx <- prepare(store)
        cid = fx._1
        keyword <- ops.createType(cid, NewType(id("tag"), None, true))
        dotted <- ops.createRole(cid, NewRole(id("a.b"), None, true, false, Nil))
        plain <- ops.createType(cid, NewType(id("vehicle"), None, true)).map(created)
        idAttr <- ops.createAttr(
          cid,
          plain,
          NewAttr(id("ID"), None, CustomFieldType.text, false, EntityMatcher.none)
        )
      } yield {
        assertInvalid(keyword)
        assertInvalid(dotted)
        assertInvalid(idAttr)
      }
    }
  }

  test("attribute values are validated against their definitions") {
    val store = h2Store()
    OEntities(store).use { ops =>
      for {
        fx <- prepare(store)
        cid = fx._1
        vehicle <- ops
          .createType(cid, NewType(id("vehicle"), Some("Vehicle"), true))
          .map(created)
        year <- ops
          .createAttr(
            cid,
            vehicle,
            NewAttr(id("year"), None, CustomFieldType.numeric, false, EntityMatcher.none)
          )
          .map(created)
        tag <- ops
          .createAttr(
            cid,
            vehicle,
            NewAttr(id("tag"), None, CustomFieldType.text, true, EntityMatcher.alias)
          )
          .map(created)
        bad <- ops.createEntity(
          cid,
          NewEntity(vehicle, "Car", None, true, List(year -> "abc"))
        )
        two <- ops.createEntity(
          cid,
          NewEntity(vehicle, "Car", None, true, List(year -> "1", year -> "2"))
        )
        car <- ops
          .createEntity(
            cid,
            NewEntity(
              vehicle,
              " Car ",
              None,
              true,
              List(year -> " 2001 ", tag -> "a", tag -> "b")
            )
          )
          .map(created)
        dup <- ops.createEntity(cid, NewEntity(vehicle, "Car", None, true, Nil))
        loaded <- ops.findEntity(cid, car).map(_.get)
        toSingle <- ops.updateAttr(
          cid,
          vehicle,
          tag,
          NewAttr(id("tag"), None, CustomFieldType.text, false, EntityMatcher.alias)
        )
        retype <- ops.updateAttr(
          cid,
          vehicle,
          year,
          NewAttr(id("year"), None, CustomFieldType.text, false, EntityMatcher.none)
        )
        _ <- ops.deleteAttr(cid, vehicle, tag).map(created)
        afterDelete <- ops.findEntity(cid, car).map(_.get)
      } yield {
        assertInvalid(bad)
        assertInvalid(two)
        assertInvalid(dup)
        assertEquals(loaded.entity.name, "Car")
        assertEquals(
          loaded.attrs.map(a => a.attr.name.id -> a.value.value).toSet,
          Set("year" -> "2001", "tag" -> "a", "tag" -> "b")
        )
        assertInvalid(toSingle)
        assertInvalid(retype)
        assertEquals(afterDelete.attrs.map(_.attr.name.id), Vector("year"))
      }
    }
  }

  test("item links respect role targets and cardinality") {
    val store = h2Store()
    OEntities(store).use { ops =>
      for {
        fx <- prepare(store)
        (cid, item) = fx
        types <- ops.findTypes(cid)
        typeId = (k: Ident) => types.find(_.entityType.key.contains(k)).get.entityType.id
        roles <- ops.findRoles(cid)
        roleId = (k: Ident) => roles.find(_.role.key.contains(k)).get.role.id
        acme <- ops
          .createEntity(
            cid,
            NewEntity(typeId(REntityType.Key.organization), "Acme", None, true, Nil)
          )
          .map(created)
        bob <- ops
          .createEntity(
            cid,
            NewEntity(typeId(REntityType.Key.person), "Bob", None, true, Nil)
          )
          .map(created)
        car <- ops
          .createEntity(
            cid,
            NewEntity(typeId(REntityType.Key.equipment), "Car", None, true, Nil)
          )
          .map(created)
        wrongType <- ops.setItemLinks(
          cid,
          item,
          roleId(REntityRole.Key.sender),
          List(car)
        )
        _ <- ops
          .setItemLinks(cid, item, roleId(REntityRole.Key.sender), List(acme, bob))
          .map(created)
        _ <- ops
          .setItemLinks(cid, item, roleId(REntityRole.Key.concerning), List(car))
          .map(created)
        single <- ops
          .createRole(cid, NewRole(id("owner"), None, false, false, Nil))
          .map(created)
        tooMany <- ops.setItemLinks(cid, item, single, List(acme, bob))
        links <- ops.findItemLinks(cid, item)
        narrow <- ops.updateRole(
          cid,
          roleId(REntityRole.Key.sender),
          NewRole(
            REntityRole.Key.sender,
            None,
            true,
            true,
            List(typeId(REntityType.Key.person))
          )
        )
        _ <- ops.deleteEntity(cid, bob).map(created)
        afterDelete <- ops.findItemLinks(cid, item)
      } yield {
        assertInvalid(wrongType)
        assertInvalid(tooMany)
        assertEquals(
          links.map(l => l.role.name.id -> l.entity.name).toSet,
          Set("sender" -> "Acme", "sender" -> "Bob", "concerning" -> "Car")
        )
        assertInvalid(narrow)
        assertEquals(afterDelete.map(_.entity.name).toSet, Set("Acme", "Car"))
      }
    }
  }
}
