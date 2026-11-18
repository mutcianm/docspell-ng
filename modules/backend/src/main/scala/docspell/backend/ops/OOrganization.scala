/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.backend.ops

import cats.data.NonEmptyList
import cats.effect.{Async, Resource}
import cats.implicits._

import docspell.backend.ops.OOrganization._
import docspell.common._
import docspell.store._
import docspell.store.queries.QLegacyEntity
import docspell.store.records._

trait OOrganization[F[_]] {
  def findAllOrg(
      collectiveId: CollectiveId,
      query: Option[String],
      order: OrganizationOrder
  ): F[Vector[OrgAndContacts]]

  def findOrg(collectiveId: CollectiveId, orgId: Ident): F[Option[OrgAndContacts]]

  def findAllOrgRefs(
      collectiveId: CollectiveId,
      nameQuery: Option[String],
      order: OrganizationOrder
  ): F[Vector[IdRef]]

  def findAllPerson(
      collectiveId: CollectiveId,
      query: Option[String],
      order: PersonOrder
  ): F[Vector[PersonAndContacts]]

  def findPerson(collectiveId: CollectiveId, persId: Ident): F[Option[PersonAndContacts]]

  def findAllPersonRefs(
      collectiveId: CollectiveId,
      nameQuery: Option[String],
      order: PersonOrder
  ): F[Vector[IdRef]]

}

object OOrganization {
  import docspell.store.qb.DSL._

  case class OrgAndContacts(org: ROrganization, contacts: Seq[RContact])

  case class PersonAndContacts(
      person: RPerson,
      org: Option[ROrganization],
      contacts: Seq[RContact]
  )

  sealed trait OrganizationOrder
  object OrganizationOrder {
    final case object NameAsc extends OrganizationOrder
    final case object NameDesc extends OrganizationOrder

    def parse(str: String): Either[String, OrganizationOrder] =
      str.toLowerCase match {
        case "name"  => Right(NameAsc)
        case "-name" => Right(NameDesc)
        case _       => Left(s"Unknown sort property for organization: $str")
      }

    def parseOrDefault(str: String): OrganizationOrder =
      parse(str).toOption.getOrElse(NameAsc)

    private[ops] def apply(order: OrganizationOrder)(table: ROrganization.Table) =
      order match {
        case NameAsc  => NonEmptyList.of(table.name.asc)
        case NameDesc => NonEmptyList.of(table.name.desc)
      }
  }

  sealed trait PersonOrder
  object PersonOrder {
    final case object NameAsc extends PersonOrder
    final case object NameDesc extends PersonOrder
    final case object OrgAsc extends PersonOrder
    final case object OrgDesc extends PersonOrder

    def parse(str: String): Either[String, PersonOrder] =
      str.toLowerCase match {
        case "name"  => Right(NameAsc)
        case "-name" => Right(NameDesc)
        case "org"   => Right(OrgAsc)
        case "-org"  => Right(OrgDesc)
        case _       => Left(s"Unknown sort property for person: $str")
      }

    def parseOrDefault(str: String): PersonOrder =
      parse(str).toOption.getOrElse(NameAsc)

    private[ops] def apply(
        order: PersonOrder
    )(person: RPerson.Table, org: ROrganization.Table) =
      order match {
        case NameAsc  => NonEmptyList.of(person.name.asc)
        case NameDesc => NonEmptyList.of(person.name.desc)
        case OrgAsc   => NonEmptyList.of(org.name.asc)
        case OrgDesc  => NonEmptyList.of(org.name.desc)
      }

    private[ops] def nameOnly(order: PersonOrder)(person: RPerson.Table) =
      order match {
        case NameAsc  => NonEmptyList.of(person.name.asc)
        case NameDesc => NonEmptyList.of(person.name.desc)
        case OrgAsc   => NonEmptyList.of(person.name.asc)
        case OrgDesc  => NonEmptyList.of(person.name.asc)
      }
  }

  private def byName[A](items: Vector[A], name: A => String, desc: Boolean): Vector[A] = {
    val asc = items.sortBy(a => name(a).toLowerCase)
    if (desc) asc.reverse else asc
  }

  // entities are the source; these are read-only views in the former shape (#24)
  def apply[F[_]: Async](store: Store[F]): Resource[F, OOrganization[F]] =
    Resource.pure[F, OOrganization[F]](new OOrganization[F] {

      def findAllOrg(
          collectiveId: CollectiveId,
          query: Option[String],
          order: OrganizationOrder
      ): F[Vector[OrgAndContacts]] =
        store
          .transact(QLegacyEntity.organizations(collectiveId, query))
          .map(all =>
            byName[(ROrganization, Vector[RContact])](
              all,
              _._1.name,
              order == OrganizationOrder.NameDesc
            ).map { case (org, cont) => OrgAndContacts(org, cont) }
          )

      def findOrg(collectiveId: CollectiveId, orgId: Ident): F[Option[OrgAndContacts]] =
        findAllOrg(collectiveId, None, OrganizationOrder.NameAsc).map(
          _.find(_.org.oid == orgId)
        )

      def findAllOrgRefs(
          collectiveId: CollectiveId,
          nameQuery: Option[String],
          order: OrganizationOrder
      ): F[Vector[IdRef]] =
        findAllOrg(collectiveId, nameQuery, order).map(
          _.map(o => IdRef(o.org.oid, o.org.name))
        )

      def findAllPerson(
          collectiveId: CollectiveId,
          query: Option[String],
          order: PersonOrder
      ): F[Vector[PersonAndContacts]] =
        store
          .transact(QLegacyEntity.persons(collectiveId, query))
          .map(all =>
            byName[(RPerson, Vector[RContact])](
              all,
              _._1.name,
              order == PersonOrder.NameDesc
            ).map { case (p, cont) => PersonAndContacts(p, None, cont) }
          )

      def findPerson(
          collectiveId: CollectiveId,
          persId: Ident
      ): F[Option[PersonAndContacts]] =
        findAllPerson(collectiveId, None, PersonOrder.NameAsc).map(
          _.find(_.person.pid == persId)
        )

      def findAllPersonRefs(
          collectiveId: CollectiveId,
          nameQuery: Option[String],
          order: PersonOrder
      ): F[Vector[IdRef]] =
        findAllPerson(collectiveId, nameQuery, order).map(
          _.map(p => IdRef(p.person.pid, p.person.name))
        )
    })
}
