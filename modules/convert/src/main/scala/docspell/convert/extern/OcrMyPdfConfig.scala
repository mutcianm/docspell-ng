/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.convert.extern

import fs2.io.file.Path

import docspell.common.exec.ExternalCommand

case class OcrMyPdfConfig(
    enabled: Boolean,
    command: ExternalCommand,
    workingDir: Path,
    mode: String = OcrMyPdfConfig.SkipText,
    // set from docspell.joex.ocr-extra-languages, see joex ConfigFile
    extraLanguages: String = ""
)

object OcrMyPdfConfig {
  val SkipText = "skip-text"
  val modes: Set[String] = Set(SkipText, "redo-ocr", "force-ocr")
}
