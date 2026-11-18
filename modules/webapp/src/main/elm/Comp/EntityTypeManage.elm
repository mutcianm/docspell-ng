{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Comp.EntityTypeManage exposing
    ( Model
    , Msg
    , init
    , update
    , view
    )

import Api
import Api.Model.AttrDef exposing (AttrDef)
import Api.Model.BasicResult exposing (BasicResult)
import Api.Model.EntityRole exposing (EntityRole)
import Api.Model.EntityRoleList exposing (EntityRoleList)
import Api.Model.EntityType exposing (EntityType)
import Api.Model.EntityTypeList exposing (EntityTypeList)
import Api.Model.IdResult exposing (IdResult)
import Comp.Basic as B
import Comp.FixedDropdown
import Comp.MenuBar as MB
import Comp.YesNoDimmer
import Data.CustomFieldType exposing (CustomFieldType)
import Data.DropdownStyle as DS
import Data.Flags exposing (Flags)
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onClick, onInput)
import Http
import Messages.Comp.EntityTypeManage exposing (Texts)
import Styles as S


{-| Manages entity types with their attribute definitions, and the roles that link
items to entities.
-}
type alias Model =
    { types : List EntityType
    , roles : List EntityRole
    , editing : Editing
    , error : Maybe (Result Http.Error String)
    , loading : Bool
    , deleteDimmer : Comp.YesNoDimmer.Model
    }


type Editing
    = NotEditing
    | EditType TypeForm
    | EditAttr EntityType AttrForm
    | EditRole RoleForm


type alias TypeForm =
    { id : String
    , builtin : Bool
    , name : String
    , label : String
    , recognize : Bool
    }


type alias AttrForm =
    { id : String
    , name : String
    , label : String
    , ftype : CustomFieldType
    , ftypeModel : Comp.FixedDropdown.Model CustomFieldType
    , multi : Bool
    , matcher : String
    , matcherModel : Comp.FixedDropdown.Model String
    , usages : Int
    }


type alias RoleForm =
    { id : String
    , builtin : Bool
    , name : String
    , label : String
    , multi : Bool
    , learn : Bool
    , targets : List String
    }


type Msg
    = TypesResp (Result Http.Error EntityTypeList)
    | RolesResp (Result Http.Error EntityRoleList)
    | NewType
    | EditTypeMsg EntityType
    | NewAttr EntityType
    | EditAttrMsg EntityType AttrDef
    | NewRole
    | EditRoleMsg EntityRole
    | SetName String
    | SetLabel String
    | SetFlag Flag Bool
    | FTypeMsg (Comp.FixedDropdown.Msg CustomFieldType)
    | MatcherMsg (Comp.FixedDropdown.Msg String)
    | ToggleTarget String Bool
    | Submit
    | IdResp (Result Http.Error IdResult)
    | BasicResp (Result Http.Error BasicResult)
    | RequestDelete
    | DeleteMsg Comp.YesNoDimmer.Msg
    | Cancel


type Flag
    = Recognize
    | Multi
    | Learn


matchers : List String
matchers =
    [ "none", "alias", "email", "website" ]


init : Flags -> ( Model, Cmd Msg )
init flags =
    ( { types = []
      , roles = []
      , editing = NotEditing
      , error = Nothing
      , loading = True
      , deleteDimmer = Comp.YesNoDimmer.emptyModel
      }
    , load flags
    )


load : Flags -> Cmd Msg
load flags =
    Cmd.batch [ Api.getEntityTypes flags TypesResp, Api.getEntityRoles flags RolesResp ]


typeForm : EntityType -> TypeForm
typeForm t =
    { id = t.id
    , builtin = t.builtin
    , name = t.name
    , label = Maybe.withDefault "" t.label
    , recognize = t.recognize
    }


attrForm : AttrDef -> AttrForm
attrForm a =
    { id = a.id
    , name = a.name
    , label = Maybe.withDefault "" a.label
    , ftype = Data.CustomFieldType.fromString a.ftype |> Maybe.withDefault Data.CustomFieldType.Text
    , ftypeModel = Comp.FixedDropdown.init Data.CustomFieldType.all
    , multi = a.multi
    , matcher = a.matcher
    , matcherModel = Comp.FixedDropdown.init matchers
    , usages = a.usages
    }


