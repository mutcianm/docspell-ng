{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Comp.ItemLinksEdit exposing
    ( Model
    , Msg
    , empty
    , init
    , setItem
    , update
    , view
    )

{-| Edits the entities linked to one item, one picker per role. Roles without links or
suggestions stay hidden until added. Changes are saved right away.
-}

import Api
import Api.Model.BasicResult exposing (BasicResult)
import Api.Model.Entity exposing (Entity)
import Api.Model.EntityList exposing (EntityList)
import Api.Model.EntityRole exposing (EntityRole)
import Api.Model.EntityRoleList exposing (EntityRoleList)
import Api.Model.EntityType exposing (EntityType)
import Api.Model.EntityTypeList exposing (EntityTypeList)
import Api.Model.IdName exposing (IdName)
import Api.Model.ItemLink exposing (ItemLink)
import Api.Model.ItemProposals exposing (ItemProposals)
import Comp.Dropdown
import Data.DropdownStyle as DS
import Data.EntityRoles
import Data.Flags exposing (Flags)
import Data.UiSettings exposing (UiSettings)
import Dict exposing (Dict)
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onClick)
import Http
import Messages.Comp.ItemLinksEdit exposing (Texts)
import Set exposing (Set)
import Styles as S


type alias Model =
    { item : String
    , incoming : Bool
    , links : List ItemLink
    , roles : List EntityRole
    , types : List EntityType
    , entities : List Entity
    , pickers : Dict String (Comp.Dropdown.Model Entity)
    , added : Set String
    , addMenuOpen : Bool
    , error : Maybe String
    }


type Msg
    = RolesResp (Result Http.Error EntityRoleList)
    | TypesResp (Result Http.Error EntityTypeList)
    | EntitiesResp (Result Http.Error EntityList)
    | PickerMsg String (Comp.Dropdown.Msg Entity)
    | ToggleAddMenu
    | AddRole String
    | AddSuggestion String String
    | SaveResp (Result Http.Error BasicResult)


empty : Model
empty =
    { item = ""
    , incoming = True
    , links = []
    , roles = []
    , types = []
    , entities = []
    , pickers = Dict.empty
    , added = Set.empty
    , addMenuOpen = False
    , error = Nothing
    }


init : Flags -> ( Model, Cmd Msg )
init flags =
    ( empty
    , Cmd.batch
        [ Api.getEntityRoles flags RolesResp
        , Api.getEntityTypes flags TypesResp
        , Api.getEntities flags "" "" EntitiesResp
        ]
    )


{-| Shows the given item; resets roles added in the view of another item.
-}
setItem : String -> Bool -> List ItemLink -> Model -> Model
setItem item incoming links model =
    let
        added =
            if item == model.item then
                model.added

            else
                Set.empty
    in
    rebuild { model | item = item, incoming = incoming, links = links, added = added }


linkedIds : String -> Model -> List String
linkedIds roleId model =
    List.filter (\l -> l.roleId == roleId) model.links |> List.map .entityId


accepts : EntityRole -> Entity -> Bool
accepts role e =
    List.isEmpty role.targets || List.member e.typeId role.targets


{-| Recreates every picker from the loaded entities and the item's links.
-}
rebuild : Model -> Model
rebuild model =
    let
        picker role =
            let
                linked =
                    linkedIds role.id model

                options =
                    List.filter (\e -> accepts role e && (e.suggest || List.member e.id linked)) model.entities

                selected =
                    List.filter (\e -> List.member e.id linked) model.entities
            in
            if role.multi then
                Comp.Dropdown.makeMultipleList { options = options, selected = selected }

            else
                Comp.Dropdown.makeSingleList { options = options, selected = List.head selected }
    in
    { model | pickers = Dict.fromList (List.map (\r -> ( r.id, picker r )) model.roles) }


roleByKey : String -> Model -> Maybe EntityRole
roleByKey key model =
    List.filter (\r -> r.key == Just key) model.roles |> List.head


{-| Proposals per role id. Correspondent proposals belong to the other side (sender of
incoming, recipient of outgoing items), concerning-person proposals to the own side.
-}
suggestions : ItemProposals -> Model -> Dict String (List IdName)
suggestions props model =
    let
        corr =
            if model.incoming then
                "sender"

            else
                "recipient"

        own =
            if model.incoming then
                "recipient"

            else
                "sender"

        forRole key values =
            case roleByKey key model of
                Just r ->
                    [ ( r.id, values ) ]

                Nothing ->
                    []
    in
    (forRole corr (props.corrOrg ++ props.corrPerson)
        ++ forRole own props.concPerson
        ++ forRole "concerning" props.concEquipment
    )
        |> List.map (\( rid, vs ) -> ( rid, List.filter (\v -> not (List.member v.id (linkedIds rid model))) vs ))
        |> List.filter (\( _, vs ) -> not (List.isEmpty vs))
        |> Dict.fromList


save : Flags -> String -> List String -> Model -> Cmd Msg
save flags roleId ids model =
    Api.setItemLinks flags model.item roleId { ids = ids } SaveResp


{-| Replaces the role's links locally, so the view follows without a reload.
-}
setLinks : String -> List String -> Model -> Model
setLinks roleId ids model =
    let
        role =
            List.filter (\r -> r.id == roleId) model.roles |> List.head

        mkLink id =
            List.filter (\e -> e.id == id) model.entities
                |> List.head
                |> Maybe.map
                    (\e ->
                        ItemLink roleId
                            (Maybe.map .name role |> Maybe.withDefault "")
                            e.id
                            e.name
                            e.typeId
                            (Maybe.andThen .label role)
                    )
    in
    { model | links = List.filter (\l -> l.roleId /= roleId) model.links ++ List.filterMap mkLink ids }


