/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.records

import docspell.common._

/** The former record shape, built from entities for the read-only compatibility API. */
case class RContact(
    contactId: Ident,
    value: String,
    kind: ContactKind,
    personId: Option[Ident],
    orgId: Option[Ident],
    created: Timestamp
)
