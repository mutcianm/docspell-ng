/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.common

object OcrLanguages {

  /** Joins the document language with additional languages in tesseract's `+` syntax
    * (e.g. `rus+nld+eng`), keeping the document language first and dropping duplicates.
    */
  def combine(lang: String, extra: String): String =
    (lang :: extra.split('+').map(_.trim).filter(_.nonEmpty).toList).distinct
      .mkString("+")
}
