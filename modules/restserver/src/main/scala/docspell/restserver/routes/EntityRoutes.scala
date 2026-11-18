/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.restserver.routes

import cats.data.OptionT
import cats.effect._
import cats.implicits._

import docspell.backend.BackendApp
import docspell.backend.auth.AuthToken
import docspell.backend.ops.OEntities
import docspell.common._
import docspell.restapi.model._
import docspell.restserver.http4s._
import docspell.store.records.{RAttrDef, REntityType}

import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.Http4sDsl

/** Routes for entity types (`/sec/entitytype`), roles (`/sec/entityrole`) and entities
  * (`/sec/entity`).
  */
object EntityRoutes {

  def types[F[_]: Async](backend: BackendApp[F], user: AuthToken): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] with ResponseGenerator[F] {}
    import dsl._
    val coll = user.account.collectiveId

    HttpRoutes.of {
      case GET -> Root =>
        backend.entities
          .findTypes(coll)
          .flatMap(ts => Ok(EntityTypeList(ts.map(mkType).toList)))

      case req @ POST -> Root =>
        req
          .as[NewEntityType]
          .flatMap(in => backend.entities.createType(coll, newType(in)))
          .flatMap(r => Ok(idResult(r)))

      case req @ PUT -> Root / Ident(id) =>
        req
          .as[NewEntityType]
          .flatMap(in => backend.entities.updateType(coll, id, newType(in)))
          .flatMap(r => Ok(basicResult(r)))

      case DELETE -> Root / Ident(id) =>
        backend.entities.deleteType(coll, id).flatMap(r => Ok(basicResult(r)))

      case req @ POST -> Root / Ident(id) / "attr" =>
        req
          .as[NewAttrDef]
          .flatMap(in => backend.entities.createAttr(coll, id, newAttr(in)))
          .flatMap(r => Ok(idResult(r)))

      case req @ PUT -> Root / Ident(id) / "attr" / Ident(attrId) =>
        req
          .as[NewAttrDef]
          .flatMap(in => backend.entities.updateAttr(coll, id, attrId, newAttr(in)))
          .flatMap(r => Ok(basicResult(r)))

      case DELETE -> Root / Ident(id) / "attr" / Ident(attrId) =>
        backend.entities.deleteAttr(coll, id, attrId).flatMap(r => Ok(basicResult(r)))
    }
  }

  def roles[F[_]: Async](backend: BackendApp[F], user: AuthToken): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] with ResponseGenerator[F] {}
    import dsl._
    val coll = user.account.collectiveId

    HttpRoutes.of {
      case GET -> Root =>
        backend.entities
          .findRoles(coll)
          .flatMap(rs => Ok(EntityRoleList(rs.map(mkRole).toList)))

      case req @ POST -> Root =>
        req
          .as[NewEntityRole]
          .flatMap(in => backend.entities.createRole(coll, newRole(in)))
          .flatMap(r => Ok(idResult(r)))

      case req @ PUT -> Root / Ident(id) =>
        req
          .as[NewEntityRole]
          .flatMap(in => backend.entities.updateRole(coll, id, newRole(in)))
          .flatMap(r => Ok(basicResult(r)))

      case DELETE -> Root / Ident(id) =>
        backend.entities.deleteRole(coll, id).flatMap(r => Ok(basicResult(r)))
    }
  }

  def entities[F[_]: Async](backend: BackendApp[F], user: AuthToken): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] with ResponseGenerator[F] {}
    import dsl._
    val coll = user.account.collectiveId

    HttpRoutes.of {
      case GET -> Root :? QueryParam.QueryOpt(q) +& QueryParam.EntityTypeOpt(typeId) =>
        backend.entities
          .findEntities(coll, typeId, q.map(_.q))
          .flatMap(es => Ok(EntityList(es.map(mkEntity).toList)))

      case req @ POST -> Root =>
        req
          .as[NewEntity]
          .flatMap(in => backend.entities.createEntity(coll, newEntity(in)))
          .flatMap(r => Ok(idResult(r)))

      case GET -> Root / Ident(id) =>
        OptionT(backend.entities.findEntity(coll, id))
          .semiflatMap(e => Ok(mkEntity(e)))
          .getOrElseF(NotFound(BasicResult(success = false, "Not found")))

      case req @ PUT -> Root / Ident(id) =>
        req
          .as[NewEntity]
          .flatMap(in => backend.entities.updateEntity(coll, id, newEntity(in)))
          .flatMap(r => Ok(basicResult(r)))

      case DELETE -> Root / Ident(id) =>
        backend.entities.deleteEntity(coll, id).flatMap(r => Ok(basicResult(r)))
    }
  }

  def idResult(r: OEntities.Result): IdResult =
    r match {
      case OEntities.Result.Success(id) => IdResult(success = true, "Created.", id)
      case OEntities.Result.NotFound =>
        IdResult(success = false, "Not found", Ident.unsafe(""))
      case OEntities.Result.Invalid(msg) =>
        IdResult(success = false, msg, Ident.unsafe(""))
    }

  def basicResult(r: OEntities.Result): BasicResult =
    r match {
      case OEntities.Result.Success(_)   => BasicResult(success = true, "Saved.")
      case OEntities.Result.NotFound     => BasicResult(success = false, "Not found")
      case OEntities.Result.Invalid(msg) => BasicResult(success = false, msg)
    }

  private def newType(in: NewEntityType) =
    OEntities.NewType(in.name, in.label, in.recognize)

  private def newAttr(in: NewAttrDef) =
    OEntities.NewAttr(in.name, in.label, in.ftype, in.multi, in.matcher)

  private def newRole(in: NewEntityRole) =
    OEntities.NewRole(in.name, in.label, in.multi, in.learn, in.targets)

  private def newEntity(in: NewEntity) =
    OEntities.NewEntity(
      in.typeId,
      in.name,
      in.notes,
      in.suggest,
      in.attrs.map(a => a.attrId -> a.value)
    )

  private def mkAttr(a: RAttrDef): AttrDef =
    AttrDef(a.id, a.name, a.label, a.ftype, a.multi, a.matcher, a.created)

  private def mkType(t: OEntities.TypeData): EntityType = {
    val et: REntityType = t.entityType
    EntityType(
      et.id,
      et.key,
      et.name,
      et.label,
      et.builtin,
      et.recognize,
      t.attrs.map(mkAttr).toList,
      et.created
    )
  }

  private def mkRole(r: OEntities.RoleData): EntityRole =
    EntityRole(
      r.role.id,
      r.role.key,
      r.role.name,
      r.role.label,
      r.role.builtin,
      r.role.multi,
      r.role.learn,
      r.targets.toList,
      r.role.created
    )

  private def mkEntity(e: OEntities.EntityData): Entity =
    Entity(
      e.entity.id,
      e.entity.typeId,
      e.entity.name,
      e.entity.notes,
      e.entity.suggest,
      e.attrs
        .map(a => EntityAttrValue(a.attr.id, Some(a.attr.name), a.value.value))
        .toList,
      e.entity.created,
      e.entity.updated
    )
}
