CREATE TABLE "addresses" (
    "id"          SERIAL PRIMARY KEY,
    "address"     varchar NOT NULL,
    "zip_code_id" int     NOT NULL,
    "longitude"   numeric NOT NULL,
    "latitude"    numeric NOT NULL
);

CREATE TABLE "dispensaries" (
    "id"                SERIAL PRIMARY KEY,
    "name"              varchar   NOT NULL,
    "logo_image_url"    varchar   NOT NULL,
    "description"       text,
    "license"           varchar   NOT NULL,
    "license_status_id" int       NOT NULL,
    "phone"             varchar   NOT NULL,
    "email"             varchar   NOT NULL,
    "instagram_url"     varchar,
    "twitter_url"       varchar,
    "facebook_url"      varchar,
    "website_url"       varchar,
    "address_id"        int       NOT NULL,
    "commission"        numeric   NOT NULL,
    "admin_id"          int       NOT NULL,
    "enabled"           boolean,
    "created_at"        timestamp NOT NULL DEFAULT (now()),
    "created_by"        varchar,
    "modified_at"       timestamp,
    "modified_by"       varchar,
    "deleted_at"        timestamp,
    "deleted_by"        varchar
);

CREATE TABLE "license_statuses" (
    "id"        SERIAL PRIMARY KEY,
    "state"     varchar NOT NULL,
    "tag_icon"  varchar NOT NULL,
    "tag_color" varchar NOT NULL
);

INSERT INTO "license_statuses" ("state", "tag_icon", "tag_color") VALUES
    ('APPROVED', 'check-circle', '#00FF00'),
    ('PENDING', 'hourglass-half', '#FFFF00'),
    ('REJECTED', 'exclamation-circle', '#FF0000'),
    ('SUSPENDED', 'ban', '#FFA500');

CREATE TABLE "working_hours" (
    "id"            SERIAL PRIMARY KEY,
    "dispensary_id" int     NOT NULL,
    "day"           varchar NOT NULL,
    "from"          time    NOT NULL,
    "to"            time    NOT NULL
);

CREATE TABLE "reviews_dispensary" (
    "id"            SERIAL PRIMARY KEY,
    "dispensary_id" int       NOT NULL,
    "title"         varchar   NOT NULL,
    "description"   varchar   NOT NULL,
    "stars"         int       NOT NULL,
    "user_id"       int       NOT NULL,
    "created_at"    timestamp NOT NULL DEFAULT (now())
);

CREATE TABLE "brands" (
    "id"             SERIAL PRIMARY KEY,
    "name"           varchar   NOT NULL,
    "description"    text      NOT NULL,
    "email"          varchar   NOT NULL,
    "state_license"  varchar   NOT NULL,
    "brand_type_id"  int       NOT NULL,
    "logo_image_url" varchar   NOT NULL,
    "instagram_url"  varchar,
    "twitter_url"    varchar,
    "facebook_url"   varchar,
    "website_url"    varchar,
    "admin_id"       int       NOT NULL,
    "enabled"        boolean,
    "created_at"     timestamp NOT NULL DEFAULT (now()),
    "created_by"     varchar,
    "modified_at"    timestamp,
    "modified_by"    varchar,
    "deleted_at"     timestamp,
    "deleted_by"     varchar
);

CREATE TABLE "brand_types" (
    "id"   SERIAL PRIMARY KEY,
    "name" varchar NOT NULL
);

CREATE TABLE "brand_featured_products" (
    "id"         SERIAL PRIMARY KEY,
    "brand_id"   int NOT NULL,
    "product_id" int NOT NULL
);

CREATE TABLE "dispensary_images" (
    "id"            SERIAL PRIMARY KEY,
    "dispensary_id" int     NOT NULL,
    "image_url"     varchar NOT NULL
);

CREATE TABLE "brand_images" (
    "id"        SERIAL PRIMARY KEY,
    "brand_id"  int     NOT NULL,
    "image_url" varchar NOT NULL
);

CREATE TABLE "brand_videos" (
    "id"        SERIAL PRIMARY KEY,
    "brand_id"  int     NOT NULL,
    "video_url" varchar NOT NULL
);

CREATE TABLE "conditions" (
    "id"       SERIAL PRIMARY KEY,
    "name"     varchar NOT NULL,
    "tag_icon" varchar NOT NULL
);

CREATE TABLE "effects" (
    "id"       SERIAL PRIMARY KEY,
    "name"     varchar NOT NULL,
    "tag_icon" varchar NOT NULL
);

