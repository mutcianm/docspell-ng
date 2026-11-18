/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.backend.ops

import cats.data.NonEmptyList
import cats.effect.{Async, Resource}
import cats.implicits._

import docspell.common._
import docspell.store.queries.QLegacyEntity
import docspell.store.records.REquipment
import docspell.store.Store

trait OEquipment[F[_]] {

  def findAll(
      collectiveId: CollectiveId,
      nameQuery: Option[String],
      order: OEquipment.EquipmentOrder
  ): F[Vector[REquipment]]

  def find(collectiveId: CollectiveId, id: Ident): F[Option[REquipment]]

}

object OEquipment {
  import docspell.store.qb.DSL._

  sealed trait EquipmentOrder
  object EquipmentOrder {
    final case object NameAsc extends EquipmentOrder
    final case object NameDesc extends EquipmentOrder

    def parse(str: String): Either[String, EquipmentOrder] =
      str.toLowerCase match {
        case "name"  => Right(NameAsc)
        case "-name" => Right(NameDesc)
        case _       => Left(s"Unknown sort property for equipments: $str")
      }

    def parseOrDefault(str: String): EquipmentOrder =
      parse(str).toOption.getOrElse(NameAsc)

    private[ops] def apply(order: EquipmentOrder)(table: REquipment.Table) = order match {
      case NameAsc  => NonEmptyList.of(table.name.asc)
      case NameDesc => NonEmptyList.of(table.name.desc)
    }
  }

  // entities are the source; these are read-only views in the former shape (#24)
  def apply[F[_]: Async](store: Store[F]): Resource[F, OEquipment[F]] =
    Resource.pure[F, OEquipment[F]](new OEquipment[F] {
      def findAll(
          collective: CollectiveId,
          nameQuery: Option[String],
          order: EquipmentOrder
      ): F[Vector[REquipment]] =
        store.transact(QLegacyEntity.equipments(collective, nameQuery)).map { all =>
          val asc = all.sortBy(_.name.toLowerCase)
          if (order == EquipmentOrder.NameDesc) asc.reverse else asc
        }

      def find(cid: CollectiveId, id: Ident): F[Option[REquipment]] =
        findAll(cid, None, EquipmentOrder.NameAsc).map(_.find(_.eid == id))
    })
}
