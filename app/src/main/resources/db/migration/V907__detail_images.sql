-- Ties a detail picture to the item that uses it (requirements/0004 R21).
--
-- Without this a DETAIL image has no owner, and "who may read it" has no
-- answer but "anybody signed in" - which would put a private list's pictures
-- in front of the whole household, exactly what R19 exists to prevent. With
-- it, a detail image inherits the access rule of the list its item is on.
--
-- Null means unclaimed: uploaded while an item was being written and not yet
-- saved, or abandoned. Those are readable only by whoever uploaded them, and
-- are the orphans docs/adr/0013 says are tolerated rather than swept.
ALTER TABLE stored_image
    ADD COLUMN item_id UUID REFERENCES todo_item (id) ON DELETE CASCADE;

-- Finding an item's pictures, and finding the abandoned ones a sweeper would
-- eventually want.
CREATE INDEX ix_stored_image_item ON stored_image (item_id) WHERE item_id IS NOT NULL;
CREATE INDEX ix_stored_image_unclaimed ON stored_image (created_at)
    WHERE item_id IS NULL AND purpose = 'DETAIL';
