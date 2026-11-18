<img align="right" src="./artwork/logo-only.svg" height="150px" style="padding-left: 20px"/>

[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=for-the-badge&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![License](https://img.shields.io/github/license/eikek/docspell.svg?style=for-the-badge&color=steelblue)](https://github.com/eikek/docspell/blob/master/LICENSE.txt)
[![Docker Pulls](https://img.shields.io/docker/pulls/docspell/restserver?color=steelblue&style=for-the-badge&logo=docker)](https://hub.docker.com/u/docspell)
[![Gitter chat](https://img.shields.io/gitter/room/eikek/docspell?style=for-the-badge&color=steelblue&logo=gitter)](https://gitter.im/eikek/docspell)

# Docspell - Document Management System (DMS)

Docspell is a personal document organizer. Or sometimes called a
"Document Management System" (DMS). You'll need a scanner to convert
your papers into files. Docspell can then assist in organizing the
resulting mess :wink:. It can unify your files from scanners, emails
and other sources. It is targeted for home use, i.e. families,
households and also for smaller groups/companies.

You can associate tags, set correspondends and lots of other
predefined and custom metadata. If your documents are associated with
such meta data, you can quickly find them later using the search
feature. But adding this manually is a tedious task. Docspell can help
by suggesting correspondents, guessing tags or finding dates using
machine learning. It can learn metadata from existing documents and
find things using NLP. This makes adding metadata to your documents a
lot easier. For machine learning, it relies on the free (GPL)
[Stanford Core NLP library](https://github.com/stanfordnlp/CoreNLP).

Docspell also runs OCR (if needed) on your documents, can provide
fulltext search and has great e-mail integration. Everything is
accessible via a REST/HTTP api. A mobile friendly SPA web application
is the default user interface. An [Android
app](https://github.com/docspell/android-client) exists for
conveniently uploading files from your phone/tablet and a
[cli](https://github.com/docspell/dsc). The [feature
overview](https://docspell.org/#feature-selection) lists some more
points.


## About this fork

**docspell-ng** is a fork of [eikek/docspell](https://github.com/eikek/docspell),
branched from upstream `master` at
[`751b0b83`](https://github.com/eikek/docspell/commit/751b0b83) (October 2026).
The rest of this README is upstream's text and still describes the base project.
Work on the fork is tracked in [its issues](https://github.com/mutcianm/docspell-ng/issues).

### Entity model

Persons, organizations and equipment are replaced by one generic entity model
([#20](https://github.com/mutcianm/docspell-ng/issues/20), design in
[#21](https://github.com/mutcianm/docspell-ng/issues/21)):

- **Entity types** with their own attributes are defined in the app, under
  *Manage Data → Types & Roles*. The built-in types are *person* and
  *organization*; *equipment* becomes a regular type that can be renamed or
  deleted.
- **Attributes** use the custom field formats. They can hold several values,
  and values marked as alias, e-mail or website are used to recognize entities
  in documents.
- **Items link entities through roles.** Each role can hold several entities.
  The built-in roles are *sender*, *recipient* and *concerning*: sender and
  recipient are the parties of a document, while concerning is for what its
  content refers to (a car, an apartment). Further roles can be added.
- **Search:** `sender:acme*`, `recipient.id~=…` and
  `organization.email:*@example.com` work alongside the existing fields; see
  the [query language docs](website/site/content/docs/query/_index.md).
- **Migration from upstream data:** correspondents become *sender* on incoming
  and *recipient* on outgoing items. The former concerning person is the
  collective's own side: *recipient* on incoming and *sender* on outgoing
  items. Concerning equipment stays under *concerning*.

### OCR and text extraction

- Additional OCR languages for multilingual documents: `ocr-extra-languages`
  ([#9](https://github.com/mutcianm/docspell-ng/issues/9)).
- Fall back to OCR when a PDF's text layer is garbled or in the wrong script:
  `extraction.pdf.max-garbage-ratio` and `min-script-ratio`
  ([#10](https://github.com/mutcianm/docspell-ng/issues/10)).
- The joex image ships the more accurate `tessdata_best` Tesseract models
  ([#11](https://github.com/mutcianm/docspell-ng/issues/11)).
- Configurable ocrmypdf mode (`convert.ocrmypdf.mode`: `skip-text`,
  `redo-ocr`, `force-ocr`) to replace stale text layers
  ([#12](https://github.com/mutcianm/docspell-ng/issues/12)).

### Fixes and maintenance

- Dropdowns focus their filter input when opened, and dropdowns and menus close
  on outside click and Escape
  ([#1](https://github.com/mutcianm/docspell-ng/issues/1),
  [#2](https://github.com/mutcianm/docspell-ng/issues/2)).
- Search: tag categories show in the sidebar again
  ([#13](https://github.com/mutcianm/docspell-ng/issues/13)), and filter changes
  made while a search is running are no longer dropped
  ([#14](https://github.com/mutcianm/docspell-ng/issues/14)).
- Webapp assets are cache-busted between builds of the same version
  ([#16](https://github.com/mutcianm/docspell-ng/issues/16)).
- The PDF viewer is pdf.js 5.4 instead of 2.12, which had CVE-2024-4367
  ([#17](https://github.com/mutcianm/docspell-ng/issues/17)), and libraries with
  published advisories are updated
  ([#18](https://github.com/mutcianm/docspell-ng/issues/18)).

### Incompatibilities with upstream

- **Database:** the fork's migrations (from `1.40.0`) move persons,
  organizations, equipment and contacts into the entity tables and drop the old
  tables. They cannot be undone; back up the database before upgrading.
- **REST API:**
  - Creating, changing and deleting organizations, persons and equipment, and
    setting `corrOrg`, `corrPerson`, `concPerson` and `concEquipment` on items,
    are removed. Use `/sec/entity*` and `/sec/item/{id}/links` instead.
  - Reading still works: the `GET` endpoints for organizations, persons,
    equipment and contacts return entities in the former shape, and items still
    carry the four fields, derived from their links. Clients like
    [dsc](https://github.com/docspell/dsc) and addons keep working.
- **Person to organization affiliation** and the person, organization and
  equipment *use* settings are gone; each entity has a single "suggest" flag.
- **Mailbox scanning** no longer guesses the direction from the sender's
  address. Mails are incoming unless the scan task sets a direction.

### Images and documentation

The fork builds its own Docker images from this repository; see
[docker/README.md](docker/README.md). The restserver in these images
serves this repository's documentation at `/docs`, so the docs match
the running version; the webapp's help links point there
([#32](https://github.com/mutcianm/docspell-ng/issues/32)).


## Impressions

Checkout the short demo videos (<1min), present on the [project
page](https://docspell.org/#demos). Here are some screenshots:

![screenshot-1](website/site/content/docs/webapp/dashboards-01_dark.png)
![screenshot-2](website/site/content/docs/webapp/docspell-curate-1_dark.png)
![screenshot-3](website/site/content/docs/webapp/custom-fields-03_dark.png)
![screenshot-4](website/site/content/docs/webapp/multiedit-04_dark.png)
![screenshot-5](website/site/content/docs/webapp/mail-item-1.png)
![screenshot-7](website/site/static/screenshots/mobile/Screenshot_2021-02-14-04-small.jpg)
![screenshot-8](website/site/static/screenshots/mobile/Screenshot_2021-02-14-09-small.jpg)

## Try it

Docspell consists of several components. The probably quickest way to
get started is to use the docker setup as described in the [get started
page](https://docspell.org/#get-started). This is only three commands
away:

``` shell
git clone https://github.com/docspell/docker docspell-docker
cd docspell-docker/docker-compose
docker-compose up -d
```

Then go to `http://localhost:7880`, sign up and login. Use the same
name for collective and user for now. More on that can be found
[here](https://docspell.org/docs/intro/).

Other ways are documented
[here](https://docspell.org/docs/install/quickstart/):

- Install the [provided](https://github.com/eikek/docspell/releases)
  `deb` file at your debian based system.
- Download [provided](https://github.com/eikek/docspell/releases) zip
  file and run the script in `bin/`, as [described
  here](https://docspell.org/docs/install/installing/#download-unpack-run).
- Using the [nix](https://nixos.org/nix) package manager as [described
  here](https://docspell.org/docs/install/installing/#nix). A NixOS
  module is available, too.
- Use the [Helm Chart](https://github.com/docspell/docker/tree/main/charts/docspell).


## Documentation

The [project page](https://docspell.org) has lots of information on
how to [use and setup](https://docspell.org/docs) docspell.


## Contributions/Support

Feedback and other contributions are very welcome! There is a [gitter
room](https://gitter.im/eikek/docspell) for chatting and questions.
You can also [open an
issue](https://github.com/eikek/docspell/issues/new) for questions,
problems and other feedback; or make a mail to `info [at]
docspell.org`. I'll try to answer in time. You may want to check
[Contributing.md](Contributing.md) for some starting points, too.

If you find this project any useful and want to support it via other
means, giving a :star: is always encouraging and much appreciated. You
can also support activity by donating via
[liberapay](https://liberapay.com/eikek/) or
[paypal](https://paypal.me/eikek0).

Thank you so much!

## Tech Stack

**Backend**

The servers is written in Scala in a pure functional style, based on
libraries from the [typelevel](https://typelevel.org) stack: Cats,
FS2, Doobie, Http4s, Circe and Pureconfig.

There are more libraries and technologies used, of course. Docspell is
only a orchestration of great tools and libs. One important is the
[Stanford-NLP](https://nlp.stanford.edu/software/), that provides the
ML features. Furthermore, file processing relies on external tools
like tesseract, unoserver/unoconvert and ocrmypdf. All dependencies can be looked
up in `project/Dependencies.scala`.


**Frontend**

The web frontend is a SPA written in [Elm](https://elm-lang.org). The
UI framework in use is [tailwind](https://tailwindcss.com).


## License

Docspell is free software, distributed under the [AGPLv3 or
later](https://spdx.org/licenses/AGPL-3.0-or-later.html).
