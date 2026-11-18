/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.migrate

import javax.sql.DataSource

import scala.concurrent.duration._

import cats.effect._
import cats.implicits._

import docspell.common._
import docspell.store.qb.DML
import docspell.store.qb.DSL._
import docspell.store.queries.QEntity
import docspell.store.records._
import docspell.store.{DatabaseTest, JdbcConfig, SchemaMigrateConfig, StoreFixture}

import doobie._
import doobie.implicits._
import org.flywaydb.core.Flyway

/** Migrates old person/organization/equipment data into the entity model (V1.40.0). */
class EntityMigrationTest extends DatabaseTest {

  // migrating MariaDB from scratch takes most of the default 30s
  override val munitIOTimeout = 2.minutes

  override def munitFixtures =
    List(postgresCnt, mariadbCnt, pgDataSource, mariaDataSource, h2DataSource)

  test("postgres: migrate entities")(checkMigration(pgDataSource()))
  test("mariadb: migrate entities")(checkMigration(mariaDataSource()))
  test("h2: migrate entities")(checkMigration(h2DataSource()))

  private val ts = Timestamp.ofMillis(1654329963743L)
  private def id(s: String) = Ident.unsafe(s)

  def checkMigration(db: (JdbcConfig, DataSource)): IO[Unit] = {
    val (jdbc, ds) = db
    StoreFixture.makeXA(ds).use { xa =>
      val migrate = FlywayMigrate[IO](jdbc, SchemaMigrateConfig.defaults, xa)
      for {
        fw <- migrate.createFlyway(FlywayMigrate.MigrationKind.Main)
        _ <- IO.blocking(
          Flyway
            .configure()
            .configuration(fw.getConfiguration)
            .target("1.39.3")
            .load()
            .migrate()
        )
        cid <- insertOldData.transact(xa)
        _ <- migrate.run
        _ <- assertions(cid).transact(xa)
        _ <- builtinsMatch(cid).transact(xa)
        _ <- derivedColumns(cid).transact(xa)
      } yield ()
    }
  }

  // RCollective.insert would already seed the new tables, which don't exist yet
  private def insertCollective: ConnectionIO[CollectiveId] =
    DML
      .insertFragment(
        RCollective.T,
        RCollective.T.allNoId,
        List(
          fr"'coll1',${CollectiveState.Active: CollectiveState},${Language.English: Language},true,$ts"
        )
      )
      .update
      .withUniqueGeneratedKeys[CollectiveId](RCollective.T.id.name)

