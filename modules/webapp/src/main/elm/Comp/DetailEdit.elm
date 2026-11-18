{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Comp.DetailEdit exposing
    ( Model
    , Msg
    , Value(..)
    , formHeading
    , initCustomField
    , initTag
    , initTagByName
    , update
    , view2
    , viewModal2
    )

{-| Module for allowing to edit metadata in the item-edit menu.

It is only possible to edit one thing at a time, suitable for being
rendered in a modal.

-}

import Api
import Api.Model.BasicResult exposing (BasicResult)
import Api.Model.NewCustomField exposing (NewCustomField)
import Api.Model.Tag exposing (Tag)
import Comp.Basic as B
import Comp.CustomFieldForm
import Comp.TagForm
import Data.Flags exposing (Flags)
import Data.Icons as Icons
import Data.UiSettings exposing (UiSettings)
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onClick)
import Http
import Messages.Comp.DetailEdit exposing (Texts)
import Styles as S


type alias Model =
    { form : FormModel
    , itemId : String
    , submitting : Bool
    , loading : Bool
    , formState : FormState
    }


type FormState
    = FormStateInitial
    | FormStateHttpError Http.Error
    | FormStateSubmitSuccessful
    | FormStateSubmitError String
    | FormStateMissingRequiredFields


isError : FormState -> Bool
isError state =
    case state of
        FormStateInitial ->
            False

        FormStateHttpError _ ->
            True

        FormStateSubmitSuccessful ->
            False

        FormStateSubmitError _ ->
            True

        FormStateMissingRequiredFields ->
            True


isSuccess : FormState -> Bool
isSuccess state =
    case state of
        FormStateInitial ->
            False

        _ ->
            not (isError state)


type FormModel
    = TM Comp.TagForm.Model
    | CFM Comp.CustomFieldForm.Model


fold :
    (Comp.TagForm.Model -> a)
    -> (Comp.CustomFieldForm.Model -> a)
    -> FormModel
    -> a
fold ft fcf model =
    case model of
        TM tm ->
            ft tm

        CFM fm ->
            fcf fm


init : String -> FormModel -> Model
init itemId fm =
    { form = fm
    , itemId = itemId
    , submitting = False
    , loading = False
    , formState = FormStateInitial
    }


initTag : String -> Comp.TagForm.Model -> Model
initTag itemId tm =
    init itemId (TM tm)


initTagByName : String -> String -> List String -> Model
initTagByName itemId name categories =
    let
        tm =
            Comp.TagForm.emptyModel categories

        tm_ =
            { tm | name = name }
    in
    initTag itemId tm_


initCustomField : String -> Model
initCustomField itemId =
    let
        cfm =
            Comp.CustomFieldForm.initEmpty
    in
    init itemId (CFM cfm)


type Msg
    = TagMsg Comp.TagForm.Msg
    | CustomFieldMsg Comp.CustomFieldForm.Msg
    | Submit
    | Cancel
    | SubmitResp (Result Http.Error BasicResult)


type Value
    = SubmitTag Tag
    | SubmitCustomField NewCustomField
    | CancelForm


makeValue : FormModel -> Value
makeValue fm =
    case fm of
        TM tm ->
            SubmitTag (Comp.TagForm.getTag tm)

        CFM fieldModel ->
            let
                cfield =
                    Comp.CustomFieldForm.makeField fieldModel
            in
            case cfield of
                Ok field ->
                    SubmitCustomField field

                Err _ ->
                    CancelForm



--- Update


update : Flags -> Msg -> Model -> ( Model, Cmd Msg, Maybe Value )
update flags msg model =
    case msg of
        Cancel ->
            ( model, Cmd.none, Just CancelForm )

        SubmitResp (Ok res) ->
            let
                ret =
                    if res.success then
                        Just (makeValue model.form)

                    else
                        Nothing
            in
            ( { model
                | formState =
                    if res.success then
                        FormStateSubmitSuccessful

                    else
                        FormStateSubmitError res.message
                , submitting = False
              }
            , Cmd.none
            , ret
            )

        SubmitResp (Err err) ->
            ( { model
                | formState = FormStateHttpError err
                , submitting = False
              }
            , Cmd.none
            , Nothing
            )

        Submit ->
            case model.form of
                TM tm ->
                    let
                        tag =
                            Comp.TagForm.getTag tm
                    in
                    if Comp.TagForm.isValid tm then
                        ( { model | submitting = True }
                        , Api.addTag flags model.itemId tag SubmitResp
                        , Nothing
                        )

                    else
                        ( { model | formState = FormStateMissingRequiredFields }
                        , Cmd.none
                        , Nothing
                        )

                CFM fm ->
                    let
                        cfield =
                            Comp.CustomFieldForm.makeField fm
                    in
                    case cfield of
                        Ok newField ->
                            ( { model | submitting = True }
                            , Api.postCustomField flags newField SubmitResp
                            , Nothing
                            )

                        Err _ ->
                            ( { model | formState = FormStateMissingRequiredFields }
                            , Cmd.none
                            , Nothing
                            )

        TagMsg lm ->
            case model.form of
                TM tm ->
                    let
                        ( tm_, tc_ ) =
                            Comp.TagForm.update flags lm tm
                    in
                    ( { model
                        | form = TM tm_
                        , formState = FormStateInitial
                      }
                    , Cmd.map TagMsg tc_
                    , Nothing
                    )

                _ ->
                    ( model, Cmd.none, Nothing )

        CustomFieldMsg lm ->
            case model.form of
                CFM fm ->
                    let
                        ( fm_, fc_, _ ) =
                            Comp.CustomFieldForm.update flags lm fm
                    in
                    ( { model
                        | form = CFM fm_
                        , formState = FormStateInitial
                      }
                    , Cmd.map CustomFieldMsg fc_
                    , Nothing
                    )

                _ ->
                    ( model, Cmd.none, Nothing )



