{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Messages.Comp.ItemLinksEdit exposing
    ( Texts
    , de
    , fr
    , gb
    )


type alias Texts =
    { choose : String
    , anyType : String
    , suggestions : String
    , addRole : String
    , saveFailed : String
    }


gb : Texts
gb =
    { choose = "Choose…"
    , anyType = "any type"
    , suggestions = "Suggestions:"
    , addRole = "Add role"
    , saveFailed = "Saving the change failed."
    }


de : Texts
de =
    { choose = "Auswählen…"
    , anyType = "alle Typen"
    , suggestions = "Vorschläge:"
    , addRole = "Rolle hinzufügen"
    , saveFailed = "Die Änderung konnte nicht gespeichert werden."
    }


fr : Texts
fr =
    { choose = "Choisir…"
    , anyType = "tous les types"
    , suggestions = "Suggestions :"
    , addRole = "Ajouter un rôle"
    , saveFailed = "L'enregistrement a échoué."
    }
