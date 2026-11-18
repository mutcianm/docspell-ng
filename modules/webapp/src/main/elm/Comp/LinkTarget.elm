{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Comp.LinkTarget exposing
    ( LinkTarget(..)
    , makeRoleLinks
    , makeCustomFieldLink
    , makeCustomFieldLink2
    , makeFolderLink
    , makeSourceLink
    , makeTagIconLink
    , makeTagLink
    )

import Api.Model.IdName exposing (IdName)
import Api.Model.ItemFieldValue exposing (ItemFieldValue)
import Api.Model.ItemLink exposing (ItemLink)
import Api.Model.Tag exposing (Tag)
import Data.EntityRoles
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onClick)
import Util.CustomField


type LinkTarget
    = LinkEntity String IdName
    | LinkFolder IdName
    | LinkTag IdName
    | LinkCustomField ItemFieldValue
    | LinkSource String
    | LinkBookmark String
    | LinkRelatedItems (List String)
    | LinkNone


{-| One line per role with links, e.g. "From: Acme" or "Concerning: Jane, Van". The label
function names each role; entities link to a search for that role and entity.
-}
makeRoleLinks :
    (ItemLink -> String)
    -> { a | links : List ItemLink }
    -> List ( String, Bool )
    -> (LinkTarget -> msg)
    -> List ( String, List (Html msg) )
makeRoleLinks roleLabel item linkClasses tagger =
    let
        roles =
            List.foldl
                (\l acc ->
                    if List.member l.roleId acc then
                        acc

                    else
                        acc ++ [ l.roleId ]
                )
                []
                (List.sortBy (.roleName >> Data.EntityRoles.order) item.links)

        line roleId =
            let
                links =
                    List.filter (\l -> l.roleId == roleId) item.links

                name =
                    List.head links |> Maybe.map roleLabel |> Maybe.withDefault ""

                linkOf l =
                    makeLink linkClasses (LinkEntity roleId >> tagger) (IdName l.entityId l.entityName)
            in
            ( name, List.intersperse (text ", ") (List.map linkOf links) )
    in
    List.map line roles


makeFolderLink :
    { a | folder : Maybe IdName }
    -> List ( String, Bool )
    -> (LinkTarget -> msg)
    -> Html msg
makeFolderLink item linkClasses tagger =
    let
        makeFolder idname =
            makeLink linkClasses (LinkFolder >> tagger) idname
    in
    Maybe.map makeFolder item.folder
        |> Maybe.withDefault (text "-")


makeTagLink :
    IdName
    -> List ( String, Bool )
    -> (LinkTarget -> msg)
    -> Html msg
makeTagLink tagId classes tagger =
    makeIconLink (i [ class "fa fa-tag mr-2" ] []) classes (LinkTag >> tagger) tagId


makeTagIconLink :
    Tag
    -> Html msg
    -> List ( String, Bool )
    -> (LinkTarget -> msg)
    -> Html msg
makeTagIconLink tagId icon classes tagger =
    makeIconLink icon classes (LinkTag >> tagger) tagId


makeCustomFieldLink :
    ItemFieldValue
    -> List ( String, Bool )
    -> (LinkTarget -> msg)
    -> Html msg
makeCustomFieldLink cv classes tagger =
    Util.CustomField.renderValue2
        classes
        (tagger (LinkCustomField cv) |> Just)
        cv


makeCustomFieldLink2 :
    ItemFieldValue
    -> List ( String, Bool )
    -> (LinkTarget -> msg)
    -> Html msg
makeCustomFieldLink2 cv classes tagger =
    Util.CustomField.renderValue2
        classes
        (tagger (LinkCustomField cv) |> Just)
        cv


makeSourceLink :
    List ( String, Bool )
    -> (LinkTarget -> msg)
    -> String
    -> Html msg
makeSourceLink classes tagger str =
    makeLink classes (.name >> LinkSource >> tagger) (IdName "" str)



--- Helpers


makeLink : List ( String, Bool ) -> (IdName -> msg) -> IdName -> Html msg
makeLink classes tagger idname =
    a
        [ onClick (tagger idname)
        , href "#"
        , classList classes
        ]
        [ text idname.name
        ]


makeIconLink :
    Html msg
    -> List ( String, Bool )
    -> (IdName -> msg)
    -> { x | id : String, name : String }
    -> Html msg
makeIconLink icon classes tagger tag =
    a
        [ onClick (tagger (IdName tag.id tag.name))
        , href "#"
        , classList classes
        ]
        [ icon
        , span []
            [ text tag.name
            ]
        ]
