/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.joex.process

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._

import docspell.common._
import docspell.scheduler.{Context, Task}
import docspell.store.Store
import docspell.store.queries.QEntity
import docspell.store.records.{REntityRole, RItem}

object LinkProposal {

  def onlyNew[F[_]: Sync](
      store: Store[F]
  )(data: ItemData): Task[F, ProcessItemArgs, ItemData] =
    if (data.item.state.isValid)
      Task
        .log[F, ProcessItemArgs](_.debug(s"Not linking proposals on existing item"))
        .map(_ => data)
    else
      LinkProposal[F](store)(data)

  def apply[F[_]: Sync](
      store: Store[F]
  )(data: ItemData): Task[F, ProcessItemArgs, ItemData] =
    if (data.item.state == ItemState.Confirmed)
      Task
        .log[F, ProcessItemArgs](_.debug(s"Not linking proposals on confirmed item"))
        .map(_ => data)
    else
      Task { ctx =>
        val proposals = data.finalProposals

        ctx.logger.info(s"Starting linking proposals") *>
          MetaProposalType.all
            .traverse(applyValue(data, proposals, ctx, store))
            .map(result => ctx.logger.info(s"Results from proposal processing: $result"))
            .map(_ => data)
      }

  def applyValue[F[_]: Sync](
      data: ItemData,
      proposalList: MetaProposalList,
      ctx: Context[F, ProcessItemArgs],
      store: Store[F]
  )(mpt: MetaProposalType): F[Result] =
    data.givenMeta.find(mpt).orElse(proposalList.find(mpt)) match {
      case None =>
        ctx.logger.debug(s"No value for $mpt") *>
          Result.noneFound(mpt).pure[F]
      case Some(a) if a.isSingleValue =>
        ctx.logger.info(s"Found one candidate for ${a.proposalType}") *>
          setItemMeta(data.item, ctx, store, a.proposalType, a.values.head.ref.id).map(
            _ => Result.single(mpt)
          )
      case Some(a) =>
        val ids = a.values.map(_.ref.id.id)
        ctx.logger.info(
          s"Found many (${a.size}, $ids) candidates for ${a.proposalType}. Setting first."
        ) *>
          setItemMeta(data.item, ctx, store, a.proposalType, a.values.head.ref.id).map(
            _ => Result.multiple(mpt)
          )
    }

  def setItemMeta[F[_]: Sync](
      item: RItem,
      ctx: Context[F, ProcessItemArgs],
      store: Store[F],
      mpt: MetaProposalType,
      value: Ident
  ): F[Int] = {
    val itemId = item.id
    val correspondent =
      if (item.direction == Direction.Incoming) REntityRole.Key.sender
      else REntityRole.Key.recipient
    def link(roleKey: Ident): F[Int] =
      ctx.logger.debug(s"Linking item to ${value.id} as ${roleKey.id} ($mpt)") *>
        store.transact(
          QEntity.replaceLinkOfType(itemId, ctx.args.meta.collective, roleKey, value)
        )

    mpt match {
      case MetaProposalType.CorrOrg | MetaProposalType.CorrPerson =>
        link(correspondent)
      case MetaProposalType.ConcPerson | MetaProposalType.ConcEquip =>
        link(REntityRole.Key.concerning)
      case MetaProposalType.DocDate =>
        MetaProposal.parseDate(value) match {
          case Some(ld) =>
            val ts = Timestamp.from(ld.atStartOfDay(Timestamp.UTC))
            ctx.logger.debug(s"Updating item date ${value.id}") *>
              store.transact(
                RItem.updateDate(
                  NonEmptyList.of(itemId),
                  ctx.args.meta.collective,
                  Some(ts)
                )
              )
          case None =>
            ctx.logger.info(s"Cannot read value '${value.id}' into a date.") *>
              0.pure[F]
        }
      case MetaProposalType.DueDate =>
        MetaProposal.parseDate(value) match {
          case Some(ld) =>
            val ts = Timestamp.from(ld.atStartOfDay(Timestamp.UTC))
            ctx.logger.debug(s"Updating item due-date suggestion ${value.id}") *>
              store.transact(
                RItem.updateDueDate(
                  NonEmptyList.of(itemId),
                  ctx.args.meta.collective,
                  Some(ts)
                )
              )
          case None =>
            ctx.logger.info(s"Cannot read value '${value.id}' into a date.") *>
              0.pure[F]
        }
    }
  }

  sealed trait Result {
    def proposalType: MetaProposalType
  }
  object Result {

    case class NoneFound(proposalType: MetaProposalType) extends Result
    case class SingleResult(proposalType: MetaProposalType) extends Result
    case class MultipleResult(proposalType: MetaProposalType) extends Result

    def noneFound(proposalType: MetaProposalType): Result = NoneFound(proposalType)
    def single(proposalType: MetaProposalType): Result = SingleResult(proposalType)
    def multiple(proposalType: MetaProposalType): Result = MultipleResult(proposalType)
  }
}
