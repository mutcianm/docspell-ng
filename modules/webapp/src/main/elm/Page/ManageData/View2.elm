{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Page.ManageData.View2 exposing (viewContent, viewSidebar)

import Comp.AddonArchiveManage
import Comp.AddonRunConfigManage
import Comp.BookmarkManage
import Comp.CustomFieldManage
import Comp.EntityManage
import Comp.EntityTypeManage
import Comp.FolderManage
import Comp.TagManage
import Data.Fields
import Data.Flags exposing (Flags)
import Data.Icons as Icons
import Data.UiSettings exposing (UiSettings)
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onClick)
import Messages.Page.ManageData exposing (Texts)
import Page.ManageData.Data exposing (..)
import Styles as S


viewSidebar : Texts -> Bool -> Flags -> UiSettings -> Model -> Html Msg
viewSidebar texts visible flags settings model =
    div
        [ id "sidebar"
        , class S.sidebar
        , class S.sidebarBg
        , classList [ ( "hidden", not visible ) ]
        ]
        [ div [ class "" ]
            [ h1 [ class S.header1 ]
                [ text texts.manageData
                ]
            ]
        , div [ class "flex flex-col my-2" ]
            [ a
                [ href "#"
                , onClick (SetTab TagTab)
                , class S.sidebarLink
                , menuEntryActive model TagTab
                ]
                [ Icons.tagIcon ""
                , span
                    [ class "ml-3" ]
                    [ text texts.basics.tags
                    ]
                ]
            , a
                [ href "#"
                , onClick (SetTab EntityTab)
                , menuEntryActive model EntityTab
                , class S.sidebarLink
                ]
                [ Icons.organizationIcon ""
                , span
                    [ class "ml-3" ]
                    [ text texts.entities
                    ]
                ]
            , a
                [ href "#"
                , onClick (SetTab EntityTypeTab)
                , menuEntryActive model EntityTypeTab
                , class S.sidebarLink
                ]
                [ i [ class "fa fa-shapes" ] []
                , span
                    [ class "ml-3" ]
                    [ text texts.entityTypes
                    ]
                ]
            , a
                [ href "#"
                , classList
                    [ ( "hidden"
                      , Data.UiSettings.fieldHidden settings Data.Fields.Folder
                      )
                    ]
                , onClick (SetTab FolderTab)
                , menuEntryActive model FolderTab
                , class S.sidebarLink
                ]
                [ Icons.folderIcon ""
                , span
                    [ class "ml-3" ]
                    [ text texts.basics.folder
                    ]
                ]
            , a
                [ href "#"
                , classList
                    [ ( "invisible hidden"
                      , Data.UiSettings.fieldHidden settings Data.Fields.CustomFields
                      )
                    ]
                , onClick (SetTab CustomFieldTab)
                , menuEntryActive model CustomFieldTab
                , class S.sidebarLink
                ]
                [ Icons.customFieldIcon2 ""
                , span
                    [ class "ml-3" ]
                    [ text texts.basics.customFields
                    ]
                ]
            , a
                [ href "#"
                , onClick (SetTab BookmarkTab)
                , menuEntryActive model BookmarkTab
                , class S.sidebarLink
                ]
                [ i [ class "fa fa-bookmark" ] []
                , span
                    [ class "ml-3" ]
                    [ text texts.bookmarks
                    ]
                ]
            , a
                [ href "#"
                , onClick (SetTab AddonArchiveTab)
                , menuEntryActive model AddonArchiveTab
                , class S.sidebarLink
                , classList [ ( "hidden", not flags.config.addonsEnabled ) ]
                ]
                [ Icons.addonIcon ""
                , span
                    [ class "ml-3" ]
                    [ text texts.addonArchives
                    ]
                ]
            , a
                [ href "#"
                , onClick (SetTab AddonRunConfigTab)
                , menuEntryActive model AddonRunConfigTab
                , class S.sidebarLink
                , classList [ ( "hidden", not flags.config.addonsEnabled ) ]
                ]
                [ Icons.addonRunConfigIcon ""
                , span
                    [ class "ml-3" ]
                    [ text texts.addonRunConfigs
                    ]
                ]
            ]
        ]


