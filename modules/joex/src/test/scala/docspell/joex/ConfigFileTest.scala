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
          "mode" -> cfg.convert.ocrmypdf.mode,
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

  test("ocrmypdf mode defaults to skip-text with deskew") {
    val args = ocrmypdfArgs(load(), "deu")
    assert(args.containsSlice(List("--skip-text", "--deskew")), args)
  }

  test("ocrmypdf redo-ocr mode drops skip-text and deskew") {
    val args =
      ocrmypdfArgs(load("DOCSPELL_JOEX_CONVERT_OCRMYPDF_MODE" -> "redo-ocr"), "deu")
    assert(args.contains("--redo-ocr"), args)
    assert(!args.contains("--skip-text") && !args.contains("--deskew"), args)
  }

  test("ocrmypdf force-ocr mode keeps deskew") {
    val args =
      ocrmypdfArgs(load("DOCSPELL_JOEX_CONVERT_OCRMYPDF_MODE" -> "force-ocr"), "deu")
    assert(args.containsSlice(List("--force-ocr", "--deskew")), args)
  }

  test("unknown ocrmypdf mode is rejected") {
    intercept[RuntimeException](load("DOCSPELL_JOEX_CONVERT_OCRMYPDF_MODE" -> "redo"))
  }
}
