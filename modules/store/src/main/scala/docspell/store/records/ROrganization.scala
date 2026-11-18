/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.records

import docspell.common._

/** The former record shape, built from entities for the read-only compatibility API. */
case class ROrganization(
    oid: Ident,
    cid: CollectiveId,
    name: String,
    street: String,
    zip: String,
    city: String,
    country: String,
    notes: Option[String],
    created: Timestamp,
    updated: Timestamp,
    shortName: Option[String],
    use: OrgUse
)