viewContent : Texts -> Flags -> UiSettings -> Model -> Html Msg
viewContent texts flags settings model =
    div
        [ id "content"
        , class S.content
        ]
        (case model.currentTab of
            Just TagTab ->
                viewTags texts settings model

            Just EntityTab ->
                viewEntities texts model

            Just EntityTypeTab ->
                viewEntityTypes texts model

            Just FolderTab ->
                viewFolder texts flags settings model

            Just CustomFieldTab ->
                viewCustomFields texts flags settings model

            Just BookmarkTab ->
                viewBookmarks texts flags settings model

            Just AddonArchiveTab ->
                if flags.config.addonsEnabled then
                    viewAddonArchives texts flags settings model

                else
                    []

            Just AddonRunConfigTab ->
                if flags.config.addonsEnabled then
                    viewAddonRunConfigs texts flags settings model

                else
                    []

            Nothing ->
                []
        )


menuEntryActive : Model -> Tab -> Attribute msg
menuEntryActive model tab =
    if model.currentTab == Just tab then
        class S.sidebarMenuItemActive

    else
        class ""


viewTags : Texts -> UiSettings -> Model -> List (Html Msg)
viewTags texts settings model =
    [ h2
        [ class S.header1
        , class "inline-flex items-center"
        ]
        [ Icons.tagIcon ""
        , div [ class "ml-2" ]
            [ text texts.basics.tags
            ]
        ]
    , Html.map TagManageMsg
        (Comp.TagManage.view2
            texts.tagManage
            settings
            model.tagManageModel
        )
    ]


viewEntities : Texts -> Model -> List (Html Msg)
viewEntities texts model =
    [ h2
        [ class S.header1
        , class "inline-flex items-center"
        ]
        [ Icons.organizationIcon ""
        , div [ class "ml-2" ]
            [ text texts.entities
            ]
        ]
    , Html.map EntityMsg (Comp.EntityManage.view texts.entityManage model.entityModel)
    ]


viewEntityTypes : Texts -> Model -> List (Html Msg)
viewEntityTypes texts model =
    [ h2
        [ class S.header1
        , class "inline-flex items-center"
        ]
        [ i [ class "fa fa-shapes" ] []
        , div [ class "ml-2" ]
            [ text texts.entityTypes
            ]
        ]
    , Html.map EntityTypeMsg (Comp.EntityTypeManage.view texts.entityTypeManage model.entityTypeModel)
    ]


viewFolder : Texts -> Flags -> UiSettings -> Model -> List (Html Msg)
viewFolder texts flags _ model =
    [ h2
        [ class S.header1
        , class "inline-flex items-center"
        ]
        [ Icons.folderIcon ""
        , div
            [ class "ml-2"
            ]
            [ text texts.basics.folder
            ]
        ]
    , Html.map FolderMsg
        (Comp.FolderManage.view2 texts.folderManage flags model.folderManageModel)
    ]


viewCustomFields : Texts -> Flags -> UiSettings -> Model -> List (Html Msg)
viewCustomFields texts flags _ model =
    [ h2
        [ class S.header1
        , class "inline-flex items-center"
        ]
        [ Icons.customFieldIcon2 ""
        , div [ class "ml-2" ]
            [ text texts.basics.customFields
            ]
        ]
    , Html.map CustomFieldMsg
        (Comp.CustomFieldManage.view2 texts.customFieldManage
            flags
            model.fieldManageModel
        )
    ]


viewBookmarks : Texts -> Flags -> UiSettings -> Model -> List (Html Msg)
viewBookmarks texts flags settings model =
    [ h2
        [ class S.header1
        , class "inline-flex items-center"
        ]
        [ i [ class "fa fa-bookmark" ] []
        , div [ class "ml-2" ]
            [ text texts.bookmarks
            ]
        ]
    , Html.map BookmarkMsg (Comp.BookmarkManage.view texts.bookmarkManage settings flags model.bookmarkModel)
    ]


viewAddonArchives : Texts -> Flags -> UiSettings -> Model -> List (Html Msg)
viewAddonArchives texts flags settings model =
    [ h2
        [ class S.header1
        , class "inline-flex items-center"
        ]
        [ Icons.addonIcon ""
        , div [ class "ml-2" ]
            [ text texts.addonArchives
            ]
        ]
    , Html.map AddonArchiveMsg (Comp.AddonArchiveManage.view texts.addonArchiveManage settings flags model.addonArchiveModel)
    ]


viewAddonRunConfigs : Texts -> Flags -> UiSettings -> Model -> List (Html Msg)
viewAddonRunConfigs texts flags settings model =
    [ h2
        [ class S.header1
        , class "inline-flex items-center"
        ]
        [ Icons.addonRunConfigIcon "mr-4"
        , div [ class "ml-2" ]
            [ text texts.addonRunConfigs
            ]
        ]
    , Html.map AddonRunConfigMsg (Comp.AddonRunConfigManage.view texts.addonRunConfigManage settings flags model.addonRunConfigModel)
    ]
