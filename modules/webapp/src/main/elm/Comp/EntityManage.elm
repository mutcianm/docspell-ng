{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Comp.EntityManage exposing
    ( Model
    , Msg
    , init
    , update
    , view
    )

import Api
import Api.Model.AttrDef exposing (AttrDef)
import Api.Model.BasicResult exposing (BasicResult)
import Api.Model.Entity exposing (Entity)
import Api.Model.EntityAttrValue exposing (EntityAttrValue)
import Api.Model.EntityList exposing (EntityList)
import Api.Model.EntityType exposing (EntityType)
import Api.Model.EntityTypeList exposing (EntityTypeList)
import Api.Model.IdResult exposing (IdResult)
import Api.Model.NewEntity exposing (NewEntity)
import Comp.Basic as B
import Comp.MenuBar as MB
import Comp.YesNoDimmer
import Data.Flags exposing (Flags)
import Dict exposing (Dict)
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onCheck, onClick, onInput)
import Http
import Messages.Comp.EntityManage exposing (Texts)
import Styles as S


{-| Lists and edits the entities of one entity type at a time.
-}
type alias Model =
    { types : List EntityType
    , selected : Maybe EntityType
    , entities : List Entity
    , query : String
    , form : Maybe Form
    , error : Maybe (Result Http.Error String)
    , loading : Bool
    , deleteDimmer : Comp.YesNoDimmer.Model
    }


type alias Form =
    { id : String
    , name : String
    , notes : String
    , suggest : Bool

    -- values per attribute id; single-valued attributes have at most one entry
    , values : Dict String (List String)
    }


type Msg
    = TypesResp (Result Http.Error EntityTypeList)
    | SelectType EntityType
    | EntitiesResp (Result Http.Error EntityList)
    | SetQuery String
    | InitNew
    | EditEntity Entity
    | SetName String
    | SetNotes String
    | SetSuggest Bool
    | SetValue String Int String
    | AddValue String
    | RemoveValue String Int
    | Submit
    | SaveResp (Result Http.Error BasicResult)
    | CreateResp (Result Http.Error IdResult)
    | RequestDelete
    | DeleteMsg Comp.YesNoDimmer.Msg
    | Cancel


init : Flags -> ( Model, Cmd Msg )
init flags =
    ( { types = []
      , selected = Nothing
      , entities = []
      , query = ""
      , form = Nothing
      , error = Nothing
      , loading = True
      , deleteDimmer = Comp.YesNoDimmer.emptyModel
      }
    , Api.getEntityTypes flags TypesResp
    )


emptyForm : Form
emptyForm =
    { id = "", name = "", notes = "", suggest = True, values = Dict.empty }


formOf : Entity -> Form
formOf e =
    let
        add v dict =
            Dict.update v.attrId (\vs -> Just (Maybe.withDefault [] vs ++ [ v.value ])) dict
    in
    { id = e.id
    , name = e.name
    , notes = Maybe.withDefault "" e.notes
    , suggest = e.suggest
    , values = List.foldl add Dict.empty e.attrs
    }


toNewEntity : EntityType -> Form -> NewEntity
toNewEntity et form =
    { typeId = et.id
    , name = form.name
    , notes =
        if String.trim form.notes == "" then
            Nothing

        else
            Just form.notes
    , suggest = form.suggest
    , attrs =
        Dict.toList form.values
            |> List.concatMap (\( aid, vs ) -> List.map (EntityAttrValue aid Nothing) vs)
    }


loadEntities : Flags -> Model -> Cmd Msg
loadEntities flags model =
    case model.selected of
        Just et ->
            Api.getEntities flags et.id model.query EntitiesResp

        Nothing ->
            Cmd.none


updateForm : (Form -> Form) -> Model -> ( Model, Cmd Msg )
updateForm f model =
    ( { model | form = Maybe.map f model.form }, Cmd.none )


setAt : Int -> a -> List a -> List a
setAt idx v =
    List.indexedMap
        (\i x ->
            if i == idx then
                v

            else
                x
        )


