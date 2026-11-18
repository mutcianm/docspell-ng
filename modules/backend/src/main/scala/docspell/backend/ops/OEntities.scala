/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.backend.ops

import cats.data.EitherT
import cats.effect._
import cats.implicits._

import docspell.backend.ops.OEntities._
import docspell.common._
import docspell.query.ItemQueryParser
import docspell.store.Store
import docspell.store.qb.DML
import docspell.store.qb.DSL._
import docspell.store.queries.QEntity
import docspell.store.records._

import doobie._

/** Entity types, their attribute definitions, roles, entities and item links. */
trait OEntities[F[_]] {

  def findTypes(coll: CollectiveId): F[Vector[TypeData]]
  def createType(coll: CollectiveId, in: NewType): F[Result]
  def updateType(coll: CollectiveId, id: Ident, in: NewType): F[Result]
  def deleteType(coll: CollectiveId, id: Ident): F[Result]

  def createAttr(coll: CollectiveId, typeId: Ident, in: NewAttr): F[Result]
  def updateAttr(coll: CollectiveId, typeId: Ident, id: Ident, in: NewAttr): F[Result]

  /** Deletes the attribute definition and all its values. */
  def deleteAttr(coll: CollectiveId, typeId: Ident, id: Ident): F[Result]

  def findRoles(coll: CollectiveId): F[Vector[RoleData]]
  def createRole(coll: CollectiveId, in: NewRole): F[Result]
  def updateRole(coll: CollectiveId, id: Ident, in: NewRole): F[Result]
  def deleteRole(coll: CollectiveId, id: Ident): F[Result]

  def findEntities(
      coll: CollectiveId,
      typeId: Option[Ident],
      nameQuery: Option[String]
  ): F[Vector[EntityData]]
  def findEntity(coll: CollectiveId, id: Ident): F[Option[EntityData]]
  def createEntity(coll: CollectiveId, in: NewEntity): F[Result]

  /** Changes name, notes, suggest flag and replaces all attribute values. */
  def updateEntity(coll: CollectiveId, id: Ident, in: NewEntity): F[Result]

  /** Deletes the entity, its attribute values and its item links. */
  def deleteEntity(coll: CollectiveId, id: Ident): F[Result]

  def findItemLinks(coll: CollectiveId, item: Ident): F[Vector[ItemLink]]

  /** Replaces the entities linked to the item under the given role. */
  def setItemLinks(
      coll: CollectiveId,
      item: Ident,
      roleId: Ident,
      entities: List[Ident]
  ): F[Result]
}

object OEntities {
  type EntityData = QEntity.EntityData
  type ItemLink = QEntity.ItemLink

  case class TypeData(entityType: REntityType, attrs: Vector[RAttrDef])
  case class RoleData(role: REntityRole, targets: Vector[Ident])

  case class NewType(name: Ident, label: Option[String], recognize: Boolean)
  case class NewAttr(
      name: Ident,
      label: Option[String],
      ftype: CustomFieldType,
      multi: Boolean,
      matcher: EntityMatcher
  )
  case class NewRole(
      name: Ident,
      label: Option[String],
      multi: Boolean,
      learn: Boolean,
      targets: List[Ident]
  )
  case class NewEntity(
      typeId: Ident,
      name: String,
      notes: Option[String],
      suggest: Boolean,
      attrs: List[(Ident, String)]
  )

  sealed trait Result
  object Result {
    case class Success(id: Ident) extends Result
    case object NotFound extends Result
    case class Invalid(msg: String) extends Result

    def success(id: Ident): Result = Success(id)
    def notFound: Result = NotFound
    def invalid(msg: String): Result = Invalid(msg)
  }

  // attribute values share the column size with custom field values
  private val maxValueLength = 300

  private type Check[A] = EitherT[ConnectionIO, Result, A]

  private def check(cond: Boolean, msg: => String): Check[Unit] =
    EitherT.cond[ConnectionIO](cond, (), Result.invalid(msg))

  private def found[A](fa: ConnectionIO[Option[A]]): Check[A] =
    EitherT.fromOptionF(fa, Result.notFound)

  private def lift[A](fa: ConnectionIO[A]): Check[A] =
    EitherT.liftF(fa)

  // role and type names appear in queries as `<role>:` and `<type>.<attr>:`
  private def usableName(name: Ident): Check[Unit] =
    check(
      ItemQueryParser.isUsableName(name.id),
      s"'${name.id}' cannot be used as a name: it must not contain dots or be a search keyword."
    )

  // attribute names appear in queries as `<type>.<attr>:`, and `<role>.id` is taken
  private def usableAttrName(name: Ident): Check[Unit] =
    check(
      !name.id.contains('.') && !name.id.equalsIgnoreCase("id"),
      s"'${name.id}' cannot be used as an attribute name."
    )

