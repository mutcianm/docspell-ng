/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.records

import docspell.common._

/** The former record shape, built from entities for the read-only compatibility API. */
case class REquipment(
    eid: Ident,
    cid: CollectiveId,
    name: String,
    created: Timestamp,
    updated: Timestamp,
    notes: Option[String],
    use: EquipmentUse
)