--- View2


view2 : Texts -> List (Attribute Msg) -> UiSettings -> Model -> Html Msg
view2 texts attr settings model =
    div attr
        (viewIntern2 texts settings True model)


formHeading : Texts -> String -> Model -> Html msg
formHeading texts classes model =
    let
        heading =
            fold (\_ -> texts.addTagHeader)
                (\_ -> texts.addCustomFieldHeader)

        headIcon =
            fold (\_ -> Icons.tagIcon "mr-2")
                (\_ -> Icons.customFieldIcon2 "mr-2")
    in
    div [ class classes ]
        [ headIcon model.form
        , text (heading model.form)
        ]


viewModal2 : Texts -> UiSettings -> Maybe Model -> Html Msg
viewModal2 texts settings mm =
    let
        hidden =
            mm == Nothing

        heading =
            fold (\_ -> texts.addTagHeader)
                (\_ -> texts.addCustomFieldHeader)

        headIcon =
            fold (\_ -> Icons.tagIcon "mr-2")
                (\_ -> Icons.customFieldIcon2 "mr-2")
    in
    div
        [ classList
            [ ( S.dimmer, True )
            , ( " hidden", hidden )
            ]
        , class "flex"
        ]
        [ div
            [ class ""
            ]
            [ div [ class S.header2 ]
                [ Maybe.map .form mm
                    |> Maybe.map headIcon
                    |> Maybe.withDefault (i [] [])
                , Maybe.map .form mm
                    |> Maybe.map heading
                    |> Maybe.withDefault ""
                    |> text
                ]
            , div [ class "scrolling content" ]
                (case mm of
                    Just model ->
                        viewIntern2 texts settings False model

                    Nothing ->
                        []
                )
            , div [ class "flex flex-row space-x-2" ]
                (case mm of
                    Just model ->
                        viewButtons2 texts model

                    Nothing ->
                        []
                )
            ]
        ]


viewButtons2 : Texts -> Model -> List (Html Msg)
viewButtons2 texts model =
    [ B.primaryButton
        { label = texts.basics.submit
        , icon =
            if model.submitting || model.loading then
                "fa fa-circle-notch animate-spin"

            else
                "fa fa-save"
        , disabled = model.submitting || model.loading
        , handler = onClick Submit
        , attrs = [ href "#" ]
        }
    , B.secondaryButton
        { label = texts.basics.cancel
        , handler = onClick Cancel
        , disabled = False
        , icon = "fa fa-times"
        , attrs = [ href "#" ]
        }
    ]


viewIntern2 : Texts -> UiSettings -> Bool -> Model -> List (Html Msg)
viewIntern2 texts settings withButtons model =
    [ div
        [ classList
            [ ( S.errorMessage, isError model.formState )
            , ( S.successMessage, isSuccess model.formState )
            , ( "hidden", model.formState == FormStateInitial )
            ]
        ]
        [ case model.formState of
            FormStateInitial ->
                text ""

            FormStateHttpError err ->
                text (texts.httpError err)

            FormStateSubmitSuccessful ->
                text texts.submitSuccessful

            FormStateSubmitError m ->
                text m

            FormStateMissingRequiredFields ->
                text texts.missingRequiredFields
        ]
    , case model.form of
        TM tm ->
            Html.map TagMsg (Comp.TagForm.view2 texts.tagForm settings tm)

        CFM fm ->
            div []
                (List.map (Html.map CustomFieldMsg)
                    (Comp.CustomFieldForm.view2
                        texts.customFieldForm
                        { classes = ""
                        , showControls = False
                        }
                        fm
                    )
                )
    ]
        ++ (if withButtons then
                [ div [ class "flex flex-row space-x-2" ]
                    (viewButtons2 texts model)
                ]

            else
                []
           )
