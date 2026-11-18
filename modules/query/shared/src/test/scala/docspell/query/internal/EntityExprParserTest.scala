/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.query.internal

import cats.data.{NonEmptyList => Nel}

import docspell.query.ItemQuery._

import munit._

class EntityExprParserTest extends FunSuite {

  def parse(s: String): Either[String, Expr] =
    ExprParser.parseQuery(s).left.map(_.toString).map(_.expr)

  test("role expressions") {
    assertEquals(
      parse("sender:acme"),
      Right(Expr.RoleMatch("sender", Operator.Like, "acme"))
    )
    assertEquals(
      parse("Sender = \"Acme Ltd\""),
      Right(Expr.RoleMatch("Sender", Operator.Eq, "Acme Ltd"))
    )
    assertEquals(
      parse("insured-object!=car"),
      Right(Expr.RoleMatch("insured-object", Operator.Neq, "car"))
    )
  }

  test("role id expressions") {
    assertEquals(parse("sender.id=abc"), Right(Expr.RoleIdMatch("sender", Nel.of("abc"))))
    assertEquals(
      parse("sender.id~=a,b"),
      Right(Expr.RoleIdMatch("sender", Nel.of("a", "b")))
    )
  }

  test("entity attribute expressions") {
    assertEquals(
      parse("vehicle.plate:AB*"),
      Right(Expr.EntityAttrMatch("vehicle", "plate", Operator.Like, "AB*"))
    )
    assertEquals(
      parse("vehicle.idnumber=7"),
      Right(Expr.EntityAttrMatch("vehicle", "idnumber", Operator.Eq, "7"))
    )
  }

  test("reserved names keep their fixed meaning") {
    assertEquals(
      parse("name:hello"),
      Right(Expr.SimpleExpr(Operator.Like, Property(Attr.ItemName, "hello")))
    )
    assertEquals(
      parse("corr.org.name:acme"),
      Right(Expr.SimpleExpr(Operator.Like, Property(Attr.Correspondent.OrgName, "acme")))
    )
    assertEquals(parse("corr:acme"), Right(Expr.CorrMacro("acme")))
    assertEquals(
      parse("f:total>10"),
      Right(Expr.CustomFieldMatch("total", Operator.Gt, "10"))
    )
    assertEquals(parse("tag:invoice").map(_.isInstanceOf[Expr.TagsMatch]), Right(true))
  }

  test("names that only start like a keyword are roles") {
    assertEquals(
      parse("nameplate:x"),
      Right(Expr.RoleMatch("nameplate", Operator.Like, "x"))
    )
    assertEquals(
      parse("concerning:bob"),
      Right(Expr.RoleMatch("concerning", Operator.Like, "bob"))
    )
    assertEquals(parse("(& sender:a concerning:b)").isRight, true)
  }
}
