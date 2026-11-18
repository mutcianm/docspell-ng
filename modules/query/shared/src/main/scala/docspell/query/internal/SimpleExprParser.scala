/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.query.internal

import cats.data.{NonEmptyList => Nel}
import cats.parse.{Parser => P}

import docspell.query.ItemQuery._
import docspell.query.internal.{Constants => C}

object SimpleExprParser {

  private[this] val op: P[Operator] =
    OperatorParser.op.surroundedBy(BasicParser.ws0)

  private[this] val inOp: P[Unit] =
    P.string(C.in).surroundedBy(BasicParser.ws0)

  private[this] val inOrOpStr =
    P.eitherOr(op ~ BasicParser.singleString, inOp *> BasicParser.stringOrMore)

  private[this] val inOrOpDate =
    P.eitherOr(op ~ DateParser.date, inOp *> DateParser.dateOrMore)

  val stringExpr: P[Expr] =
    (AttrParser.stringAttr ~ inOrOpStr).map {
      case (attr, Right((op, value))) =>
        Expr.SimpleExpr(op, Property.StringProperty(attr, value))
      case (attr, Left(values)) =>
        Expr.InExpr(attr, values)
    }

  val dateExpr: P[Expr] =
    (AttrParser.dateAttr ~ inOrOpDate).map {
      case (attr, Right((op, value))) =>
        Expr.SimpleExpr(op, Property.DateProperty(attr, value))
      case (attr, Left(values)) =>
        Expr.InDateExpr(attr, values)
    }

  val existsExpr: P[Expr.Exists] =
    (P.ignoreCase(C.exist) *> P.char(C.like) *> AttrParser.anyAttr).map(attr =>
      Expr.Exists(attr)
    )

  val fulltextExpr: P[Expr.Fulltext] =
    (P.ignoreCase(C.content) *> P.char(C.like) *> BasicParser.singleString).map(q =>
      Expr.Fulltext(q)
    )

  val tagIdExpr: P[Expr.TagIdsMatch] =
    (P.ignoreCase(C.tagId) *> OperatorParser.tagOp ~ BasicParser.stringOrMore).map {
      case (op, values) =>
        Expr.TagIdsMatch(op, values)
    }

  val tagExpr: P[Expr.TagsMatch] =
    (P.ignoreCase(C.tag) *> OperatorParser.tagOp ~ BasicParser.stringOrMore).map {
      case (op, values) =>
        Expr.TagsMatch(op, values)
    }

  val catExpr: P[Expr.TagCategoryMatch] =
    (P.ignoreCase(C.cat) *> OperatorParser.tagOp ~ BasicParser.stringOrMore).map {
      case (op, values) =>
        Expr.TagCategoryMatch(op, values)
    }

  val customFieldExpr: P[Expr.CustomFieldMatch] =
    (P.string(C.customField) *> P.char(
      C.like
    ) *> BasicParser.identParser ~ op ~ BasicParser.singleString)
      .map { case ((name, op), value) =>
        Expr.CustomFieldMatch(name, op, value)
      }

  val customFieldIdExpr: P[Expr.CustomFieldIdMatch] =
    (P.string(C.customFieldId) *> P.char(
      C.like
    ) *> BasicParser.identParser ~ op ~ BasicParser.singleString)
      .map { case ((name, op), value) =>
        Expr.CustomFieldIdMatch(name, op, value)
      }

  val inboxExpr: P[Expr.InboxExpr] =
    (P.string(C.inbox) *> P.char(C.like) *> BasicParser.bool).map(Expr.InboxExpr.apply)

  val dirExpr: P[Expr.DirectionExpr] =
    (P.string(C.incoming) *> P.char(C.like) *> BasicParser.bool)
      .map(Expr.DirectionExpr.apply)

  val checksumExpr: P[Expr.ChecksumMatch] =
    (P.string(C.checksum) *> P.char(C.like) *> BasicParser.singleString)
      .map(Expr.ChecksumMatch.apply)

  val attachIdExpr: P[Expr.AttachId] =
    (P.ignoreCase(C.attachId) *> P.char(C.eqs) *> BasicParser.singleString)
      .map(Expr.AttachId.apply)

  private[this] val entityName: P[String] =
    P.charsWhile(BasicParser.nameChars.contains)

  private[this] val unreservedName: P[String] =
    entityName.flatMap(n => if (C.reserved.contains(n.toLowerCase)) P.fail else P.pure(n))

  val roleIdExpr: P[Expr.RoleIdMatch] =
    ((unreservedName <* P.ignoreCase(".id")) ~ P.eitherOr(
      inOp *> BasicParser.stringOrMore,
      P.char(C.eqs) *> BasicParser.singleString.map(Nel.one)
    )).map { case (role, ids) => Expr.RoleIdMatch(role, ids.merge) }

  val entityAttrExpr: P[Expr.EntityAttrMatch] =
    ((unreservedName <* P.char('.')) ~ entityName ~ op ~ BasicParser.singleString)
      .map { case (((t, attr), op), value) => Expr.EntityAttrMatch(t, attr, op, value) }

  val roleExpr: P[Expr.RoleMatch] =
    (unreservedName ~ op ~ BasicParser.singleString).map { case ((role, op), value) =>
      Expr.RoleMatch(role, op, value)
    }

  // tried first and backtracking, so reserved names fall through to the fixed syntax
  val entityExpr: P[Expr] =
    P.oneOf(List(roleIdExpr.backtrack, entityAttrExpr.backtrack, roleExpr.backtrack))

  val simpleExpr: P[Expr] =
    P.oneOf(
      List(
        entityExpr,
        dateExpr,
        stringExpr,
        existsExpr,
        fulltextExpr,
        tagIdExpr,
        tagExpr,
        catExpr,
        customFieldIdExpr,
        customFieldExpr,
        inboxExpr,
        dirExpr,
        checksumExpr,
        attachIdExpr
      )
    )
}
