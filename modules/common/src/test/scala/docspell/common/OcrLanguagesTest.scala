/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.common

import munit._

class OcrLanguagesTest extends FunSuite {

  test("no extra languages keeps the document language") {
    assertEquals(OcrLanguages.combine("deu", ""), "deu")
    assertEquals(OcrLanguages.combine("deu", " + "), "deu")
  }

  test("extra languages are appended in order") {
    assertEquals(OcrLanguages.combine("nld", "rus+eng"), "nld+rus+eng")
    assertEquals(OcrLanguages.combine("nld", " rus + eng "), "nld+rus+eng")
  }

  test("duplicates are dropped, document language stays first") {
    assertEquals(OcrLanguages.combine("rus", "nld+rus+eng+nld"), "rus+nld+eng")
  }
}
