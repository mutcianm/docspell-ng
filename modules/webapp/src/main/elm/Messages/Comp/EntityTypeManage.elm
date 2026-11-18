{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Messages.Comp.EntityTypeManage exposing
    ( Texts
    , de
    , fr
    , gb
    )

import Data.CustomFieldType exposing (CustomFieldType)
import Http
import Messages.Basics
import Messages.Comp.HttpError
import Messages.Data.CustomFieldType


type alias Texts =
    { basics : Messages.Basics.Texts
    , httpError : Http.Error -> String
    , fieldType : CustomFieldType -> String
    , matcher : String -> String
    , types : String
    , roles : String
    , newType : String
    , newAttr : String
    , newRole : String
    , builtin : String
    , attributes : String
    , accepts : String
    , acceptsInfo : String
    , anyType : String
    , label : String
    , nameInfo : String
    , attrNameInfo : String
    , format : String
    , recognize : String
    , recognizeInfo : String
    , multiValue : String
    , multiValueInfo : String
    , matcherLabel : String
    , matcherInfo : String
    , multiEntity : String
    , multiEntityInfo : String
    , learn : String
    , learnInfo : String
    , reallyDeleteType : String
    , reallyDeleteAttr : Int -> String
    , reallyDeleteRole : String
    }


gb : Texts
gb =
    { basics = Messages.Basics.gb
    , httpError = Messages.Comp.HttpError.gb
    , fieldType = Messages.Data.CustomFieldType.gb
    , matcher =
        \m ->
            case m of
                "alias" ->
                    "Alias"

                "email" ->
                    "E-Mail"

                "website" ->
                    "Website"

                _ ->
                    "None"
    , types = "Entity types"
    , roles = "Roles"
    , newType = "New type"
    , newAttr = "New attribute"
    , newRole = "New role"
    , builtin = "built-in"
    , attributes = "Attributes"
    , accepts = "Accepts"
    , acceptsInfo = "The types of entities this role can link. None selected means any type."
    , anyType = "any type"
    , label = "Label"
    , nameInfo = "Used in search queries, like sender:acme. No dots and no search keywords."
    , attrNameInfo = "Used in search queries, like vehicle.plate:AB*. No dots."
    , format = "Format"
    , recognize = "Recognize in documents"
    , recognizeInfo = "Look for entities of this type in the text of new documents."
    , multiValue = "Several values"
    , multiValueInfo = "An entity may have more than one value for this attribute."
    , matcherLabel = "Use for matching"
    , matcherInfo = "Values marked as alias, e-mail or website are searched for in documents and mails."
    , multiEntity = "Several entities"
    , multiEntityInfo = "An item may link more than one entity under this role."
    , learn = "Learn"
    , learnInfo = "Let the classifier learn to propose entities for this role."
    , reallyDeleteType = "Really delete this type? Only types without entities can be deleted."
    , reallyDeleteAttr = \n -> "Really delete this attribute? Its " ++ String.fromInt n ++ " values are removed."
    , reallyDeleteRole = "Really delete this role? Only roles not used by any item can be deleted."
    }


de : Texts
de =
    { basics = Messages.Basics.de
    , httpError = Messages.Comp.HttpError.de
    , fieldType = Messages.Data.CustomFieldType.de
    , matcher =
        \m ->
            case m of
                "alias" ->
                    "Alias"

                "email" ->
                    "E-Mail"

                "website" ->
                    "Webseite"

                _ ->
                    "Keine"
    , types = "Typen"
    , roles = "Rollen"
    , newType = "Neuer Typ"
    , newAttr = "Neues Attribut"
    , newRole = "Neue Rolle"
    , builtin = "eingebaut"
    , attributes = "Attribute"
    , accepts = "Erlaubt"
    , acceptsInfo = "Die Typen, die mit dieser Rolle verknüpft werden können. Keine Auswahl erlaubt alle Typen."
    , anyType = "alle Typen"
    , label = "Bezeichnung"
    , nameInfo = "Wird in Suchanfragen verwendet, z.B. sender:acme. Keine Punkte und keine Suchbegriffe."
    , attrNameInfo = "Wird in Suchanfragen verwendet, z.B. vehicle.plate:AB*. Keine Punkte."
    , format = "Format"
    , recognize = "In Dokumenten erkennen"
    , recognizeInfo = "Im Text neuer Dokumente nach Einträgen dieses Typs suchen."
    , multiValue = "Mehrere Werte"
    , multiValueInfo = "Ein Eintrag darf mehrere Werte für dieses Attribut haben."
    , matcherLabel = "Zum Abgleich verwenden"
    , matcherInfo = "Als Alias, E-Mail oder Webseite markierte Werte werden in Dokumenten und E-Mails gesucht."
    , multiEntity = "Mehrere Einträge"
    , multiEntityInfo = "Ein Dokument darf mehrere Einträge mit dieser Rolle verknüpfen."
    , learn = "Lernen"
    , learnInfo = "Den Klassifikator Einträge für diese Rolle vorschlagen lassen."
    , reallyDeleteType = "Diesen Typ wirklich löschen? Nur Typen ohne Einträge können gelöscht werden."
    , reallyDeleteAttr = \n -> "Dieses Attribut wirklich löschen? Seine " ++ String.fromInt n ++ " Werte werden entfernt."
    , reallyDeleteRole = "Diese Rolle wirklich löschen? Nur Rollen, die kein Dokument verwendet, können gelöscht werden."
    }


fr : Texts
fr =
    { basics = Messages.Basics.fr
    , httpError = Messages.Comp.HttpError.fr
    , fieldType = Messages.Data.CustomFieldType.fr
    , matcher =
        \m ->
            case m of
                "alias" ->
                    "Alias"

                "email" ->
                    "E-mail"

                "website" ->
                    "Site web"

                _ ->
                    "Aucun"
    , types = "Types"
    , roles = "Rôles"
    , newType = "Nouveau type"
    , newAttr = "Nouvel attribut"
    , newRole = "Nouveau rôle"
    , builtin = "intégré"
    , attributes = "Attributs"
    , accepts = "Accepte"
    , acceptsInfo = "Les types que ce rôle peut lier. Aucune sélection accepte tous les types."
    , anyType = "tous les types"
    , label = "Libellé"
    , nameInfo = "Utilisé dans les recherches, par ex. sender:acme. Sans points ni mots-clés de recherche."
    , attrNameInfo = "Utilisé dans les recherches, par ex. vehicle.plate:AB*. Sans points."
    , format = "Format"
    , recognize = "Reconnaître dans les documents"
    , recognizeInfo = "Chercher des entrées de ce type dans le texte des nouveaux documents."
    , multiValue = "Plusieurs valeurs"
    , multiValueInfo = "Une entrée peut avoir plusieurs valeurs pour cet attribut."
    , matcherLabel = "Utiliser pour la correspondance"
    , matcherInfo = "Les valeurs marquées alias, e-mail ou site web sont recherchées dans les documents et e-mails."
    , multiEntity = "Plusieurs entrées"
    , multiEntityInfo = "Un document peut lier plusieurs entrées avec ce rôle."
    , learn = "Apprendre"
    , learnInfo = "Laisser le classificateur proposer des entrées pour ce rôle."
    , reallyDeleteType = "Vraiment supprimer ce type ? Seuls les types sans entrées peuvent être supprimés."
    , reallyDeleteAttr = \n -> "Vraiment supprimer cet attribut ? Ses " ++ String.fromInt n ++ " valeurs seront supprimées."
    , reallyDeleteRole = "Vraiment supprimer ce rôle ? Seuls les rôles non utilisés peuvent être supprimés."
    }