CREATE TABLE "flavors" (
    "id"       SERIAL PRIMARY KEY,
    "name"     varchar NOT NULL,
    "tag_icon" varchar NOT NULL
);

CREATE TABLE "categories" (
    "id"        SERIAL PRIMARY KEY,
    "name"      varchar NOT NULL,
    "image_url" varchar NOT NULL,
    "tag_icon"  varchar NOT NULL,
    "tag_color" varchar NOT NULL
);

CREATE TABLE "subcategories" (
    "id"          SERIAL PRIMARY KEY,
    "name"        varchar NOT NULL,
    "category_id" int     NOT NULL,
    "image_url"   varchar NOT NULL,
    "tag_icon"    varchar NOT NULL,
    "tag_color"   varchar NOT NULL
);

CREATE TABLE "uses" (
    "id"        SERIAL PRIMARY KEY,
    "name"      varchar NOT NULL,
    "image_url" varchar NOT NULL
);

CREATE TABLE "strains" (
    "id"                       SERIAL PRIMARY KEY,
    "ucpc"                     varchar   NOT NULL,
    "name"                     varchar   NOT NULL,
    "description"              varchar   NOT NULL,
    "strain_type_id"           int       NOT NULL,
    "seed_company_id"          int       NOT NULL,
    "calming_energizing_value" int       NOT NULL,
    "thc"                      int,
    "cbd"                      int,
    "cbg"                      int,
    "thcv"                     int,
    "enabled"                  boolean,
    "created_at"               timestamp NOT NULL DEFAULT (now()),
    "created_by"               varchar,
    "modified_at"              timestamp,
    "modified_by"              varchar,
    "deleted_at"               timestamp,
    "deleted_by"               varchar
);

CREATE TABLE "terpenes" (
    "id"   SERIAL PRIMARY KEY,
    "name" varchar NOT NULL
);

CREATE TABLE "strain_terpenes" (
    "id"         SERIAL PRIMARY KEY,
    "strain_id"  int NOT NULL,
    "terpene_id" int NOT NULL
);

CREATE TABLE "seed_companies" (
    "id"   SERIAL PRIMARY KEY,
    "name" varchar NOT NULL
);

CREATE TABLE "strain_types" (
    "id"   SERIAL PRIMARY KEY,
    "name" varchar NOT NULL
);

CREATE TABLE "strain_images" (
    "id"        SERIAL PRIMARY KEY,
    "strain_id" int     NOT NULL,
    "image_url" varchar NOT NULL
);

CREATE TABLE "products" (
    "id"              SERIAL PRIMARY KEY,
    "ocpc"            varchar   NOT NULL,
    "title"           varchar   NOT NULL,
    "description"     text,
    "collection_id"   int       NOT NULL,
    "category_id"     int       NOT NULL,
    "subcategory_id"  int       NOT NULL,
    "brand_id"        int       NOT NULL,
    "strain_id"       int       NOT NULL,
    "format_value"    int       NOT NULL,
    "format_unit_id"  int       NOT NULL,
    "content_value"   int       NOT NULL,
    "content_unit_id" int       NOT NULL,
    "is_core_product" boolean,
    "approved"        boolean,
    "thc"             int,
    "cbd"             int,
    "enabled"         boolean,
    "created_at"      timestamp NOT NULL DEFAULT (now()),
    "created_by"      varchar,
    "modified_at"     timestamp,
    "modified_by"     varchar,
    "deleted_at"      timestamp,
    "deleted_by"      varchar
);

CREATE TABLE "collections" (
    "id"   SERIAL PRIMARY KEY,
    "name" varchar NOT NULL
);

CREATE TABLE "reviews_product" (
    "id"          SERIAL PRIMARY KEY,
    "product_id"  int       NOT NULL,
    "title"       varchar   NOT NULL,
    "description" varchar   NOT NULL,
    "image_url"   varchar,
    "stars"       int       NOT NULL,
    "user_id"     int       NOT NULL,
    "created_at"  timestamp NOT NULL DEFAULT (now())
);

CREATE TABLE "units" (
    "id"   SERIAL PRIMARY KEY,
    "name" varchar NOT NULL
);

CREATE TABLE "product_available_states" (
    "id"         SERIAL PRIMARY KEY,
    "product_id" int NOT NULL,
    "state_id"   int NOT NULL
);

CREATE TABLE "product_images" (
    "id"         SERIAL PRIMARY KEY,
    "product_id" int     NOT NULL,
    "image_url"  varchar NOT NULL
);