roleForm : EntityRole -> RoleForm
roleForm r =
    { id = r.id
    , builtin = r.builtin
    , name = r.name
    , label = Maybe.withDefault "" r.label
    , multi = r.multi
    , learn = r.learn
    , targets = r.targets
    }


maybeString : String -> Maybe String
maybeString s =
    if String.trim s == "" then
        Nothing

    else
        Just (String.trim s)


update : Flags -> Msg -> Model -> ( Model, Cmd Msg )
update flags msg model =
    case msg of
        TypesResp (Ok list) ->
            ( { model | types = list.items, loading = False }, Cmd.none )

        TypesResp (Err err) ->
            ( { model | loading = False, error = Just (Err err) }, Cmd.none )

        RolesResp (Ok list) ->
            ( { model | roles = list.items }, Cmd.none )

        RolesResp (Err err) ->
            ( { model | error = Just (Err err) }, Cmd.none )

        NewType ->
            edit (EditType { id = "", builtin = False, name = "", label = "", recognize = True }) model

        EditTypeMsg t ->
            edit (EditType (typeForm t)) model

        NewAttr t ->
            edit (EditAttr t (attrForm (AttrDef "" "" Nothing "text" False "none" 0 0))) model

        EditAttrMsg t a ->
            edit (EditAttr t (attrForm a)) model

        NewRole ->
            edit
                (EditRole { id = "", builtin = False, name = "", label = "", multi = True, learn = False, targets = [] })
                model

        EditRoleMsg r ->
            edit (EditRole (roleForm r)) model

        SetName s ->
            mapEditing
                (\e ->
                    case e of
                        EditType f ->
                            EditType { f | name = s }

                        EditAttr t f ->
                            EditAttr t { f | name = s }

                        EditRole f ->
                            EditRole { f | name = s }

                        NotEditing ->
                            e
                )
                model

        SetLabel s ->
            mapEditing
                (\e ->
                    case e of
                        EditType f ->
                            EditType { f | label = s }

                        EditAttr t f ->
                            EditAttr t { f | label = s }

                        EditRole f ->
                            EditRole { f | label = s }

                        NotEditing ->
                            e
                )
                model

        SetFlag flag b ->
            mapEditing
                (\e ->
                    case ( e, flag ) of
                        ( EditType f, Recognize ) ->
                            EditType { f | recognize = b }

                        ( EditAttr t f, Multi ) ->
                            EditAttr t { f | multi = b }

                        ( EditRole f, Multi ) ->
                            EditRole { f | multi = b }

                        ( EditRole f, Learn ) ->
                            EditRole { f | learn = b }

                        _ ->
                            e
                )
                model

        FTypeMsg lm ->
            mapEditing
                (\e ->
                    case e of
                        EditAttr t f ->
                            let
                                ( dm, sel ) =
                                    Comp.FixedDropdown.update lm f.ftypeModel
                            in
                            EditAttr t { f | ftypeModel = dm, ftype = Maybe.withDefault f.ftype sel }

                        _ ->
                            e
                )
                model

        MatcherMsg lm ->
            mapEditing
                (\e ->
                    case e of
                        EditAttr t f ->
                            let
                                ( dm, sel ) =
                                    Comp.FixedDropdown.update lm f.matcherModel
                            in
                            EditAttr t { f | matcherModel = dm, matcher = Maybe.withDefault f.matcher sel }

                        _ ->
                            e
                )
                model

        ToggleTarget typeId b ->
            mapEditing
                (\e ->
                    case e of
                        EditRole f ->
                            EditRole
                                { f
                                    | targets =
                                        if b then
                                            typeId :: List.filter ((/=) typeId) f.targets

                                        else
                                            List.filter ((/=) typeId) f.targets
                                }

                        _ ->
                            e
                )
                model

        Submit ->
            ( { model | loading = True, error = Nothing }, submit flags model.editing )

        IdResp (Ok r) ->
            finish flags r.success r.message model

        IdResp (Err err) ->
            ( { model | loading = False, error = Just (Err err) }, Cmd.none )

        BasicResp (Ok r) ->
            finish flags r.success r.message model

        BasicResp (Err err) ->
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
                    if flag then
                        case model.editing of
                            EditType f ->
                                Api.deleteEntityType flags f.id BasicResp

                            EditAttr t f ->
                                Api.deleteAttrDef flags t.id f.id BasicResp

                            EditRole f ->
                                Api.deleteEntityRole flags f.id BasicResp

                            NotEditing ->
                                Cmd.none

                    else
                        Cmd.none
            in
            ( { model | deleteDimmer = dm }, cmd )

        Cancel ->
            ( { model | editing = NotEditing, error = Nothing }, Cmd.none )


