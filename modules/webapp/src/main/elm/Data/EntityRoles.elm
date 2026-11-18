{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Data.EntityRoles exposing (order, sortRoles)

{-| Built-in roles come first in a fixed order, other roles follow by name.
-}

import Api.Model.EntityRole exposing (EntityRole)


order : String -> ( Int, String )
order name =
    case name of
        "sender" ->
            ( 0, name )

        "recipient" ->
            ( 1, name )

        "concerning" ->
            ( 2, name )

        _ ->
            ( 3, String.toLower name )


sortRoles : List EntityRole -> List EntityRole
sortRoles =
    List.sortBy (.name >> order)
