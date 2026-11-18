{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Page.ManageData.Update exposing (update)

import Comp.AddonArchiveManage
import Comp.AddonRunConfigManage
import Comp.BookmarkManage
import Comp.CustomFieldManage
import Comp.EntityManage
import Comp.EntityTypeManage
import Comp.FolderManage
import Comp.TagManage
import Data.Flags exposing (Flags)
import Data.UiSettings exposing (UiSettings)
import Page.ManageData.Data exposing (..)


update : Flags -> UiSettings -> Msg -> Model -> ( Model, Cmd Msg, Sub Msg )
update flags uiSettings msg model =
    case msg of
        SetTab t ->
            let
                m =
                    { model | currentTab = Just t }
            in
            case t of
                TagTab ->
                    update flags uiSettings (TagManageMsg Comp.TagManage.LoadTags) m

                EntityTab ->
                    let
                        ( em, ec ) =
                            Comp.EntityManage.init flags
                    in
                    ( { m | entityModel = em }, Cmd.map EntityMsg ec, Sub.none )

                EntityTypeTab ->
                    let
                        ( tm, tc ) =
                            Comp.EntityTypeManage.init flags
                    in
                    ( { m | entityTypeModel = tm }, Cmd.map EntityTypeMsg tc, Sub.none )

                FolderTab ->
                    let
                        ( sm, sc ) =
                            Comp.FolderManage.init flags
                    in
                    ( { m | folderManageModel = sm }, Cmd.map FolderMsg sc, Sub.none )

                CustomFieldTab ->
                    let
                        ( cm, cc ) =
                            Comp.CustomFieldManage.init flags
                    in
                    ( { m | fieldManageModel = cm }, Cmd.map CustomFieldMsg cc, Sub.none )

                BookmarkTab ->
                    let
                        ( bm, bc ) =
                            Comp.BookmarkManage.init flags
                    in
                    ( { m | bookmarkModel = bm }, Cmd.map BookmarkMsg bc, Sub.none )

                AddonArchiveTab ->
                    let
                        ( aam, aac ) =
                            Comp.AddonArchiveManage.init flags
                    in
                    ( { m | addonArchiveModel = aam }, Cmd.map AddonArchiveMsg aac, Sub.none )

                AddonRunConfigTab ->
                    let
                        ( arm, arc ) =
                            Comp.AddonRunConfigManage.init flags
                    in
                    ( { m | addonRunConfigModel = arm }, Cmd.map AddonRunConfigMsg arc, Sub.none )

        TagManageMsg m ->
            let
                ( m2, c2 ) =
                    Comp.TagManage.update flags m model.tagManageModel
            in
            ( { model | tagManageModel = m2 }, Cmd.map TagManageMsg c2, Sub.none )

        EntityMsg lm ->
            let
                ( m2, c2 ) =
                    Comp.EntityManage.update flags lm model.entityModel
            in
            ( { model | entityModel = m2 }, Cmd.map EntityMsg c2, Sub.none )

        EntityTypeMsg lm ->
            let
                ( m2, c2 ) =
                    Comp.EntityTypeManage.update flags lm model.entityTypeModel
            in
            ( { model | entityTypeModel = m2 }, Cmd.map EntityTypeMsg c2, Sub.none )

        FolderMsg lm ->
            let
                ( m2, c2 ) =
                    Comp.FolderManage.update flags lm model.folderManageModel
            in
            ( { model | folderManageModel = m2 }
            , Cmd.map FolderMsg c2
            , Sub.none
            )

        CustomFieldMsg lm ->
            let
                ( m2, c2 ) =
                    Comp.CustomFieldManage.update flags lm model.fieldManageModel
            in
            ( { model | fieldManageModel = m2 }
            , Cmd.map CustomFieldMsg c2
            , Sub.none
            )

        BookmarkMsg lm ->
            let
                ( m2, c2, s2 ) =
                    Comp.BookmarkManage.update flags lm model.bookmarkModel
            in
            ( { model | bookmarkModel = m2 }
            , Cmd.map BookmarkMsg c2
            , Sub.map BookmarkMsg s2
            )

        AddonArchiveMsg lm ->
            let
                ( aam, aac, aas ) =
                    Comp.AddonArchiveManage.update flags lm model.addonArchiveModel
            in
            ( { model | addonArchiveModel = aam }
            , Cmd.map AddonArchiveMsg aac
            , Sub.map AddonArchiveMsg aas
            )

        AddonRunConfigMsg lm ->
            let
                ( arm, arc, ars ) =
                    Comp.AddonRunConfigManage.update flags uiSettings.timeZone lm model.addonRunConfigModel
            in
            ( { model | addonRunConfigModel = arm }
            , Cmd.map AddonRunConfigMsg arc
            , Sub.map AddonRunConfigMsg ars
            )