edit : Editing -> Model -> ( Model, Cmd Msg )
edit e model =
    ( { model | editing = e, error = Nothing }, Cmd.none )


mapEditing : (Editing -> Editing) -> Model -> ( Model, Cmd Msg )
mapEditing f model =
    ( { model | editing = f model.editing }, Cmd.none )


submit : Flags -> Editing -> Cmd Msg
submit flags editing =
    case editing of
        EditType f ->
            let
                data =
                    { name = String.trim f.name, label = maybeString f.label, recognize = f.recognize }
            in
            if f.id == "" then
                Api.postEntityType flags data IdResp

            else
                Api.putEntityType flags f.id data BasicResp

        EditAttr t f ->
            let
                data =
                    { name = String.trim f.name
                    , label = maybeString f.label
                    , ftype = Data.CustomFieldType.asString f.ftype
                    , multi = f.multi
                    , matcher = f.matcher
                    }
            in
            if f.id == "" then
                Api.postAttrDef flags t.id data IdResp

            else
                Api.putAttrDef flags t.id f.id data BasicResp

        EditRole f ->
            let
                data =
                    { name = String.trim f.name
                    , label = maybeString f.label
                    , multi = f.multi
                    , learn = f.learn
                    , targets = f.targets
                    }
            in
            if f.id == "" then
                Api.postEntityRole flags data IdResp

            else
                Api.putEntityRole flags f.id data BasicResp

        NotEditing ->
            Cmd.none


finish : Flags -> Bool -> String -> Model -> ( Model, Cmd Msg )
finish flags success message model =
    if success then
        ( { model | loading = False, editing = NotEditing, error = Nothing }, load flags )

    else
        ( { model | loading = False, error = Just (Ok message) }, Cmd.none )



--- View


displayName : { a | name : String, label : Maybe String } -> String
displayName r =
    Maybe.withDefault r.name r.label


view : Texts -> Model -> Html Msg
view texts model =
    div [ class "flex flex-col md:relative" ]
        [ case model.editing of
            NotEditing ->
                viewLists texts model

            EditType f ->
                viewTypeForm texts model f

            EditAttr t f ->
                viewAttrForm texts model t f

            EditRole f ->
                viewRoleForm texts model f
        , B.loadingDimmer { active = model.loading, label = texts.basics.loading }
        ]


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


builtinBadge : Texts -> Bool -> Html msg
builtinBadge texts builtin =
    if builtin then
        span [ class "ml-2 text-xs opacity-60" ] [ text texts.builtin ]

    else
        text ""


