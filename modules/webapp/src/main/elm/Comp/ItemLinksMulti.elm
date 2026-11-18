{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Comp.ItemLinksMulti exposing
    ( Change
    , Model
    , Msg
    , empty
    , init
    , update
    , view
    )

{-| Changes the entities linked under each role on several items. Every role has its own
mode: add the picked entities, replace the links with them, or remove them.
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
import Data.UiSettings exposing (UiSettings)
import Dict exposing (Dict)
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onClick)
import Http
import Styles as S


type alias Model =
    { roles : List EntityRole
    , types : List EntityType
    , entities : List Entity
    , pickers : Dict String (Comp.Dropdown.Model Entity)
    , modes : Dict String String
    }


{-| A change to send: role id, entity ids and the mode (add, replace or remove).
-}
type alias Change =
    { roleId : String
    , entities : List String
    , mode : String
    }


type Msg
    = RolesResp (Result Http.Error EntityRoleList)
    | TypesResp (Result Http.Error EntityTypeList)
    | EntitiesResp (Result Http.Error EntityList)
    | PickerMsg String (Comp.Dropdown.Msg Entity)
    | SetMode String String


modes : List String
modes =
    [ "add", "replace", "remove" ]


empty : Model
empty =
    { roles = [], types = [], entities = [], pickers = Dict.empty, modes = Dict.empty }


init : Flags -> Cmd Msg
init flags =
    Cmd.batch
        [ Api.getEntityRoles flags RolesResp
        , Api.getEntityTypes flags TypesResp
        , Api.getEntities flags "" "" EntitiesResp
        ]


rebuild : Model -> Model
rebuild model =
    let
        picker role =
            Comp.Dropdown.makeMultipleList
                { options = List.filter (\e -> List.isEmpty role.targets || List.member e.typeId role.targets) model.entities
                , selected = []
                }
    in
    { model | pickers = Dict.fromList (List.map (\r -> ( r.id, picker r )) model.roles) }


modeOf : String -> Model -> String
modeOf roleId model =
    Dict.get roleId model.modes |> Maybe.withDefault "add"


update : Msg -> Model -> ( Model, Cmd Msg, Maybe Change )
update msg model =
    case msg of
        RolesResp (Ok list) ->
            ( rebuild { model | roles = Data.EntityRoles.sortRoles list.items }, Cmd.none, Nothing )

        TypesResp (Ok list) ->
            ( { model | types = list.items }, Cmd.none, Nothing )

        EntitiesResp (Ok list) ->
            ( rebuild { model | entities = list.items }, Cmd.none, Nothing )

        RolesResp (Err _) ->
            ( model, Cmd.none, Nothing )

        TypesResp (Err _) ->
            ( model, Cmd.none, Nothing )

        EntitiesResp (Err _) ->
            ( model, Cmd.none, Nothing )

        PickerMsg roleId lm ->
            case Dict.get roleId model.pickers of
                Just picker ->
                    let
                        ( pm, pc ) =
                            Comp.Dropdown.update lm picker

                        change =
                            if Comp.Dropdown.isDropdownChangeMsg lm then
                                Just
                                    { roleId = roleId
                                    , entities = Comp.Dropdown.getSelected pm |> List.map .id
                                    , mode = modeOf roleId model
                                    }

                            else
                                Nothing
                    in
                    ( { model | pickers = Dict.insert roleId pm model.pickers }
                    , Cmd.map (PickerMsg roleId) pc
                    , change
                    )

                Nothing ->
                    ( model, Cmd.none, Nothing )

        SetMode roleId mode ->
            ( { model | modes = Dict.insert roleId mode model.modes }, Cmd.none, Nothing )



--- View


typeLabel : Model -> String -> String
typeLabel model typeId =
    List.filter (\t -> t.id == typeId) model.types
        |> List.head
        |> Maybe.map (\t -> Maybe.withDefault t.name t.label)
        |> Maybe.withDefault ""


view : { placeholder : String, modeLabel : String -> String } -> UiSettings -> Model -> Html Msg
view texts settings model =
    let
        cfg =
            { makeOption = \e -> { text = e.name, additional = typeLabel model e.typeId }
            , labelColor = \_ -> \_ -> ""
            , placeholder = texts.placeholder
            , style = DS.sidebarStyle
            }

        modeLink roleId mode =
            a
                [ href "#"
                , onClick (SetMode roleId mode)
                , class "px-2 py-0.5 text-xs"
                , classList
                    [ ( "bg-blue-500 text-white rounded", modeOf roleId model == mode )
                    , ( S.link, modeOf roleId model /= mode )
                    ]
                ]
                [ text (texts.modeLabel mode) ]

        viewRole role =
            div [ class "mb-4" ]
                [ div [ class "flex justify-between items-center mb-1" ]
                    [ label [ class S.inputLabel, class "mb-0" ] [ text (Maybe.withDefault role.name role.label) ]
                    , div [ class "flex" ] (List.map (modeLink role.id) modes)
                    ]
                , case Dict.get role.id model.pickers of
                    Just picker ->
                        Html.map (PickerMsg role.id) (Comp.Dropdown.view2 cfg settings picker)

                    Nothing ->
                        text ""
                ]
    in
    div [] (List.map viewRole model.roles)
