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
import docspell.backend.ops.OOrganization
import docspell.common.Ident
import docspell.restapi.model._
import docspell.restserver.conv.Conversions._
import docspell.restserver.http4s.QueryParam

import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.Http4sDsl

object PersonRoutes {

  def apply[F[_]: Async](backend: BackendApp[F], user: AuthToken): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes.of {
      case GET -> Root :? QueryParam.FullOpt(full) +&
          QueryParam.QueryOpt(q) +& QueryParam.PersonSort(sort) =>
        val order = sort.getOrElse(OOrganization.PersonOrder.NameAsc)
        if (full.getOrElse(false))
          for {
            data <- backend.organization.findAllPerson(
              user.account.collectiveId,
              q.map(_.q),
              order
            )
            resp <- Ok(PersonList(data.map(mkPerson).toList))
          } yield resp
        else
          for {
            data <- backend.organization.findAllPersonRefs(
              user.account.collectiveId,
              q.map(_.q),
              order
            )
            resp <- Ok(ReferenceList(data.map(mkIdName).toList))
          } yield resp

      case GET -> Root / Ident(id) =>
        (for {
          org <- OptionT(backend.organization.findPerson(user.account.collectiveId, id))
          resp <- OptionT.liftF(Ok(mkPerson(org)))
        } yield resp).getOrElseF(NotFound())
    }
  }
}
