/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.joex

import docspell.common.OcrLanguages
import docspell.config.EnvConfig
import docspell.config.Implicits._
import docspell.joex.ConfigFile.Implicits._

import munit._
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.ip4s._

class ConfigFileTest extends FunSuite {

  def load(env: (String, String)*): Config = {
    val raw = ConfigSource
      .fromConfig(EnvConfig.loadFrom(env.toMap.view))
      .at("docspell.joex")
      .loadOrThrow[Config]
    ConfigFile.validate.validOrThrow(raw)
  }

  def ocrmypdfArgs(cfg: Config, lang: String): List[String] =
    cfg.convert.ocrmypdf.command
      .resolve(
        Map(
          "lang" -> lang,
          "langs" -> OcrLanguages.combine(lang, cfg.convert.ocrmypdf.extraLanguages),
          "infile" -> "in.pdf",
          "outfile" -> "out.pdf"
        )
      )
      .args
      .values
      .toList

  test("no extra OCR languages by default") {
    val cfg = load()
    assertEquals(cfg.extraction.ocr.tesseract.extraLanguages, "")
    assert(ocrmypdfArgs(cfg, "deu").containsSlice(List("-l", "deu")))
  }

  test("ocr-extra-languages reaches all OCR commands") {
    val cfg = load("DOCSPELL_JOEX_OCR__EXTRA__LANGUAGES" -> "rus+eng")
    assertEquals(cfg.extraction.ocr.tesseract.extraLanguages, "rus+eng")
    assertEquals(cfg.convert.tesseract.extraLanguages, "rus+eng")
    assertEquals(cfg.convert.ocrmypdf.extraLanguages, "rus+eng")
    assert(ocrmypdfArgs(cfg, "nld").containsSlice(List("-l", "nld+rus+eng")))
  }

  test("language specific mappings still match the document language") {
    val cfg = load("DOCSPELL_JOEX_OCR__EXTRA__LANGUAGES" -> "eng")
    assert(ocrmypdfArgs(cfg, "jpn").containsSlice(List("-l", "jpn", "--output-type")))
  }
}
