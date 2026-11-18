{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Comp.RoleSearch exposing
    ( Model
    , Msg
    , empty
    , init
    , isChangeMsg
    , isEmpty
    , queries
    , reset
    , select
    , update
    , view
    )

{-| Search filters by linked entities, one picker per role. Several entities in one role
match any of them; different roles must all match.
-}

import Api
import Api.Model.Entity exposing (Entity)
import Api.Model.EntityList exposing (EntityList)
import Api.Model.EntityRole exposing (EntityRole)
import Api.Model.EntityRoleList exposing (EntityRoleList)
import Api.Model.EntityType exposing (EntityType)
import Api.Model.EntityTypeList exposing (EntityTypeList)
import Comp.Dropdown
import Data.DropdownStyle as DS
import Data.EntityRoles
import Data.Flags exposing (Flags)
import Data.ItemQuery as Q exposing (ItemQuery)
import Data.UiSettings exposing (UiSettings)
import Dict exposing (Dict)
import Html exposing (..)
import Html.Attributes exposing (..)
import Http
import Styles as S


type alias Model =
    { roles : List EntityRole
    , types : List EntityType
    , entities : List Entity
    , pickers : Dict String (Comp.Dropdown.Model Entity)
    , pending : List ( String, String )
    }


type Msg
    = RolesResp (Result Http.Error EntityRoleList)
    | TypesResp (Result Http.Error EntityTypeList)
    | EntitiesResp (Result Http.Error EntityList)
    | PickerMsg String (Comp.Dropdown.Msg Entity)


empty : Model
empty =
    { roles = [], types = [], entities = [], pickers = Dict.empty, pending = [] }


init : Flags -> Model -> ( Model, Cmd Msg )
init flags model =
    ( model
    , Cmd.batch
        [ Api.getEntityRoles flags RolesResp
        , Api.getEntityTypes flags TypesResp
        , Api.getEntities flags "" "" EntitiesResp
        ]
    )


isChangeMsg : Msg -> Bool
isChangeMsg msg =
    case msg of
        PickerMsg _ lm ->
            Comp.Dropdown.isDropdownChangeMsg lm

        _ ->
            False


selected : String -> Model -> List Entity
selected roleId model =
    Dict.get roleId model.pickers |> Maybe.map Comp.Dropdown.getSelected |> Maybe.withDefault []


isEmpty : Model -> Bool
isEmpty model =
    List.all (\r -> List.isEmpty (selected r.id model)) model.roles


queries : Model -> List ItemQuery
queries model =
    List.filterMap
        (\r ->
            case selected r.id model of
                [] ->
                    Nothing

                es ->
                    Just (Q.RoleIds r.name (List.map .id es))
        )
        model.roles


reset : Model -> Model
reset model =
    rebuild [] model


{-| Selects only the given entity under the role, e.g. when following a link on an item.
-}
select : String -> String -> Model -> Model
select roleId entityId model =
    if List.isEmpty model.roles || List.isEmpty model.entities then
        { model | pending = [ ( roleId, entityId ) ] }

    else
        rebuild [ ( roleId, entityId ) ] model


rebuild : List ( String, String ) -> Model -> Model
rebuild selection model =
    let
        picker role =
            let
                options =
                    List.filter (\e -> List.isEmpty role.targets || List.member e.typeId role.targets) model.entities

                chosen =
                    List.filter (\e -> List.member ( role.id, e.id ) selection) options
            in
            Comp.Dropdown.makeMultipleList { options = options, selected = chosen }
    in
    { model
        | pickers = Dict.fromList (List.map (\r -> ( r.id, picker r )) model.roles)
        , pending =
            if List.isEmpty model.roles || List.isEmpty model.entities then
                selection

            else
                []
    }


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        RolesResp (Ok list) ->
            ( rebuild model.pending { model | roles = Data.EntityRoles.sortRoles list.items }, Cmd.none )

        TypesResp (Ok list) ->
            ( { model | types = list.items }, Cmd.none )

        EntitiesResp (Ok list) ->
            ( rebuild model.pending { model | entities = list.items }, Cmd.none )

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
                    in
                    ( { model | pickers = Dict.insert roleId pm model.pickers }, Cmd.map (PickerMsg roleId) pc )

                Nothing ->
                    ( model, Cmd.none )



--- View


typeLabel : Model -> String -> String
typeLabel model typeId =
    List.filter (\t -> t.id == typeId) model.types
        |> List.head
        |> Maybe.map (\t -> Maybe.withDefault t.name t.label)
        |> Maybe.withDefault ""


view : String -> UiSettings -> Model -> Html Msg
view placeholder settings model =
    let
        cfg =
            { makeOption = \e -> { text = e.name, additional = typeLabel model e.typeId }
            , labelColor = \_ -> \_ -> ""
            , placeholder = placeholder
            , style = DS.sidebarStyle
            }

        viewRole role =
            div [ class "mb-4" ]
                [ label [ class S.inputLabel ] [ text (Maybe.withDefault role.name role.label) ]
                , case Dict.get role.id model.pickers of
                    Just picker ->
                        Html.map (PickerMsg role.id) (Comp.Dropdown.view2 cfg settings picker)

                    Nothing ->
                        text ""
                ]
    in
    div [] (List.map viewRole model.roles)