viewLists : Texts -> Model -> Html Msg
viewLists texts model =
    div []
        [ viewError texts model
        , MB.view
            { start = [ MB.CustomElement (h3 [ class S.header3 ] [ text texts.types ]) ]
            , end =
                [ MB.PrimaryButton
                    { tagger = NewType
                    , title = texts.newType
                    , icon = Just "fa fa-plus"
                    , label = texts.newType
                    }
                ]
            , rootClasses = "mb-2"
            , sticky = False
            }
        , table [ class S.tableMain, class "mb-8" ]
            [ thead []
                [ tr []
                    [ th [ class "w-px whitespace-nowrap" ] []
                    , th [ class "text-left" ] [ text texts.basics.name ]
                    , th [ class "text-left" ] [ text texts.attributes ]
                    ]
                ]
            , tbody [] (List.map (viewTypeRow texts) model.types)
            ]
        , MB.view
            { start = [ MB.CustomElement (h3 [ class S.header3 ] [ text texts.roles ]) ]
            , end =
                [ MB.PrimaryButton
                    { tagger = NewRole
                    , title = texts.newRole
                    , icon = Just "fa fa-plus"
                    , label = texts.newRole
                    }
                ]
            , rootClasses = "mb-2"
            , sticky = False
            }
        , table [ class S.tableMain ]
            [ thead []
                [ tr []
                    [ th [ class "w-px whitespace-nowrap" ] []
                    , th [ class "text-left" ] [ text texts.basics.name ]
                    , th [ class "text-left" ] [ text texts.accepts ]
                    ]
                ]
            , tbody [] (List.map (viewRoleRow texts model) model.roles)
            ]
        ]


viewTypeRow : Texts -> EntityType -> Html Msg
viewTypeRow texts t =
    tr [ class S.tableRow ]
        [ B.editLinkTableCell texts.basics.edit (EditTypeMsg t)
        , td [ class "text-left py-4 md:py-2 pr-2" ]
            [ text (displayName t), builtinBadge texts t.builtin ]
        , td [ class "text-left py-4 md:py-2 pr-2 text-sm" ]
            (List.map
                (\attr ->
                    a [ href "#", class S.link, class "mr-3", onClick (EditAttrMsg t attr) ]
                        [ text (displayName attr) ]
                )
                t.attrs
                ++ [ a [ href "#", class S.link, onClick (NewAttr t), title texts.newAttr ]
                        [ i [ class "fa fa-plus" ] [] ]
                   ]
            )
        ]


viewRoleRow : Texts -> Model -> EntityRole -> Html Msg
viewRoleRow texts model r =
    let
        targetNames =
            if List.isEmpty r.targets then
                texts.anyType

            else
                List.filter (\t -> List.member t.id r.targets) model.types
                    |> List.map displayName
                    |> String.join ", "
    in
    tr [ class S.tableRow ]
        [ B.editLinkTableCell texts.basics.edit (EditRoleMsg r)
        , td [ class "text-left py-4 md:py-2 pr-2" ]
            [ text (displayName r), builtinBadge texts r.builtin ]
        , td [ class "text-left py-4 md:py-2 pr-2 text-sm" ] [ text targetNames ]
        ]


formButtons : Texts -> Bool -> Html Msg
formButtons texts deletable =
    MB.view
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
            if deletable then
                [ MB.DeleteButton
                    { tagger = RequestDelete
                    , title = texts.basics.delete
                    , icon = Just "fa fa-trash"
                    , label = texts.basics.delete
                    }
                ]

            else
                []
        , rootClasses = "mb-4"
        , sticky = True
        }


deleteDimmer : Texts -> String -> Model -> Html Msg
deleteDimmer texts question model =
    Html.map DeleteMsg
        (Comp.YesNoDimmer.viewN True
            (Comp.YesNoDimmer.defaultSettings question texts.basics.yes texts.basics.no)
            model.deleteDimmer
        )


nameInput : Texts -> Bool -> String -> String -> Html Msg
nameInput texts disabled_ info v =
    div [ class "mb-4" ]
        [ label [ class S.inputLabel, for "et-name" ] [ text texts.basics.name, B.inputRequired ]
        , input
            [ type_ "text"
            , id "et-name"
            , onInput SetName
            , value v
            , disabled disabled_
            , class S.textInput
            , classList [ ( S.inputErrorBorder, String.trim v == "" ) ]
            ]
            []
        , div [ class "opacity-75 text-sm" ] [ text info ]
        ]