removeAt : Int -> List a -> List a
removeAt idx list =
    List.take idx list ++ List.drop (idx + 1) list


update : Flags -> Msg -> Model -> ( Model, Cmd Msg )
update flags msg model =
    case msg of
        TypesResp (Ok list) ->
            let
                selected =
                    case model.selected of
                        Just et ->
                            List.filter (\t -> t.id == et.id) list.items |> List.head

                        Nothing ->
                            List.head list.items

                next =
                    { model | types = list.items, selected = selected, loading = False }
            in
            ( next, loadEntities flags next )

        TypesResp (Err err) ->
            ( { model | loading = False, error = Just (Err err) }, Cmd.none )

        SelectType et ->
            let
                next =
                    { model | selected = Just et, form = Nothing, error = Nothing, entities = [] }
            in
            ( next, loadEntities flags next )

        EntitiesResp (Ok list) ->
            ( { model | entities = list.items }, Cmd.none )

        EntitiesResp (Err err) ->
            ( { model | error = Just (Err err) }, Cmd.none )

        SetQuery q ->
            let
                next =
                    { model | query = q }
            in
            ( next, loadEntities flags next )

        InitNew ->
            ( { model | form = Just emptyForm, error = Nothing }, Cmd.none )

        EditEntity e ->
            ( { model | form = Just (formOf e), error = Nothing }, Cmd.none )

        SetName s ->
            updateForm (\f -> { f | name = s }) model

        SetNotes s ->
            updateForm (\f -> { f | notes = s }) model

        SetSuggest b ->
            updateForm (\f -> { f | suggest = b }) model

        SetValue aid idx v ->
            updateForm
                (\f ->
                    { f
                        | values =
                            Dict.update aid
                                (\vs ->
                                    case vs of
                                        Just list ->
                                            if idx < List.length list then
                                                Just (setAt idx v list)

                                            else
                                                Just (list ++ [ v ])

                                        Nothing ->
                                            Just [ v ]
                                )
                                f.values
                    }
                )
                model

        AddValue aid ->
            updateForm
                (\f -> { f | values = Dict.update aid (\vs -> Just (Maybe.withDefault [] vs ++ [ "" ])) f.values })
                model

        RemoveValue aid idx ->
            updateForm
                (\f -> { f | values = Dict.update aid (Maybe.map (removeAt idx)) f.values })
                model

        Submit ->
            case ( model.selected, model.form ) of
                ( Just et, Just form ) ->
                    if String.trim form.name == "" then
                        ( model, Cmd.none )

                    else if form.id == "" then
                        ( { model | loading = True }
                        , Api.postEntity flags (toNewEntity et form) CreateResp
                        )

                    else
                        ( { model | loading = True }
                        , Api.putEntity flags form.id (toNewEntity et form) SaveResp
                        )

                _ ->
                    ( model, Cmd.none )

        CreateResp (Ok r) ->
            finish flags r.success r.message model

        CreateResp (Err err) ->
            ( { model | loading = False, error = Just (Err err) }, Cmd.none )

        SaveResp (Ok r) ->
            finish flags r.success r.message model

        SaveResp (Err err) ->
            ( { model | loading = False, error = Just (Err err) }, Cmd.none )

        RequestDelete ->
            let
                ( dm, _ ) =
                    Comp.YesNoDimmer.update Comp.YesNoDimmer.activate model.deleteDimmer
            in
            ( { model | deleteDimmer = dm }, Cmd.none )

        DeleteMsg lm ->
            let
                ( dm, flag ) =
                    Comp.YesNoDimmer.update lm model.deleteDimmer

                cmd =
                    case ( flag, model.form ) of
                        ( True, Just form ) ->
                            Api.deleteEntity flags form.id SaveResp

                        _ ->
                            Cmd.none
            in
            ( { model | deleteDimmer = dm }, cmd )

        Cancel ->
            ( { model | form = Nothing, error = Nothing }, loadEntities flags model )


