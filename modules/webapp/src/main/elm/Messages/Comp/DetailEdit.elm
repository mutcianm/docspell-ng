{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Messages.Comp.DetailEdit exposing
    ( Texts
    , de
    , fr
    , gb
    )

import Http
import Messages.Basics
import Messages.Comp.CustomFieldForm
import Messages.Comp.HttpError
import Messages.Comp.TagForm


type alias Texts =
    { basics : Messages.Basics.Texts
    , tagForm : Messages.Comp.TagForm.Texts
    , customFieldForm : Messages.Comp.CustomFieldForm.Texts
    , httpError : Http.Error -> String
    , submitSuccessful : String
    , missingRequiredFields : String
    , addTagHeader : String
    , addCustomFieldHeader : String
    }


gb : Texts
gb =
    { basics = Messages.Basics.gb
    , tagForm = Messages.Comp.TagForm.gb
    , customFieldForm = Messages.Comp.CustomFieldForm.gb
    , httpError = Messages.Comp.HttpError.gb
    , submitSuccessful = "Successfully saved."
    , missingRequiredFields = "Please fill required fields."
    , addTagHeader = "Add Tag"
    , addCustomFieldHeader = "Add Custom Field"
    }


de : Texts
de =
    { basics = Messages.Basics.de
    , tagForm = Messages.Comp.TagForm.de
    , customFieldForm = Messages.Comp.CustomFieldForm.de
    , httpError = Messages.Comp.HttpError.de
    , submitSuccessful = "Erfolgreich gespeichert."
    , missingRequiredFields = "Bitte die erforderlichen Felder ausfüllen."
    , addTagHeader = "Tag anlegen"
    , addCustomFieldHeader = "Benutzerfeld anlegen"
    }


fr : Texts
fr =
    { basics = Messages.Basics.fr
    , tagForm = Messages.Comp.TagForm.fr
    , customFieldForm = Messages.Comp.CustomFieldForm.fr
    , httpError = Messages.Comp.HttpError.fr
    , submitSuccessful = "Enregistré"
    , missingRequiredFields = "Veuillez compléter les champs requis."
    , addTagHeader = "Ajouter un Tag"
    , addCustomFieldHeader = "Ajouter un champs personnalisé"
    }
