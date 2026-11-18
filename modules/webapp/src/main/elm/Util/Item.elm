{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Util.Item exposing
    ( concTemplate
    , corrTemplate
    , roleLabel
    , toItemLight
    )

import Api.Model.Attachment exposing (Attachment)
import Api.Model.AttachmentLight exposing (AttachmentLight)
import Api.Model.ItemDetail exposing (ItemDetail)
import Api.Model.ItemLight exposing (ItemLight)
import Messages.Basics
import Data.Fields
import Data.ItemTemplate as IT exposing (ItemTemplate)
import Api.Model.ItemLink exposing (ItemLink)
import Data.UiSettings exposing (UiSettings)


toItemLight : ItemDetail -> ItemLight
toItemLight detail =
    { id = detail.id
    , name = detail.name
    , state = detail.state
    , date = Maybe.withDefault detail.created detail.itemDate
    , dueDate = detail.dueDate
    , source = detail.source
    , direction = Just detail.direction
    , corrOrg = detail.corrOrg
    , corrPerson = detail.corrPerson
    , concPerson = detail.concPerson
    , concEquipment = detail.concEquipment
    , folder = detail.folder
    , attachments = List.indexedMap toAttachmentLight detail.attachments
    , tags = detail.tags
    , customfields = detail.customfields
    , notes = detail.notes
    , relatedItems = List.map .id detail.relatedItems
    , highlighting = []
    , links = detail.links
    }


toAttachmentLight : Int -> Attachment -> AttachmentLight
toAttachmentLight index attach =
    { id = attach.id
    , position = index
    , name = attach.name
    , pageCount = Nothing
    }


corrTemplate : UiSettings -> ItemTemplate
corrTemplate settings =
    let
        fieldHidden f =
            Data.UiSettings.fieldHidden settings f

        hiddenTuple =
            ( fieldHidden Data.Fields.CorrOrg, fieldHidden Data.Fields.CorrPerson )
    in
    case hiddenTuple of
        ( True, True ) ->
            IT.empty

        ( True, False ) ->
            IT.corrPerson

        ( False, True ) ->
            IT.corrOrg

        ( False, False ) ->
            IT.correspondent


concTemplate : UiSettings -> ItemTemplate
concTemplate settings =
    let
        fieldHidden f =
            Data.UiSettings.fieldHidden settings f

        hiddenTuple =
            ( fieldHidden Data.Fields.ConcPerson, fieldHidden Data.Fields.ConcEquip )
    in
    case hiddenTuple of
        ( True, True ) ->
            IT.empty

        ( True, False ) ->
            IT.concEquip

        ( False, True ) ->
            IT.concPerson

        ( False, False ) ->
            IT.concerning


{-| How a role is named on cards: sender and recipient read as From and To.
-}
roleLabel : Messages.Basics.Texts -> ItemLink -> String
roleLabel texts link =
    case link.roleName of
        "sender" ->
            texts.from

        "recipient" ->
            texts.to

        "concerning" ->
            texts.concerning

        _ ->
            Maybe.withDefault link.roleName link.roleLabel