update : Flags -> Msg -> Model -> ( Model, Cmd Msg )
update flags msg model =
    case msg of
        RolesResp (Ok list) ->
            ( rebuild { model | roles = Data.EntityRoles.sortRoles list.items }, Cmd.none )

        TypesResp (Ok list) ->
            ( { model | types = list.items }, Cmd.none )

        EntitiesResp (Ok list) ->
            ( rebuild { model | entities = list.items }, Cmd.none )

        RolesResp (Err _) ->
            ( model, Cmd.none )

        TypesResp (Err _) ->
            ( model, Cmd.none )

        EntitiesResp (Err _) ->
            ( model, Cmd.none )

        PickerMsg roleId lm ->
            case Dict.get roleId model.pickers of
                Just picker ->
                    let
                        ( pm, pc ) =
                            Comp.Dropdown.update lm picker

                        ids =
                            Comp.Dropdown.getSelected pm |> List.map .id

                        next =
                            { model | pickers = Dict.insert roleId pm model.pickers }
                    in
                    if Comp.Dropdown.isDropdownChangeMsg lm then
                        ( setLinks roleId ids next
                        , Cmd.batch [ save flags roleId ids next, Cmd.map (PickerMsg roleId) pc ]
                        )

                    else
                        ( next, Cmd.map (PickerMsg roleId) pc )

                Nothing ->
                    ( model, Cmd.none )

        ToggleAddMenu ->
            ( { model | addMenuOpen = not model.addMenuOpen }, Cmd.none )

        AddRole roleId ->
            ( { model | added = Set.insert roleId model.added, addMenuOpen = False }, Cmd.none )

        AddSuggestion roleId entityId ->
            let
                multi =
                    List.any (\r -> r.id == roleId && r.multi) model.roles

                ids =
                    if multi then
                        linkedIds roleId model ++ [ entityId ]

                    else
                        [ entityId ]

                next =
                    rebuild (setLinks roleId ids model)
            in
            ( next, save flags roleId ids next )

        SaveResp (Ok r) ->
            ( { model
                | error =
                    if r.success then
                        Nothing

                    else
                        Just r.message
              }
            , Cmd.none
            )

        SaveResp (Err _) ->
            ( { model | error = Just "" }, Cmd.none )



--- View


roleName : EntityRole -> String
roleName r =
    Maybe.withDefault r.name r.label


typeLabel : Model -> String -> String
typeLabel model typeId =
    List.filter (\t -> t.id == typeId) model.types
        |> List.head
        |> Maybe.map (\t -> Maybe.withDefault t.name t.label)
        |> Maybe.withDefault ""


acceptsText : Texts -> Model -> EntityRole -> String
acceptsText texts model role =
    if List.isEmpty role.targets then
        texts.anyType

    else
        List.map (typeLabel model) role.targets |> String.join ", "


view : Texts -> UiSettings -> ItemProposals -> Model -> Html Msg
view texts settings props model =
    let
        suggested =
            suggestions props model

        visible r =
            Set.member r.id model.added || not (List.isEmpty (linkedIds r.id model)) || Dict.member r.id suggested

        ( shown, hidden ) =
            List.partition visible model.roles
    in
    div []
        (List.map (viewRole texts settings model suggested) shown
            ++ [ viewAddRole texts hidden model
               , case model.error of
                    Just err ->
                        div [ class S.errorMessage, class "my-2" ]
                            [ text
                                (if err == "" then
                                    texts.saveFailed

                                 else
                                    err
                                )
                            ]

                    Nothing ->
                        text ""
               ]
        )


viewRole : Texts -> UiSettings -> Model -> Dict String (List IdName) -> EntityRole -> Html Msg
viewRole texts settings model suggested role =
    let
        cfg =
            { makeOption = \e -> { text = e.name, additional = typeLabel model e.typeId }
            , labelColor = \_ -> \_ -> ""
            , placeholder = texts.choose
            , style = DS.sidebarStyle
            }
    in
    div [ class "mb-4" ]
        [ label [ class S.inputLabel, class "flex justify-between" ]
            [ span [] [ text (roleName role) ]
            , span [ class "font-normal opacity-60 text-xs" ] [ text (acceptsText texts model role) ]
            ]
        , case Dict.get role.id model.pickers of
            Just picker ->
                Html.map (PickerMsg role.id) (Comp.Dropdown.view2 cfg settings picker)

            Nothing ->
                text ""
        , case Dict.get role.id suggested of
            Just values ->
                div [ class "flex flex-wrap items-center gap-1 mt-1 text-sm" ]
                    (span [ class "opacity-60 mr-1" ] [ text texts.suggestions ]
                        :: List.map
                            (\v ->
                                a [ href "#", class S.link, class "mr-2", onClick (AddSuggestion role.id v.id) ]
                                    [ text v.name ]
                            )
                            (List.take 6 values)
                    )

            Nothing ->
                text ""
        ]


viewAddRole : Texts -> List EntityRole -> Model -> Html Msg
viewAddRole texts hidden model =
    if List.isEmpty hidden then
        text ""

    else
        div [ class "mb-4" ]
            [ a [ href "#", class S.link, class "text-sm", onClick ToggleAddMenu ]
                [ i [ class "fa fa-plus mr-1" ] [], text texts.addRole ]
            , if model.addMenuOpen then
                div [ class "flex flex-col mt-1 ml-4 text-sm" ]
                    (List.map
                        (\r -> a [ href "#", class S.link, class "py-1", onClick (AddRole r.id) ] [ text (roleName r) ])
                        hidden
                    )

              else
                text ""
            ]
