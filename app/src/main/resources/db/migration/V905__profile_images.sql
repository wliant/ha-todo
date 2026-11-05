-- Stored images (requirements/0004 R32, and the detail field's pictures).
--
-- The bytes live in MinIO; this table is the inventory that says what exists,
-- who may read it, and what it is. Serving them through the API rather than by
-- presigned URL is docs/adr/0013 - MinIO is not published through either HTTPS
-- front door, and a presigned URL would outlive a revoked grant besides.

CREATE TABLE stored_image (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    object_key   VARCHAR(200) NOT NULL UNIQUE,
    content_type VARCHAR(40)  NOT NULL,
    size_bytes   INTEGER      NOT NULL,
    purpose      VARCHAR(16)  NOT NULL,
    uploaded_by  UUID         REFERENCES app_user (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_stored_image_purpose CHECK (purpose IN ('AVATAR', 'DETAIL')),

    -- The allowlist is repeated here, not just in the service, because this
    -- column decides what Content-Type the API will serve from our own origin.
    -- SVG is absent on purpose: an SVG served same-origin is script execution
    -- in our own security context.
    CONSTRAINT ck_stored_image_content_type
        CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp')),

    CONSTRAINT ck_stored_image_size CHECK (size_bytes > 0)
);

ALTER TABLE app_user
    ADD COLUMN avatar_image_id UUID REFERENCES stored_image (id) ON DELETE SET NULL;