finish : Flags -> Bool -> String -> Model -> ( Model, Cmd Msg )
finish flags success message model =
    if success then
        let
            next =
                { model | loading = False, form = Nothing, error = Nothing }
        in
        ( next, loadEntities flags next )

    else
        ( { model | loading = False, error = Just (Ok message) }, Cmd.none )


--- View


typeName : EntityType -> String
typeName et =
    Maybe.withDefault et.name et.label


view : Texts -> Model -> Html Msg
view texts model =
    div [ class "flex flex-col md:relative" ]
        [ div [ class "flex flex-wrap gap-2 mb-4" ]
            (List.map (viewTypeButton model) model.types)
        , case ( model.selected, model.form ) of
            ( Just et, Just form ) ->
                viewForm texts et form model

            ( Just et, Nothing ) ->
                viewTable texts et model

            ( Nothing, _ ) ->
                div [ class "opacity-75" ] [ text texts.noTypes ]
        , B.loadingDimmer { active = model.loading, label = texts.basics.loading }
        ]


viewTypeButton : Model -> EntityType -> Html Msg
viewTypeButton model et =
    let
        active =
            Maybe.map .id model.selected == Just et.id
    in
    button
        [ type_ "button"
        , onClick (SelectType et)
        , class
            (if active then
                S.primaryButton

             else
                S.secondaryBasicButton
            )
        ]
        [ text (typeName et) ]


viewError : Texts -> Model -> Html Msg
viewError texts model =
    case model.error of
        Just err ->
            div [ class S.errorMessage, class "my-2" ]
                [ case err of
                    Err e ->
                        text (texts.httpError e)

                    Ok m ->
                        text m
                ]

        Nothing ->
            text ""


viewTable : Texts -> EntityType -> Model -> Html Msg
viewTable texts et model =
    div []
        [ MB.view
            { start =
                [ MB.TextInput
                    { tagger = SetQuery
                    , value = model.query
                    , placeholder = texts.basics.searchPlaceholder
                    , icon = Just "fa fa-search"
                    }
                ]
            , end =
                [ MB.PrimaryButton
                    { tagger = InitNew
                    , title = texts.addEntity (typeName et)
                    , icon = Just "fa fa-plus"
                    , label = texts.newEntity
                    }
                ]
            , rootClasses = "mb-4"
            , sticky = True
            }
        , viewError texts model
        , table [ class S.tableMain ]
            [ thead []
                [ tr []
                    [ th [ class "w-px whitespace-nowrap" ] []
                    , th [ class "text-left" ] [ text texts.basics.name ]
                    , th [ class "text-left hidden sm:table-cell" ] [ text texts.attributes ]
                    , th [ class "text-center" ] [ text texts.suggest ]
                    ]
                ]
            , tbody [] (List.map (viewRow texts et) model.entities)
            ]
        ]


viewRow : Texts -> EntityType -> Entity -> Html Msg
viewRow texts et e =
    let
        attrName v =
            List.filter (\a -> a.id == v.attrId) et.attrs
                |> List.head
                |> Maybe.map (\a -> Maybe.withDefault a.name a.label)
                |> Maybe.withDefault (Maybe.withDefault "" v.name)

        summary =
            List.map (\v -> attrName v ++ ": " ++ v.value) e.attrs
                |> String.join ", "
    in
    tr [ class S.tableRow ]
        [ B.editLinkTableCell texts.basics.edit (EditEntity e)
        , td [ class "text-left py-4 md:py-2 pr-2" ] [ text e.name ]
        , td [ class "text-left py-4 md:py-2 pr-2 hidden sm:table-cell opacity-75 text-sm" ]
            [ text summary ]
        , td [ class "text-center py-4 md:py-2" ]
            [ if e.suggest then
                i [ class "fa fa-check" ] []

              else
                i [ class "fa fa-minus opacity-50" ] []
            ]
        ]