labelInput : Texts -> String -> Html Msg
labelInput texts v =
    div [ class "mb-4" ]
        [ label [ class S.inputLabel, for "et-label" ] [ text texts.label ]
        , input [ type_ "text", id "et-label", onInput SetLabel, value v, class S.textInput ] []
        ]


checkbox : String -> String -> String -> Bool -> Flag -> Html Msg
checkbox idStr lbl info v flag =
    div [ class "mb-4" ]
        [ MB.viewItem <|
            MB.Checkbox { tagger = SetFlag flag, label = lbl, value = v, id = idStr }
        , div [ class "opacity-75 text-sm" ] [ text info ]
        ]


viewTypeForm : Texts -> Model -> TypeForm -> Html Msg
viewTypeForm texts model f =
    div []
        [ formButtons texts (f.id /= "" && not f.builtin)
        , deleteDimmer texts texts.reallyDeleteType model
        , h3 [ class S.header2 ]
            [ text
                (if f.id == "" then
                    texts.newType

                 else
                    f.name
                )
            ]
        , viewError texts model
        , nameInput texts f.builtin texts.nameInfo f.name
        , labelInput texts f.label
        , checkbox "et-recognize" texts.recognize texts.recognizeInfo f.recognize Recognize
        ]


viewAttrForm : Texts -> Model -> EntityType -> AttrForm -> Html Msg
viewAttrForm texts model t f =
    let
        ftypeCfg =
            { display = texts.fieldType
            , icon = \_ -> Nothing
            , style = DS.mainStyle
            , selectPlaceholder = texts.basics.selectPlaceholder
            }

        matcherCfg =
            { display = texts.matcher
            , icon = \_ -> Nothing
            , style = DS.mainStyle
            , selectPlaceholder = texts.basics.selectPlaceholder
            }
    in
    div []
        [ formButtons texts (f.id /= "")
        , deleteDimmer texts (texts.reallyDeleteAttr f.usages) model
        , h3 [ class S.header2 ]
            [ text (displayName t ++ ": ")
            , text
                (if f.id == "" then
                    texts.newAttr

                 else
                    f.name
                )
            ]
        , viewError texts model
        , nameInput texts False texts.attrNameInfo f.name
        , labelInput texts f.label
        , div [ class "mb-4" ]
            [ label [ class S.inputLabel ] [ text texts.format ]
            , Html.map FTypeMsg (Comp.FixedDropdown.viewStyled2 ftypeCfg False (Just f.ftype) f.ftypeModel)
            ]
        , checkbox "attr-multi" texts.multiValue texts.multiValueInfo f.multi Multi
        , div [ class "mb-4" ]
            [ label [ class S.inputLabel ] [ text texts.matcherLabel ]
            , Html.map MatcherMsg (Comp.FixedDropdown.viewStyled2 matcherCfg False (Just f.matcher) f.matcherModel)
            , div [ class "opacity-75 text-sm" ] [ text texts.matcherInfo ]
            ]
        ]


viewRoleForm : Texts -> Model -> RoleForm -> Html Msg
viewRoleForm texts model f =
    div []
        [ formButtons texts (f.id /= "" && not f.builtin)
        , deleteDimmer texts texts.reallyDeleteRole model
        , h3 [ class S.header2 ]
            [ text
                (if f.id == "" then
                    texts.newRole

                 else
                    f.name
                )
            ]
        , viewError texts model
        , nameInput texts f.builtin texts.nameInfo f.name
        , labelInput texts f.label
        , checkbox "role-multi" texts.multiEntity texts.multiEntityInfo f.multi Multi
        , checkbox "role-learn" texts.learn texts.learnInfo f.learn Learn
        , div [ class "mb-4" ]
            (label [ class S.inputLabel ] [ text texts.accepts ]
                :: List.map
                    (\t ->
                        MB.viewItem <|
                            MB.Checkbox
                                { tagger = ToggleTarget t.id
                                , label = displayName t
                                , value = List.member t.id f.targets
                                , id = "role-target-" ++ t.id
                                }
                    )
                    model.types
                ++ [ div [ class "opacity-75 text-sm" ] [ text texts.acceptsInfo ] ]
            )
        ]
