/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.extract

import docspell.common.Language

import munit._

class TextLayerCheckTest extends FunSuite {

  val enabled = PdfConfig(500, maxGarbageRatio = 0.1, minScriptRatio = 0.1)
  val disabled = PdfConfig(500, maxGarbageRatio = 1.0, minScriptRatio = 0.0)

  val russian = "Лабораторная служба. Контакт-центр: 8 (812) 309 12 21, www.example.ru"
  val dutch = "Geachte mevrouw, hierbij ontvangt u de bevestiging. Totaal: € 44,00"
  val symbolSoup = "█▐ ▌ ▐▐▌ ▌ ▌▌ █ ▐▌▐█▐ █ ▌▐█▐ Заказ █▐█ ▌█▌██▐ █▐█ █▌"
  val russianOcrAsLatin = "Nabopatopnar cnyx6a. Kohtakt-uehtp: 8 (812) 309 12 21"

  test("clean text passes") {
    assertEquals(TextLayerCheck.garbled(russian, Language.Russian, enabled), None)
    assertEquals(TextLayerCheck.garbled(dutch, Language.Dutch, enabled), None)
  }

  test("symbol soup is garbled") {
    assert(TextLayerCheck.garbageRatio(symbolSoup) > 0.5)
    assert(TextLayerCheck.garbled(symbolSoup, Language.Russian, enabled).isDefined)
  }

  test("wrong script for the document language is garbled") {
    assertEquals(TextLayerCheck.scriptRatio(russianOcrAsLatin, Language.Russian), 0.0)
    assert(TextLayerCheck.garbled(russianOcrAsLatin, Language.Russian, enabled).isDefined)
    assert(
      TextLayerCheck
        .garbled("Геахте мевроу, хирбей онтвангт у", Language.Dutch, enabled)
        .isDefined
    )
  }

  test("mixed-script text passes") {
    // about a quarter of the letters are Latin
    assertEquals(TextLayerCheck.garbled(russian, Language.Dutch, enabled), None)
  }

  test("text without letters is not judged by script") {
    assertEquals(TextLayerCheck.scriptRatio("12.03.2024 44,00", Language.Russian), 1.0)
  }

  test("default config disables both checks") {
    assertEquals(TextLayerCheck.garbled(symbolSoup, Language.Russian, disabled), None)
    assertEquals(
      TextLayerCheck.garbled(russianOcrAsLatin, Language.Russian, disabled),
      None
    )
  }
}
