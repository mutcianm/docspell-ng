/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.extract

import java.lang.Character.UnicodeScript

import docspell.common.Language

/** Heuristics to detect a garbled text layer in a PDF: broken font encodings produce
  * symbol soup, OCR done in the wrong language produces letters of the wrong script.
  */
object TextLayerCheck {

  /** Share of non-whitespace characters that are symbols (other than currency or math),
    * unassigned, private-use or control characters, or the replacement character.
    */
  def garbageRatio(text: String): Double = {
    val chars = text.filterNot(_.isWhitespace)
    if (chars.isEmpty) 0.0
    else chars.count(isGarbage).toDouble / chars.length
  }

  /** Share of letters written in a script expected for the language (1.0 without
    * letters).
    */
  def scriptRatio(text: String, lang: Language): Double = {
    val expected = scripts(lang)
    val letters = text.codePoints().toArray.filter(Character.isLetter)
    if (letters.isEmpty) 1.0
    else letters.count(cp => expected(UnicodeScript.of(cp))).toDouble / letters.length
  }

  /** Returns why the text looks garbled, if it does. */
  def garbled(text: String, lang: Language, cfg: PdfConfig): Option[String] = {
    val garbage = garbageRatio(text)
    lazy val script = scriptRatio(text, lang)
    if (garbage > cfg.maxGarbageRatio)
      Some(f"$garbage%.2f of the characters are garbage (max ${cfg.maxGarbageRatio})")
    else if (script < cfg.minScriptRatio)
      Some(
        f"only $script%.2f of the letters match ${lang.iso3} (min ${cfg.minScriptRatio})"
      )
    else None
  }

  private def isGarbage(c: Char): Boolean =
    c == '\uFFFD' || (Character.getType(c).toByte match {
      case Character.OTHER_SYMBOL | Character.MODIFIER_SYMBOL | Character.PRIVATE_USE |
          Character.UNASSIGNED | Character.CONTROL | Character.SURROGATE =>
        true
      case _ => false
    })

  private def scripts(lang: Language): Set[UnicodeScript] =
    lang match {
      case Language.Russian | Language.Ukrainian => Set(UnicodeScript.CYRILLIC)
      case Language.Hebrew                       => Set(UnicodeScript.HEBREW)
      case Language.Khmer                        => Set(UnicodeScript.KHMER)
      case Language.Japanese | Language.JpnVert =>
        Set(UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA)
      case _ => Set(UnicodeScript.LATIN)
    }
}
