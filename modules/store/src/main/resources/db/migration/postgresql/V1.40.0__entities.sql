-- Generic entity model (#20): user-definable entity types, attributes and item roles.
-- Built-in types and roles are seeded per collective and identified by their key.

create table "entity_type"(
  "id" varchar(254) not null primary key,
  "coll_id" bigint not null,
  "type_key" varchar(254),
  "name" varchar(254) not null,
  "label" varchar(254),
  "builtin" boolean not null,
  "recognize" boolean not null,
  "created" timestamp not null,
  foreign key ("coll_id") references "collective"("id"),
  unique ("coll_id", "name"),
  unique ("coll_id", "type_key")
);

create table "attr_def"(
  "id" varchar(254) not null primary key,
  "type_id" varchar(254) not null,
  "name" varchar(254) not null,
  "label" varchar(254),
  "ftype" varchar(100) not null,
  "multi" boolean not null,
  "matcher" varchar(100) not null,
  "created" timestamp not null,
  foreign key ("type_id") references "entity_type"("id"),
  unique ("type_id", "name")
);

create table "entity"(
  "id" varchar(254) not null primary key,
  "coll_id" bigint not null,
  "type_id" varchar(254) not null,
  "name" varchar(254) not null,
  "notes" text,
  "suggest" boolean not null,
  "created" timestamp not null,
  "updated" timestamp not null,
  foreign key ("coll_id") references "collective"("id"),
  foreign key ("type_id") references "entity_type"("id"),
  unique ("type_id", "name")
);

create table "entity_attr"(
  "id" varchar(254) not null primary key,
  "entity_id" varchar(254) not null,
  "attr_def_id" varchar(254) not null,
  "value_" varchar(300) not null,
  foreign key ("entity_id") references "entity"("id"),
  foreign key ("attr_def_id") references "attr_def"("id")
);
create index "entity_attr_entity_id_idx" on "entity_attr"("entity_id");
create index "entity_attr_def_value_idx" on "entity_attr"("attr_def_id", "value_");

create table "entity_role"(
  "id" varchar(254) not null primary key,
  "coll_id" bigint not null,
  "role_key" varchar(254),
  "name" varchar(254) not null,
  "label" varchar(254),
  "builtin" boolean not null,
  "multi" boolean not null,
  "learn" boolean not null,
  "created" timestamp not null,
  foreign key ("coll_id") references "collective"("id"),
  unique ("coll_id", "name"),
  unique ("coll_id", "role_key")
);

create table "entity_role_target"(
  "role_id" varchar(254) not null,
  "type_id" varchar(254) not null,
  primary key ("role_id", "type_id"),
  foreign key ("role_id") references "entity_role"("id"),
  foreign key ("type_id") references "entity_type"("id")
);

create table "item_entity"(
  "id" varchar(254) not null primary key,
  "item_id" varchar(254) not null,
  "entity_id" varchar(254) not null,
  "role_id" varchar(254) not null,
  foreign key ("item_id") references "item"("itemid"),
  foreign key ("entity_id") references "entity"("id"),
  foreign key ("role_id") references "entity_role"("id"),
  unique ("item_id", "entity_id", "role_id")
);
create index "item_entity_entity_id_idx" on "item_entity"("entity_id");
create index "item_entity_role_id_idx" on "item_entity"("role_id");

-- built-in types and roles for every existing collective
insert into "entity_type" ("id", "coll_id", "type_key", "name", "label", "builtin", "recognize", "created")
  select concat('et-person-', c."id"), c."id", 'person', 'person', 'Person', true, true, current_timestamp from "collective" c
  union all
  select concat('et-organization-', c."id"), c."id", 'organization', 'organization', 'Organization', true, true, current_timestamp from "collective" c
  union all
  select concat('et-equipment-', c."id"), c."id", 'equipment', 'equipment', 'Equipment', false, true, current_timestamp from "collective" c;

insert into "attr_def" ("id", "type_id", "name", "label", "ftype", "multi", "matcher", "created")
  select concat(t."id", '-', a."n"), t."id", a."n", a."l", 'text', a."m", a."k", current_timestamp
  from "entity_type" t,
    (select 'street' as "n", 'Street' as "l", false as "m", 'none' as "k"
     union all select 'zip', 'Zip code', false, 'none'
     union all select 'city', 'City', false, 'none'
     union all select 'country', 'Country', false, 'none'
     union all select 'phone', 'Phone', true, 'none'
     union all select 'mobile', 'Mobile', true, 'none'
     union all select 'fax', 'Fax', true, 'none'
     union all select 'email', 'E-Mail', true, 'email'
     union all select 'website', 'Website', true, 'website') a
  where t."type_key" in ('person', 'organization');

insert into "attr_def" ("id", "type_id", "name", "label", "ftype", "multi", "matcher", "created")
  select concat(t."id", '-shortname'), t."id", 'shortname', 'Short name', 'text', true, 'alias', current_timestamp
  from "entity_type" t where t."type_key" = 'organization';

insert into "entity_role" ("id", "coll_id", "role_key", "name", "label", "builtin", "multi", "learn", "created")
  select concat('role-sender-', c."id"), c."id", 'sender', 'sender', 'Sender', true, true, true, current_timestamp from "collective" c
  union all
  select concat('role-recipient-', c."id"), c."id", 'recipient', 'recipient', 'Recipient', true, true, true, current_timestamp from "collective" c
  union all
  select concat('role-concerning-', c."id"), c."id", 'concerning', 'concerning', 'Concerning', true, true, true, current_timestamp from "collective" c;

-- sender and recipient accept persons and organizations, concerning accepts any type
insert into "entity_role_target" ("role_id", "type_id")
  select r."id", t."id" from "entity_role" r join "entity_type" t on t."coll_id" = r."coll_id"
  where r."role_key" in ('sender', 'recipient') and t."type_key" in ('person', 'organization');

-- existing data; entities keep their ids, so a collision between tables fails the migration
insert into "entity" ("id", "coll_id", "type_id", "name", "notes", "suggest", "created", "updated")
  select p."pid", p."coll_id", concat('et-person-', p."coll_id"), p."name", p."notes",
         coalesce(p."person_use", '') <> 'disabled', p."created", p."updated"
  from "person" p;
insert into "entity" ("id", "coll_id", "type_id", "name", "notes", "suggest", "created", "updated")
  select o."oid", o."coll_id", concat('et-organization-', o."coll_id"), o."name", o."notes",
         coalesce(o."org_use", '') <> 'disabled', o."created", o."updated"
  from "organization" o;
insert into "entity" ("id", "coll_id", "type_id", "name", "notes", "suggest", "created", "updated")
  select e."eid", e."coll_id", concat('et-equipment-', e."coll_id"), e."name", e."notes",
         coalesce(e."equip_use", '') <> 'disabled', e."created", e."updated"
  from "equipment" e;

insert into "entity_attr" ("id", "entity_id", "attr_def_id", "value_")
  select concat(x."id", '-', x."n"), x."id", concat(x."tid", '-', x."n"), x."v" from (
    select p."pid" as "id", concat('et-person-', p."coll_id") as "tid", 'street' as "n", p."street" as "v" from "person" p
    union all select p."pid", concat('et-person-', p."coll_id"), 'zip', p."zip" from "person" p
    union all select p."pid", concat('et-person-', p."coll_id"), 'city', p."city" from "person" p
    union all select p."pid", concat('et-person-', p."coll_id"), 'country', p."country" from "person" p
    union all select o."oid", concat('et-organization-', o."coll_id"), 'street', o."street" from "organization" o
    union all select o."oid", concat('et-organization-', o."coll_id"), 'zip', o."zip" from "organization" o
    union all select o."oid", concat('et-organization-', o."coll_id"), 'city', o."city" from "organization" o
    union all select o."oid", concat('et-organization-', o."coll_id"), 'country', o."country" from "organization" o
    union all select o."oid", concat('et-organization-', o."coll_id"), 'shortname', o."short_name" from "organization" o
  ) x
  where x."v" is not null and x."v" <> '';

insert into "entity_attr" ("id", "entity_id", "attr_def_id", "value_")
  select c."contactid", e."id", concat(e."type_id", '-', lower(c."kind")), c."value_"
  from "contact" c join "entity" e on e."id" = coalesce(c."pid", c."oid");

insert into "item_entity" ("id", "item_id", "entity_id", "role_id")
  select concat(i."itemid", '-', x."k"), i."itemid", x."eid",
         case when x."k" in ('corrorg', 'corrperson')
              then case when i."incoming" then concat('role-sender-', i."coll_id") else concat('role-recipient-', i."coll_id") end
              -- the concerning person is the collective's own side of the document
              when x."k" = 'concperson'
              then case when i."incoming" then concat('role-recipient-', i."coll_id") else concat('role-sender-', i."coll_id") end
              else concat('role-concerning-', i."coll_id") end
  from "item" i join (
    select "itemid", 'corrorg' as "k", "corrorg" as "eid" from "item" where "corrorg" is not null
    union all select "itemid", 'corrperson', "corrperson" from "item" where "corrperson" is not null
    union all select "itemid", 'concperson', "concperson" from "item" where "concperson" is not null
    union all select "itemid", 'concequipment', "concequipment" from "item" where "concequipment" is not null
  ) x on x."itemid" = i."itemid";