viewForm : Texts -> EntityType -> Form -> Model -> Html Msg
viewForm texts et form model =
    let
        dimmerSettings =
            Comp.YesNoDimmer.defaultSettings texts.reallyDelete texts.basics.yes texts.basics.no
    in
    div [ class "flex flex-col md:relative" ]
        [ MB.view
            { start =
                [ MB.PrimaryButton
                    { tagger = Submit
                    , title = texts.basics.submitThisForm
                    , icon = Just "fa fa-save"
                    , label = texts.basics.submit
                    }
                , MB.SecondaryButton
                    { tagger = Cancel
                    , title = texts.basics.backToList
                    , icon = Just "fa fa-arrow-left"
                    , label = texts.basics.cancel
                    }
                ]
            , end =
                if form.id /= "" then
                    [ MB.DeleteButton
                        { tagger = RequestDelete
                        , title = texts.reallyDelete
                        , icon = Just "fa fa-trash"
                        , label = texts.basics.delete
                        }
                    ]

                else
                    []
            , rootClasses = "mb-4"
            , sticky = True
            }
        , Html.map DeleteMsg (Comp.YesNoDimmer.viewN True dimmerSettings model.deleteDimmer)
        , h3 [ class S.header2 ]
            [ text
                (if form.id == "" then
                    texts.addEntity (typeName et)

                 else
                    form.name
                )
            ]
        , viewError texts model
        , div [ class "mb-4" ]
            [ label [ class S.inputLabel, for "entity-name" ]
                [ text texts.basics.name, B.inputRequired ]
            , input
                [ type_ "text"
                , id "entity-name"
                , onInput SetName
                , value form.name
                , class S.textInput
                , classList [ ( S.inputErrorBorder, String.trim form.name == "" ) ]
                ]
                []
            ]
        , div [] (List.map (viewAttr texts form) et.attrs)
        , div [ class "mb-4" ]
            [ label [ class S.inputLabel, for "entity-notes" ] [ text texts.notes ]
            , textarea
                [ id "entity-notes", onInput SetNotes, value form.notes, class S.textAreaInput ]
                []
            ]
        , div [ class "mb-4" ]
            [ MB.viewItem <|
                MB.Checkbox
                    { tagger = SetSuggest
                    , label = texts.suggest
                    , value = form.suggest
                    , id = "entity-suggest"
                    }
            , div [ class "opacity-75 text-sm" ] [ text texts.suggestInfo ]
            ]
        ]


viewAttr : Texts -> Form -> AttrDef -> Html Msg
viewAttr texts form attr =
    let
        values =
            Dict.get attr.id form.values |> Maybe.withDefault []

        shown =
            if List.isEmpty values then
                [ "" ]

            else
                values

        fieldLabel =
            Maybe.withDefault attr.name attr.label

        viewInput idx v =
            div [ class "flex flex-row items-center mb-1" ]
                [ valueInput attr idx v
                , if attr.multi && List.length shown > 1 then
                    a
                        [ href "#"
                        , class S.link
                        , class "ml-2"
                        , onClick (RemoveValue attr.id idx)
                        ]
                        [ i [ class "fa fa-times" ] [] ]

                  else
                    text ""
                ]
    in
    div [ class "mb-4" ]
        (label [ class S.inputLabel ] [ text fieldLabel ]
            :: List.indexedMap viewInput shown
            ++ (if attr.multi then
                    [ a [ href "#", class S.link, class "text-sm", onClick (AddValue attr.id) ]
                        [ i [ class "fa fa-plus mr-1" ] [], text texts.addValue ]
                    ]

                else
                    []
               )
        )


valueInput : AttrDef -> Int -> String -> Html Msg
valueInput attr idx v =
    case attr.ftype of
        "bool" ->
            input
                [ type_ "checkbox"
                , class S.checkboxInput
                , checked (v == "true")
                , onCheck
                    (\b ->
                        SetValue attr.id
                            idx
                            (if b then
                                "true"

                             else
                                "false"
                            )
                    )
                ]
                []

        "date" ->
            input [ type_ "date", value v, onInput (SetValue attr.id idx), class S.textInput ] []

        _ ->
            input [ type_ "text", value v, onInput (SetValue attr.id idx), class S.textInput ] []
