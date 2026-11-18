{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Messages.Comp.EntityManage exposing
    ( Texts
    , de
    , fr
    , gb
    )

import Http
import Messages.Basics
import Messages.Comp.HttpError


type alias Texts =
    { basics : Messages.Basics.Texts
    , httpError : Http.Error -> String
    , noTypes : String
    , addEntity : String -> String
    , newEntity : String
    , attributes : String
    , suggest : String
    , suggestInfo : String
    , notes : String
    , reallyDelete : String
    , addValue : String
    }


gb : Texts
gb =
    { basics = Messages.Basics.gb
    , httpError = Messages.Comp.HttpError.gb
    , noTypes = "There are no entity types yet."
    , addEntity = \t -> "Add a new " ++ t
    , newEntity = "New"
    , attributes = "Attributes"
    , suggest = "Suggest"
    , suggestInfo = "Offer this entry in proposals and when choosing entries for an item."
    , notes = "Notes"
    , reallyDelete = "Really delete this entry? It is removed from all items."
    , addValue = "Add value"
    }


de : Texts
de =
    { basics = Messages.Basics.de
    , httpError = Messages.Comp.HttpError.de
    , noTypes = "Es gibt noch keine Typen."
    , addEntity = \t -> "Neu anlegen: " ++ t
    , newEntity = "Neu"
    , attributes = "Attribute"
    , suggest = "Vorschlagen"
    , suggestInfo = "Diesen Eintrag in Vorschlägen und bei der Auswahl für ein Dokument anbieten."
    , notes = "Notizen"
    , reallyDelete = "Diesen Eintrag wirklich löschen? Er wird von allen Dokumenten entfernt."
    , addValue = "Wert hinzufügen"
    }


fr : Texts
fr =
    { basics = Messages.Basics.fr
    , httpError = Messages.Comp.HttpError.fr
    , noTypes = "Il n'y a pas encore de types."
    , addEntity = \t -> "Ajouter : " ++ t
    , newEntity = "Nouveau"
    , attributes = "Attributs"
    , suggest = "Proposer"
    , suggestInfo = "Proposer cette entrée dans les suggestions et lors du choix pour un document."
    , notes = "Notes"
    , reallyDelete = "Vraiment supprimer cette entrée ? Elle sera retirée de tous les documents."
    , addValue = "Ajouter une valeur"
    }
