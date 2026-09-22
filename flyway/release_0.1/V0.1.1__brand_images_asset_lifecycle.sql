-- Brand images asset lifecycle: promote brand_images to a first-class asset table.
-- Renames image_url to image_key (opaque blob key, never a presigned URL), adds the shared
-- asset lifecycle status persisted as text with a CHECK contract, adds BaseEntity audit columns,
-- and adds targeted indexes. Applies on top of V0.1.0 and on a fresh database.

ALTER TABLE "brand_images" RENAME COLUMN "image_url" TO "image_key";

ALTER TABLE "brand_images" ADD COLUMN "status" varchar(16) NOT NULL DEFAULT 'PENDING';

ALTER TABLE "brand_images" ALTER COLUMN "status" DROP DEFAULT;

ALTER TABLE "brand_images"
    ADD CONSTRAINT "chk_brand_images_status" CHECK ("status" IN ('PENDING', 'UPLOADED', 'DELETED'));

ALTER TABLE "brand_images" ADD COLUMN "created_at" timestamp NOT NULL DEFAULT (now());
ALTER TABLE "brand_images" ADD COLUMN "created_by" varchar;
ALTER TABLE "brand_images" ADD COLUMN "modified_at" timestamp;
ALTER TABLE "brand_images" ADD COLUMN "modified_by" varchar;
ALTER TABLE "brand_images" ADD COLUMN "deleted_at" timestamp;
ALTER TABLE "brand_images" ADD COLUMN "deleted_by" varchar;

CREATE UNIQUE INDEX "uq_brand_images_image_key" ON "brand_images" ("image_key");
CREATE INDEX "ix_brand_images_brand_status" ON "brand_images" ("brand_id", "status");
CREATE INDEX "ix_brand_images_pending_created" ON "brand_images" ("created_at") WHERE "status" = 'PENDING';