  private def item(n: String, cid: CollectiveId, dir: Direction): RItem =
    RItem(
      id(n),
      cid,
      n,
      None,
      "test",
      dir,
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

  private def insertOldData: ConnectionIO[CollectiveId] =
    for {
      cid <- insertCollective
      // the old tables are gone from the code, so the rows go in as plain SQL
      _ <-
        sql"""insert into person (pid, coll_id, name, street, zip, city, country, notes, created, updated, person_use)
                 values ('p1', $cid, 'Alice', 'Main 1', '', 'Town', '', 'note', $ts, $ts, 'disabled')""".update.run
      _ <-
        sql"""insert into organization (oid, coll_id, name, street, zip, city, country, created, updated, short_name, org_use)
                 values ('o1', $cid, 'Acme', '', '', '', '', $ts, $ts, 'AC', 'correspondent')""".update.run
      _ <- sql"""insert into equipment (eid, coll_id, name, created, updated, equip_use)
                 values ('e1', $cid, 'Car', $ts, $ts, 'concerning')""".update.run
      _ <- sql"""insert into contact (contactid, value_, kind, oid, created)
                 values ('c1', 'info@acme.test', 'Email', 'o1', $ts)""".update.run
      _ <- sql"""insert into contact (contactid, value_, kind, pid, created)
                 values ('c2', '12345', 'Phone', 'p1', $ts)""".update.run
      _ <- RItem.insert(
        item("i1", cid, Direction.Incoming)
          .copy(
            corrOrg = Some(id("o1")),
            concPerson = Some(id("p1")),
            concEquipment = Some(id("e1"))
          )
      )
      _ <- RItem.insert(
        item("i2", cid, Direction.Outgoing).copy(corrPerson = Some(id("p1")))
      )
    } yield cid

  private def attrValues(entity: String): ConnectionIO[Set[(String, String)]] =
    for {
      values <- REntityAttr.findByEntity(id(entity))
      defs <- values.traverse(v => RAttrDef.findById(v.attrDefId).map(_.get.name.id))
    } yield defs.zip(values.map(_.value)).toSet

  private def links(
      item: String,
      cid: CollectiveId
  ): ConnectionIO[Set[(String, String)]] =
    RItemEntity
      .findByItem(id(item))
      .flatMap(
        _.traverse(l =>
          REntityRole.findById(l.roleId, cid).map(r => (r.get.name.id, l.entityId.id))
        )
          .map(_.toSet)
      )

  private def assertions(cid: CollectiveId): ConnectionIO[Unit] =
    for {
      person <- REntityType.findByKey(REntityType.Key.person, cid).map(_.get)
      equip <- REntityType.findByKey(REntityType.Key.equipment, cid).map(_.get)
      org <- REntityType.findByKey(REntityType.Key.organization, cid).map(_.get)
      p1 <- REntity.findById(id("p1"), cid).map(_.get)
      o1 <- REntity.findById(id("o1"), cid).map(_.get)
      e1 <- REntity.findById(id("e1"), cid).map(_.get)
      p1Attrs <- attrValues("p1")
      o1Attrs <- attrValues("o1")
      i1 <- links("i1", cid)
      i2 <- links("i2", cid)
      sender <- REntityRole.findByKey(REntityRole.Key.sender, cid).map(_.get)
      senderTargets <- REntityRoleTarget.findByRole(sender.id)
      concerning <- REntityRole.findByKey(REntityRole.Key.concerning, cid).map(_.get)
      concTargets <- REntityRoleTarget.findByRole(concerning.id)
    } yield {
      assert(person.builtin && org.builtin && !equip.builtin)
      assertEquals(
        (p1.typeId, p1.name, p1.notes, p1.suggest),
        (person.id, "Alice", Some("note"), false)
      )
      assertEquals((o1.typeId, o1.suggest), (org.id, true))
      assertEquals(e1.typeId, equip.id)
      assertEquals(
        p1Attrs,
        Set("street" -> "Main 1", "city" -> "Town", "phone" -> "12345")
      )
      assertEquals(o1Attrs, Set("shortname" -> "AC", "email" -> "info@acme.test"))
      assertEquals(i1, Set("sender" -> "o1", "recipient" -> "p1", "concerning" -> "e1"))
      assertEquals(i2, Set("recipient" -> "p1"))
      assertEquals(senderTargets.toSet, Set(person.id, org.id))
      assertEquals(concTargets, Vector.empty)
    }

  private def shape(cid: CollectiveId) =
    for {
      types <- REntityType.findAll(cid)
      attrs <- types.traverse(t =>
        RAttrDef
          .findByType(t.id)
          .map(_.map(a => (t.key, t.builtin, a.name, a.multi, a.matcher)))
      )
      roles <- REntityRole.findAll(cid)
      targets <- roles.traverse(r =>
        REntityRoleTarget
          .findByRole(r.id)
          .map(ts => (r.key, ts.map(t => types.find(_.id == t).flatMap(_.key)).toSet))
      )
    } yield (types.map(t => (t.key, t.builtin)).toSet, attrs.flatten.toSet, targets.toSet)

  // a collective created after the migration starts with the same types, attributes and roles
  private def builtinsMatch(migrated: CollectiveId): ConnectionIO[Unit] =
    for {
      fresh <- RCollective.insert(
        RCollective(
          CollectiveId.unknown,
          id("coll2"),
          CollectiveState.Active,
          Language.English,
          true,
          ts
        )
      )
      a <- shape(migrated)
      b <- shape(fresh)
    } yield assertEquals(b, a)

  // the item columns point at entities (V1.41.0) and follow the links
  private def derivedColumns(cid: CollectiveId): ConnectionIO[Unit] =
    for {
      org <- REntityType.findByKey(REntityType.Key.organization, cid).map(_.get)
      _ <- REntity.insert(REntity(id("o2"), cid, org.id, "Newco", None, true, ts, ts))
      _ <- QEntity.setLinkOfType(
        id("i2"),
        cid,
        REntityRole.Key.recipient,
        org.key.get,
        Some(id("o2"))
      )
      outgoing <- RItem.findById(id("i2")).map(_.get)
      _ <- RItem.updateDirection(
        cats.data.NonEmptyList.of(id("i2")),
        cid,
        Direction.Incoming
      )
      _ <- QEntity.refreshItemColumns(id("i2"), cid)
      incoming <- RItem.findById(id("i2")).map(_.get)
      i1 <- RItem.findById(id("i1")).map(_.get)
    } yield {
      assertEquals(
        (outgoing.corrOrg, outgoing.corrPerson),
        (Some(id("o2")), Some(id("p1")))
      )
      assertEquals((incoming.corrOrg, incoming.corrPerson), (None, None))
      assertEquals(
        (i1.corrOrg, i1.concPerson, i1.concEquipment),
        (Some(id("o1")), Some(id("p1")), Some(id("e1")))
      )
    }
}
