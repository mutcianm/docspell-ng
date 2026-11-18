/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.restserver.webapp

import cats.data.{Kleisli, OptionT}
import cats.effect._

import org.http4s._

/** Serves the documentation site when it was bundled at build time (`bundleDocs`). It is
  * tried after all other routes, since the site uses root-relative paths like `/docs/…`
  * and `/styles.css`.
  */
object DocsRoutes {
  private val base = "/META-INF/docs-site"

  def apply[F[_]: Async]: HttpRoutes[F] =
    Kleisli {
      case req if req.method == Method.GET =>
        val segments = req.pathInfo.segments.map(_.decoded())
        if (segments.exists(s => s == ".." || s.contains('/'))) OptionT.none
        else {
          val path = segments.mkString("/", "/", "")
          val isDir = req.pathInfo.endsWithSlash || !segments.lastOption.exists(_.contains('.'))
          val resource =
            if (isDir) s"$base${path.stripSuffix("/")}/index.html" else s"$base$path"
          StaticFile.fromResource(resource, Some(req))
        }
      case _ =>
        OptionT.none
    }
}
