/*
 * Copyright 2020 Eike K. & Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package docspell.store.queries

import docspell.common._
import docspell.store.records._

case class ItemData(
    item: RItem,
    corrOrg: Option[IdRef],
    corrPerson: Option[IdRef],
    concPerson: Option[IdRef],
    concEquip: Option[IdRef],
    folder: Option[IdRef],
    tags: Vector[RTag],
    attachments: Vector[(RAttachment, RFileMeta)],
    sources: Vector[(RAttachmentSource, RFileMeta)],
    archives: Vector[(RAttachmentArchive, RFileMeta)],
    customFields: Vector[ItemFieldValue],
    relatedItems: Vector[ListItem],
    links: Vector[QEntity.ItemLink]
) {

  def filterCollective(coll: CollectiveId): Option[ItemData] =
    if (item.cid == coll) Some(this) else None
}
