+++
title = "Entities and Roles"
weight = 12
[extra]
mktoc = true
+++

# Entities and Roles

Persons, organizations and the things your documents are about (a
car, an apartment, an insurance policy) are *entities*. Every entity
has an *entity type*, which defines the attributes it can have. Items
link entities through *roles*: an invoice from Acme links the
organization *Acme* under the role *sender*, and the car it is about
under the role *concerning*.

Both are managed on the *Manage Data* page, reachable from the top
right menu or the dashboard:

- *Types & Roles* defines entity types, their attributes and the roles
- *Entities* holds the entities themselves


# Entity Types

Each collective starts with these types:

- *person* and *organization*: built-in types. They cannot be renamed
  or deleted, but you can add attributes to them. They come with
  address and contact attributes (street, zip code, city, country,
  phone, mobile, fax, e-mail, website); organizations also have a
  *short name*.
- *equipment*: a regular type without attributes. You can rename it,
  add attributes to it or delete it.

Click *New type* to add your own, for example *vehicle* or
*apartment*. A type has:

- *Name*: the identifier used in [search
  queries](@/docs/query/_index.md#roles-and-entities), like
  `vehicle.plate:AB*`. It cannot contain dots and cannot be a keyword
  of the query language (like `name` or `tag`).
- *Label*: optional text shown in the app instead of the name. It can
  contain spaces and any characters; leave it empty to show the name.
- *Recognize in documents*: look for entities of this type in the text
  of new documents. Currently, document processing only recognizes
  persons, organizations and equipment; for other types this flag has
  no effect yet.

A type can only be deleted when it has no entities.


## Attributes

Attributes are the fields of the entities of a type. Each attribute
has a name (used in search queries, no dots), an optional label and a
*format*. The formats are the same as for [custom
fields](@/docs/webapp/customfields.md): text, numeric, date, bool and
money. The format of an attribute that has values cannot be changed.

*Several values* allows an entity to have more than one value for the
attribute, for example several phone numbers.

*Use for matching* marks values that are used to recognize entities
in documents and mails:

- *None*: the value is not used for matching (the default).
- *Alias*: another name of the entity. A term found in a document is
  compared with entity names and aliases. Use it for short names,
  abbreviations or former names, like *ACME* for *Acme Corporation*.
- *E-Mail*: e-mail addresses found in documents and mails are
  compared with these values. They also show up as
  completion proposals when [sending
  mails](@/docs/webapp/mailitem.md).
- *Website*: websites found in documents are compared with these
  values.

Deleting an attribute removes its values from all entities.


# Roles

A role says how an entity relates to an item. The built-in roles are:

- *sender* and *recipient*: the parties of the document. They accept
  persons and organizations.
- *concerning*: what the content of the document refers to, for
  example a car or an apartment. It accepts entities of any type.

Built-in roles cannot be renamed or deleted. Click *New role* to add
your own, for example *insurer* or *tenant*. A role has:

- *Name* and *Label*: as for types. The name is used as the search
  field, like `insurer:acme*`.
- *Accepts*: the entity types this role can link. If none is
  selected, the role accepts any type.
- *Several entities*: whether an item may link more than one entity
  under this role.
- *Learn*: let the classifier learn to propose entities for this
  role. For added roles this has no effect yet.

A role can only be deleted when no item uses it.


# Entities

The *Entities* page shows a switcher for the entity types, the list
of entities of the selected type and a form to create or edit one.
The form shows the attributes of the type; attributes with several
values have an *Add value* button.

The *Suggest* flag decides whether an entity is offered: when it is
off, the entity is neither proposed by document processing nor
offered in the pickers when editing items. Use it for entities you
want to keep for existing items but no longer use.

Deleting an entity removes it from all items.


# Linking Entities to Items

In the item detail, the section *Linked entities* has one picker per
role. Only entities of the types the role accepts are offered. Roles
that have neither links nor suggestions are hidden; click *Add role*
to show one of them.

When processing a new document, Docspell proposes entities (see [text
analysis](@/docs/joex/file-processing.md#text-analysis)). An
organization or person found as the other party of the document is
proposed under *sender* for incoming and under *recipient* for
outgoing items. A person found as the one the document is about is
proposed for the collective's own side: *recipient* for incoming and
*sender* for outgoing items. Equipment is proposed under
*concerning*. Docspell links the most likely candidate and shows the
others as suggestions below the picker, where you can choose one with
a click.

Item cards and the item header show one line per role with linked
entities: *From …* for sender, *To …* for recipient, *Concerning …*
for concerning, and the role's label (or name) for other roles.

Entities can also be linked to several items at once with
[multi-edit](@/docs/webapp/multiedit.md), and the [search
menu](@/docs/webapp/finding.md#linked-entities) allows to find items
by their linked entities.


# Upgrading from Upstream Docspell

When upgrading a database from upstream Docspell, persons,
organizations, equipment and their contacts are moved into entities:

- The correspondent organization and person become *sender* on
  incoming and *recipient* on outgoing items.
- The concerning person is the collective's own side: it becomes
  *recipient* on incoming and *sender* on outgoing items.
- Concerning equipment stays under *concerning*.

A person's affiliation to an organization is not kept. The former
*use* settings of persons, organizations and equipment are replaced by
the single *Suggest* flag of each entity; it is off for entries whose
use was *disabled*. The old tables are dropped, so this migration
cannot be undone. Back up the database before upgrading.
