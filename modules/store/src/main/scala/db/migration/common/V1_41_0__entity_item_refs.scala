/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package db.migration.common

import org.flywaydb.core.api.migration.{BaseJavaMigration, Context}

/** Points the item's corr/conc columns at the entity table (#24). The columns are kept in
  * sync with the item's links; foreign key names differ per database, so they are read
  * from the JDBC metadata.
  */
class V1_41_0__entity_item_refs extends BaseJavaMigration {
  private val columns = List("corrorg", "corrperson", "concperson", "concequipment")

  override def migrate(context: Context): Unit = {
    val conn = context.getConnection
    val product = conn.getMetaData.getDatabaseProductName.toLowerCase
    val mariadb = product.contains("maria") || product.contains("mysql")
    def quote(name: String) = if (mariadb) s"`$name`" else "\"" + name + "\""

    val keys = conn.getMetaData.getImportedKeys(conn.getCatalog, null, "item")
    val names = Iterator
      .continually(keys)
      .takeWhile(_.next())
      .collect {
        case rs if columns.contains(rs.getString("FKCOLUMN_NAME").toLowerCase) =>
          rs.getString("FK_NAME")
      }
      .toSet
    keys.close()

    val stmt = conn.createStatement()
    try {
      names.foreach { fk =>
        val drop = if (mariadb) "drop foreign key" else "drop constraint"
        stmt.execute(s"alter table ${quote("item")} $drop ${quote(fk)}")
      }
      columns.foreach { col =>
        stmt.execute(
          s"alter table ${quote("item")} add foreign key (${quote(col)}) references ${quote("entity")}(${quote("id")})"
        )
      }
    } finally stmt.close()
  }
}