CREATE TABLE "product_uses" (
    "id"         SERIAL PRIMARY KEY,
    "product_id" int NOT NULL,
    "use_id"     int NOT NULL
);

CREATE TABLE "strain_effects" (
    "id"        SERIAL PRIMARY KEY,
    "strain_id" int NOT NULL,
    "effect_id" int NOT NULL
);

CREATE TABLE "strain_conditions" (
    "id"           SERIAL PRIMARY KEY,
    "strain_id"    int NOT NULL,
    "condition_id" int NOT NULL
);

CREATE TABLE "strain_flavors" (
    "id"        SERIAL PRIMARY KEY,
    "strain_id" int NOT NULL,
    "flavor_id" int NOT NULL
);

CREATE TABLE "fav_user_products" (
    "id"         SERIAL PRIMARY KEY,
    "user_id"    int NOT NULL,
    "product_id" int NOT NULL
);

CREATE TABLE "fav_user_brands" (
    "id"       SERIAL PRIMARY KEY,
    "user_id"  int NOT NULL,
    "brand_id" int NOT NULL
);

CREATE TABLE "dispensary_products" (
    "id"            SERIAL PRIMARY KEY,
    "dispensary_id" int       NOT NULL,
    "product_id"    int       NOT NULL,
    "price"         numeric   NOT NULL,
    "stock"         int       NOT NULL,
    "enabled"       boolean,
    "created_at"    timestamp NOT NULL DEFAULT (now()),
    "created_by"    varchar,
    "modified_at"   timestamp,
    "modified_by"   varchar,
    "deleted_at"    timestamp,
    "deleted_by"    varchar
);

CREATE TABLE "stock_traces" (
    "id"                    SERIAL PRIMARY KEY,
    "dispensary_product_id" int       NOT NULL,
    "stock"                 int       NOT NULL,
    "date"                  timestamp NOT NULL DEFAULT (now()),
    "stock_origin_id"       int       NOT NULL,
    "order_id"              int
);

CREATE TABLE "stock_origins" (
    "id"   SERIAL PRIMARY KEY,
    "name" varchar NOT NULL
);

CREATE TABLE "configs" (
    "id"    SERIAL PRIMARY KEY,
    "key"   varchar NOT NULL,
    "value" varchar NOT NULL
);

ALTER TABLE "dispensaries"
    ADD CONSTRAINT fk_dispensaries_license_statuses FOREIGN KEY ("license_status_id") REFERENCES "license_statuses" ("id");

ALTER TABLE "dispensaries"
    ADD CONSTRAINT fk_dispensaries_addresses FOREIGN KEY ("address_id") REFERENCES "addresses" ("id");

ALTER TABLE "working_hours"
    ADD CONSTRAINT fk_working_hours_dispensaries FOREIGN KEY ("dispensary_id") REFERENCES "dispensaries" ("id");

ALTER TABLE "reviews_dispensary"
    ADD CONSTRAINT fk_reviews_dispensary_dispensaries FOREIGN KEY ("dispensary_id") REFERENCES "dispensaries" ("id");

ALTER TABLE "brands"
    ADD CONSTRAINT fk_brands_brand_types FOREIGN KEY ("brand_type_id") REFERENCES "brand_types" ("id");

ALTER TABLE "brand_featured_products"
    ADD CONSTRAINT fk_brand_featured_products_brands FOREIGN KEY ("brand_id") REFERENCES "brands" ("id");

ALTER TABLE "brand_featured_products"
    ADD CONSTRAINT fk_brand_featured_products_products FOREIGN KEY ("product_id") REFERENCES "products" ("id");

ALTER TABLE "dispensary_images"
    ADD CONSTRAINT fk_dispensary_images_dispensary_images FOREIGN KEY ("dispensary_id") REFERENCES "dispensaries" ("id");

ALTER TABLE "brand_images"
    ADD CONSTRAINT fk_brand_images_brands FOREIGN KEY ("brand_id") REFERENCES "brands" ("id");

ALTER TABLE "brand_videos"
    ADD CONSTRAINT fk_brand_videos_brands FOREIGN KEY ("brand_id") REFERENCES "brands" ("id");

ALTER TABLE "strains"
    ADD CONSTRAINT fk_strains_strain_types FOREIGN KEY ("strain_type_id") REFERENCES "strain_types" ("id");

ALTER TABLE "strains"
    ADD CONSTRAINT fk_strains_seed_companies FOREIGN KEY ("seed_company_id") REFERENCES "seed_companies" ("id");

