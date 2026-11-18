{-
   Copyright 2020 Eike K. & Contributors

   SPDX-License-Identifier: AGPL-3.0-or-later
-}


module Messages.Page.ManageData exposing
    ( Texts
    , de
    , fr
    , gb
    )

import Data.TimeZone exposing (TimeZone)
import Messages.Basics
import Messages.Comp.AddonArchiveManage
import Messages.Comp.AddonRunConfigManage
import Messages.Comp.BookmarkManage
import Messages.Comp.CustomFieldManage
import Messages.Comp.EntityManage
import Messages.Comp.EntityTypeManage
import Messages.Comp.FolderManage
import Messages.Comp.TagManage


type alias Texts =
    { basics : Messages.Basics.Texts
    , tagManage : Messages.Comp.TagManage.Texts
    , entityManage : Messages.Comp.EntityManage.Texts
    , entityTypeManage : Messages.Comp.EntityTypeManage.Texts
    , folderManage : Messages.Comp.FolderManage.Texts
    , customFieldManage : Messages.Comp.CustomFieldManage.Texts
    , bookmarkManage : Messages.Comp.BookmarkManage.Texts
    , addonArchiveManage : Messages.Comp.AddonArchiveManage.Texts
    , addonRunConfigManage : Messages.Comp.AddonRunConfigManage.Texts
    , manageData : String
    , bookmarks : String
    , addonArchives : String
    , addonRunConfigs : String
    , entities : String
    , entityTypes : String
    }


gb : TimeZone -> Texts
gb tz =
    { basics = Messages.Basics.gb
    , tagManage = Messages.Comp.TagManage.gb
    , entityManage = Messages.Comp.EntityManage.gb
    , entityTypeManage = Messages.Comp.EntityTypeManage.gb
    , folderManage = Messages.Comp.FolderManage.gb tz
    , customFieldManage = Messages.Comp.CustomFieldManage.gb tz
    , bookmarkManage = Messages.Comp.BookmarkManage.gb
    , addonArchiveManage = Messages.Comp.AddonArchiveManage.gb
    , addonRunConfigManage = Messages.Comp.AddonRunConfigManage.gb tz
    , manageData = "Manage Data"
    , bookmarks = "Bookmarks"
    , addonArchives = "Addons"
    , addonRunConfigs = "Addon Run Configurations"
    , entities = "Entities"
    , entityTypes = "Types & Roles"
    }


de : TimeZone -> Texts
de tz =
    { basics = Messages.Basics.de
    , tagManage = Messages.Comp.TagManage.de
    , entityManage = Messages.Comp.EntityManage.de
    , entityTypeManage = Messages.Comp.EntityTypeManage.de
    , folderManage = Messages.Comp.FolderManage.de tz
    , customFieldManage = Messages.Comp.CustomFieldManage.de tz
    , bookmarkManage = Messages.Comp.BookmarkManage.de
    , addonArchiveManage = Messages.Comp.AddonArchiveManage.de
    , addonRunConfigManage = Messages.Comp.AddonRunConfigManage.de tz
    , manageData = "Daten verwalten"
    , bookmarks = "Bookmarks"
    , addonArchives = "Addons"
    , addonRunConfigs = "Addon Run Configurations"
    , entities = "Einträge"
    , entityTypes = "Typen & Rollen"
    }


fr : TimeZone -> Texts
fr tz =
    { basics = Messages.Basics.fr
    , tagManage = Messages.Comp.TagManage.fr
    , entityManage = Messages.Comp.EntityManage.fr
    , entityTypeManage = Messages.Comp.EntityTypeManage.fr
    , folderManage = Messages.Comp.FolderManage.fr tz
    , customFieldManage = Messages.Comp.CustomFieldManage.fr tz
    , bookmarkManage = Messages.Comp.BookmarkManage.fr
    , addonArchiveManage = Messages.Comp.AddonArchiveManage.fr
    , addonRunConfigManage = Messages.Comp.AddonRunConfigManage.fr tz
    , manageData = "Gestion des métadonnées"
    , bookmarks = "Favoris"
    , addonArchives = "Addons"
    , addonRunConfigs = "Addon Run Configurations"
    , entities = "Entrées"
    , entityTypes = "Types et rôles"
    }