  def apply[F[_]](store: Store[F]): Resource[F, OEntities[F]] =
    Resource.pure[F, OEntities[F]](new OEntities[F] {
      private def run(c: Check[Ident]): F[Result] =
        store.transact(c.fold(identity, Result.success))

      def findTypes(coll: CollectiveId): F[Vector[TypeData]] =
        store.transact(for {
          types <- REntityType.findAll(coll)
          data <- types.traverse(t => RAttrDef.findByType(t.id).map(TypeData(t, _)))
        } yield data)

      private def nameFree(
          coll: CollectiveId,
          name: Ident,
          self: Option[Ident]
      ): Check[Unit] =
        lift(REntityType.findAll(coll)).flatMap(all =>
          check(
            !all.exists(t => t.name == name && !self.contains(t.id)),
            s"An entity type named '${name.id}' already exists."
          )
        )

      def createType(coll: CollectiveId, in: NewType): F[Result] =
        run(for {
          _ <- usableName(in.name)
          _ <- nameFree(coll, in.name, None)
          id <- lift(Ident.randomId[ConnectionIO])
          now <- lift(Timestamp.current[ConnectionIO])
          _ <- lift(
            REntityType.insert(
              REntityType(id, coll, None, in.name, in.label, false, in.recognize, now)
            )
          )
        } yield id)

      def updateType(coll: CollectiveId, id: Ident, in: NewType): F[Result] =
        run(for {
          t <- found(REntityType.findById(id, coll))
          _ <- check(!t.builtin || t.name == in.name, "Built-in types cannot be renamed.")
          _ <- usableName(in.name)
          _ <- nameFree(coll, in.name, Some(id))
          _ <- lift(
            REntityType.update(
              t.copy(name = in.name, label = in.label, recognize = in.recognize)
            )
          )
        } yield id)

      def deleteType(coll: CollectiveId, id: Ident): F[Result] =
        run(for {
          t <- found(REntityType.findById(id, coll))
          _ <- check(!t.builtin, "Built-in types cannot be deleted.")
          n <- lift(QEntity.countEntitiesOfType(id))
          _ <- check(n == 0, s"The type is used by $n entities.")
          attrs <- lift(RAttrDef.findByType(id))
          _ <- lift(attrs.traverse_(a => RAttrDef.deleteById(a.id)))
          _ <- lift(REntityRoleTarget.deleteByType(id))
          _ <- lift(REntityType.deleteById(id, coll))
        } yield id)

      private def attrNameFree(
          typeId: Ident,
          name: Ident,
          self: Option[Ident]
      ): Check[Unit] =
        lift(RAttrDef.findByType(typeId)).flatMap(all =>
          check(
            !all.exists(a => a.name == name && !self.contains(a.id)),
            s"An attribute named '${name.id}' already exists."
          )
        )

      def createAttr(coll: CollectiveId, typeId: Ident, in: NewAttr): F[Result] =
        run(for {
          _ <- found(REntityType.findById(typeId, coll))
          _ <- usableAttrName(in.name)
          _ <- attrNameFree(typeId, in.name, None)
          id <- lift(Ident.randomId[ConnectionIO])
          now <- lift(Timestamp.current[ConnectionIO])
          _ <- lift(
            RAttrDef.insert(
              RAttrDef(id, typeId, in.name, in.label, in.ftype, in.multi, in.matcher, now)
            )
          )
        } yield id)

      private def findAttr(
          coll: CollectiveId,
          typeId: Ident,
          id: Ident
      ): Check[RAttrDef] =
        for {
          _ <- found(REntityType.findById(typeId, coll))
          a <- found(RAttrDef.findById(id))
          _ <- check(a.typeId == typeId, "The attribute belongs to another type.")
        } yield a

      def updateAttr(
          coll: CollectiveId,
          typeId: Ident,
          id: Ident,
          in: NewAttr
      ): F[Result] =
        run(for {
          a <- findAttr(coll, typeId, id)
          _ <- usableAttrName(in.name)
          _ <- attrNameFree(typeId, in.name, Some(id))
          values <- lift(REntityAttr.findByAttrDef(id))
          _ <- check(
            in.ftype == a.ftype || values.isEmpty,
            "The type of an attribute with values cannot be changed."
          )
          _ <- check(
            in.multi || values.groupBy(_.entityId).forall(_._2.size <= 1),
            "Some entities have several values for this attribute."
          )
          _ <- lift(
            RAttrDef.update(
              a.copy(
                name = in.name,
                label = in.label,
                ftype = in.ftype,
                multi = in.multi,
                matcher = in.matcher
              )
            )
          )
        } yield id)

      def deleteAttr(coll: CollectiveId, typeId: Ident, id: Ident): F[Result] =
        run(for {
          _ <- findAttr(coll, typeId, id)
          _ <- lift(REntityAttr.deleteByAttrDef(id))
          _ <- lift(RAttrDef.deleteById(id))
        } yield id)

      def findRoles(coll: CollectiveId): F[Vector[RoleData]] =
        store.transact(for {
          roles <- REntityRole.findAll(coll)
          data <- roles.traverse(r =>
            REntityRoleTarget.findByRole(r.id).map(RoleData(r, _))
          )
        } yield data)

      private def roleChecks(
          coll: CollectiveId,
          in: NewRole,
          self: Option[Ident]
      ): Check[Unit] =
        for {
          _ <- usableName(in.name)
          roles <- lift(REntityRole.findAll(coll))
          _ <- check(
            !roles.exists(r => r.name == in.name && !self.contains(r.id)),
            s"A role named '${in.name.id}' already exists."
          )
          types <- lift(REntityType.findAll(coll))
          _ <- check(
            in.targets.forall(t => types.exists(_.id == t)),
            "Unknown entity type in role targets."
          )
        } yield ()

      private def setTargets(roleId: Ident, targets: List[Ident]): Check[Unit] =
        lift(
          REntityRoleTarget.deleteByRole(roleId) *>
            targets.distinct.traverse_(t =>
              REntityRoleTarget.insert(REntityRoleTarget(roleId, t))
            )
        )

      def createRole(coll: CollectiveId, in: NewRole): F[Result] =
        run(for {
          _ <- roleChecks(coll, in, None)
          id <- lift(Ident.randomId[ConnectionIO])
          now <- lift(Timestamp.current[ConnectionIO])
          _ <- lift(
            REntityRole.insert(
              REntityRole(
                id,
                coll,
                None,
                in.name,
                in.label,
                false,
                in.multi,
                in.learn,
                now
              )
            )
          )
          _ <- setTargets(id, in.targets)
        } yield id)

      def updateRole(coll: CollectiveId, id: Ident, in: NewRole): F[Result] =
        run(for {
          r <- found(REntityRole.findById(id, coll))
          _ <- check(!r.builtin || r.name == in.name, "Built-in roles cannot be renamed.")
          _ <- roleChecks(coll, in, Some(id))
          links <- lift(RItemEntity.findByRole(id))
          _ <- check(
            in.multi || links.groupBy(_.itemId).forall(_._2.size <= 1),
            "Some items have several entities for this role."
          )
          linkedTypes <- lift(
            links
              .map(_.entityId)
              .distinct
              .traverse(e => REntity.findById(e, coll).map(_.map(_.typeId)))
          )
          _ <- check(
            in.targets.isEmpty || linkedTypes.flatten.forall(in.targets.contains),
            "Some items link entities of a type the role would no longer accept."
          )
          _ <- lift(
            REntityRole.update(
              r.copy(name = in.name, label = in.label, multi = in.multi, learn = in.learn)
            )
          )
          _ <- setTargets(id, in.targets)
        } yield id)

      def deleteRole(coll: CollectiveId, id: Ident): F[Result] =
        run(for {
          r <- found(REntityRole.findById(id, coll))
          _ <- check(!r.builtin, "Built-in roles cannot be deleted.")
          n <- lift(QEntity.countLinksOfRole(id))
          _ <- check(n == 0, s"The role is used by $n item links.")
          _ <- lift(REntityRoleTarget.deleteByRole(id))
          _ <- lift(REntityRole.deleteById(id, coll))
        } yield id)

      def findEntities(
          coll: CollectiveId,
          typeId: Option[Ident],
          nameQuery: Option[String]
      ): F[Vector[EntityData]] =
        store.transact(QEntity.findEntities(coll, typeId, nameQuery))

      def findEntity(coll: CollectiveId, id: Ident): F[Option[EntityData]] =
        store.transact(QEntity.findEntity(coll, id))

      /** Validates and normalizes attribute values against the type's definitions. */
      private def attrValues(
          typeId: Ident,
          values: List[(Ident, String)]
      ): Check[List[(RAttrDef, String)]] =
        for {
          defs <- lift(RAttrDef.findByType(typeId))
          parsed <- EitherT.fromEither[ConnectionIO](
            values
              .map { case (id, v) => (id, v.trim) }
              .filter(_._2.nonEmpty)
              .traverse { case (id, v) =>
                for {
                  d <- defs
                    .find(_.id == id)
                    .toRight(Result.invalid(s"Unknown attribute: ${id.id}"))
                  n <- d.ftype
                    .parseValue(v)
                    .map(d.ftype.valueString)
                    .leftMap(err =>
                      Result.invalid(s"Invalid value for '${d.name.id}': $err")
                    )
                  _ <- Either.cond(
                    n.length <= maxValueLength,
                    (),
                    Result.invalid(s"Value for '${d.name.id}' is too long.")
                  )
                } yield (d, n)
              }
          )
          single = parsed.groupBy(_._1).find { case (d, vs) => !d.multi && vs.size > 1 }
          _ <- check(
            single.isEmpty,
            s"'${single.map(_._1.name.id).getOrElse("")}' takes one value."
          )
        } yield parsed

      private def entityNameFree(
          coll: CollectiveId,
          typeId: Ident,
          name: String,
          self: Option[Ident]
      ): Check[Unit] =
        lift(REntity.findByType(typeId, coll)).flatMap(all =>
          check(
            !all.exists(e => e.name == name && !self.contains(e.id)),
            s"An entity named '$name' already exists."
          )
        )

      private def insertAttrs(
          entityId: Ident,
          values: List[(RAttrDef, String)]
      ): Check[Unit] =
        lift(values.traverse_ { case (d, v) =>
          Ident
            .randomId[ConnectionIO]
            .flatMap(id => REntityAttr.insert(REntityAttr(id, entityId, d.id, v)))
        })

      def createEntity(coll: CollectiveId, in: NewEntity): F[Result] =
        run(for {
          _ <- found(REntityType.findById(in.typeId, coll))
          name = in.name.trim
          _ <- check(name.nonEmpty, "The name must not be empty.")
          _ <- entityNameFree(coll, in.typeId, name, None)
          values <- attrValues(in.typeId, in.attrs)
          id <- lift(Ident.randomId[ConnectionIO])
          now <- lift(Timestamp.current[ConnectionIO])
          _ <- lift(
            REntity.insert(
              REntity(id, coll, in.typeId, name, in.notes, in.suggest, now, now)
            )
          )
          _ <- insertAttrs(id, values)
        } yield id)

      def updateEntity(coll: CollectiveId, id: Ident, in: NewEntity): F[Result] =
        run(for {
          e <- found(REntity.findById(id, coll))
          _ <- check(in.typeId == e.typeId, "The type of an entity cannot be changed.")
          name = in.name.trim
          _ <- check(name.nonEmpty, "The name must not be empty.")
          _ <- entityNameFree(coll, e.typeId, name, Some(id))
          values <- attrValues(e.typeId, in.attrs)
          now <- lift(Timestamp.current[ConnectionIO])
          _ <- lift(
            REntity.update(
              e.copy(name = name, notes = in.notes, suggest = in.suggest, updated = now)
            )
          )
          _ <- lift(REntityAttr.deleteByEntity(id))
          _ <- insertAttrs(id, values)
        } yield id)

      def deleteEntity(coll: CollectiveId, id: Ident): F[Result] =
        run(for {
          _ <- found(REntity.findById(id, coll))
          _ <- lift(REntityAttr.deleteByEntity(id))
          _ <- lift(RItemEntity.deleteByEntity(id))
          _ <- lift(REntity.deleteById(id, coll))
        } yield id)

      def findItemLinks(coll: CollectiveId, item: Ident): F[Vector[ItemLink]] =
        store.transact(QEntity.findItemLinks(item, coll))

      def setItemLinks(
          coll: CollectiveId,
          item: Ident,
          roleId: Ident,
          entities: List[Ident]
      ): F[Result] =
        run(for {
          _ <- found(RItem.findByIdAndCollective(item, coll))
          role <- found(REntityRole.findById(roleId, coll))
          ids = entities.distinct
          _ <- check(
            role.multi || ids.size <= 1,
            s"The role '${role.name.id}' takes one entity."
          )
          targets <- lift(REntityRoleTarget.findByRole(roleId))
          linked <- ids.traverse(e =>
            EitherT.fromOptionF(
              REntity.findById(e, coll),
              Result.invalid(s"Unknown entity: ${e.id}")
            )
          )
          _ <- check(
            targets.isEmpty || linked.forall(e => targets.contains(e.typeId)),
            s"The role '${role.name.id}' does not accept some of the given entities."
          )
          now <- lift(Timestamp.current[ConnectionIO])
          _ <- lift(RItemEntity.deleteByItemAndRole(item, roleId))
          _ <- lift(
            ids.traverse_(e =>
              Ident
                .randomId[ConnectionIO]
                .flatMap(id => RItemEntity.insert(RItemEntity(id, item, e, roleId)))
            )
          )
          _ <- lift(
            DML.update(RItem.T, RItem.T.id === item, DML.set(RItem.T.updated.setTo(now)))
          )
        } yield item)
    })
}