ALTER TABLE "strain_terpenes"
    ADD CONSTRAINT fk_strain_terpenes_strains FOREIGN KEY ("strain_id") REFERENCES "strains" ("id");

ALTER TABLE "strain_terpenes"
    ADD CONSTRAINT fk_strain_terpenes_terpenes FOREIGN KEY ("terpene_id") REFERENCES "terpenes" ("id");

ALTER TABLE "strain_images"
    ADD CONSTRAINT fk_strain_images_strains FOREIGN KEY ("strain_id") REFERENCES "strains" ("id");

ALTER TABLE "products"
    ADD CONSTRAINT fk_products_collections FOREIGN KEY ("collection_id") REFERENCES "collections" ("id");

ALTER TABLE "products"
    ADD CONSTRAINT fk_products_categories FOREIGN KEY ("category_id") REFERENCES "categories" ("id");

ALTER TABLE "products"
    ADD CONSTRAINT fk_products_subcategories FOREIGN KEY ("subcategory_id") REFERENCES "subcategories" ("id");

ALTER TABLE "products"
    ADD CONSTRAINT fk_products_brands FOREIGN KEY ("brand_id") REFERENCES "brands" ("id");

ALTER TABLE "products"
    ADD CONSTRAINT fk_products_strains FOREIGN KEY ("strain_id") REFERENCES "strains" ("id");

ALTER TABLE "products"
    ADD CONSTRAINT fk_products_units_format FOREIGN KEY ("format_unit_id") REFERENCES "units" ("id");

ALTER TABLE "products"
    ADD CONSTRAINT fk_products_units_content FOREIGN KEY ("content_unit_id") REFERENCES "units" ("id");

ALTER TABLE "reviews_product"
    ADD CONSTRAINT fk_reviews_product_products FOREIGN KEY ("product_id") REFERENCES "products" ("id");

ALTER TABLE "product_available_states"
    ADD CONSTRAINT fk_product_available_states_products FOREIGN KEY ("product_id") REFERENCES "products" ("id");

ALTER TABLE "product_images"
    ADD CONSTRAINT fk_product_images_products FOREIGN KEY ("product_id") REFERENCES "products" ("id");

ALTER TABLE "product_uses"
    ADD CONSTRAINT fk_product_uses_products FOREIGN KEY ("product_id") REFERENCES "products" ("id");

ALTER TABLE "product_uses"
    ADD CONSTRAINT fk_product_uses_uses FOREIGN KEY ("use_id") REFERENCES "uses" ("id");

ALTER TABLE "strain_effects"
    ADD CONSTRAINT fk_strain_effects_strains FOREIGN KEY ("strain_id") REFERENCES "strains" ("id");

ALTER TABLE "strain_effects"
    ADD CONSTRAINT fk_strain_effects_effects FOREIGN KEY ("effect_id") REFERENCES "effects" ("id");

ALTER TABLE "strain_conditions"
    ADD CONSTRAINT fk_strain_conditions_strains FOREIGN KEY ("strain_id") REFERENCES "strains" ("id");

ALTER TABLE "strain_conditions"
    ADD CONSTRAINT fk_strain_conditions_conditions FOREIGN KEY ("condition_id") REFERENCES "conditions" ("id");

ALTER TABLE "strain_flavors"
    ADD CONSTRAINT fk_strain_flavors_strains FOREIGN KEY ("strain_id") REFERENCES "strains" ("id");

ALTER TABLE "strain_flavors"
    ADD CONSTRAINT fk_strain_flavors_flavors FOREIGN KEY ("flavor_id") REFERENCES "flavors" ("id");

ALTER TABLE "dispensary_products"
    ADD CONSTRAINT fk_dispensary_products_dispensaries FOREIGN KEY ("dispensary_id") REFERENCES "dispensaries" ("id");

ALTER TABLE "dispensary_products"
    ADD CONSTRAINT fk_dispensary_products_products FOREIGN KEY ("product_id") REFERENCES "products" ("id");

ALTER TABLE "stock_traces"
    ADD CONSTRAINT fk_stock_traces_dispensary_products FOREIGN KEY ("dispensary_product_id") REFERENCES "dispensary_products" ("id");

ALTER TABLE "stock_traces"
    ADD CONSTRAINT fk_stock_traces_stock_origins FOREIGN KEY ("stock_origin_id") REFERENCES "stock_origins" ("id");

ALTER TABLE "subcategories"
    ADD CONSTRAINT fk_subcategories_categories FOREIGN KEY ("category_id") REFERENCES "categories" ("id");
