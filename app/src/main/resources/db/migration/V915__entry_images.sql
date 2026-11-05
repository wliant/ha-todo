-- A collection entry owns the pictures in its detail, exactly as an item and a
-- recurrence rule already do (requirements/0006 R61, docs/adr/0032).
ALTER TABLE stored_image
    ADD COLUMN entry_id UUID REFERENCES data_entry (id) ON DELETE CASCADE;

CREATE INDEX ix_stored_image_entry ON stored_image (entry_id) WHERE entry_id IS NOT NULL;

-- A picture belongs to one owner or to none. The two-column form added in V911
-- does not generalise, and Postgres cannot alter a CHECK - so it is dropped and
-- recreated as a count, which will not need rewriting when a fourth owner
-- appears. (At four, docs/adr/0032 says to replace this shape with a join
-- table rather than add a fifth column.)
ALTER TABLE stored_image DROP CONSTRAINT ck_stored_image_owner;
ALTER TABLE stored_image ADD CONSTRAINT ck_stored_image_owner
    CHECK (num_nonnulls(item_id, rule_id, entry_id) <= 1);

-- Repairs a pre-existing bug, because this is the change that makes it matter.
-- The index was written in V907 as "item_id IS NULL AND purpose = 'DETAIL'" and
-- was never updated when rule_id arrived in V911, so it has been reporting
-- every rule-owned picture as an unclaimed orphan. Nothing sweeps orphans today
-- (docs/adr/0013 tolerates them), so it is misleading rather than destructive -
-- but a sweeper written against it would delete the pictures behind every
-- standing instruction.
DROP INDEX ix_stored_image_unclaimed;
CREATE INDEX ix_stored_image_unclaimed ON stored_image (created_at)
    WHERE item_id IS NULL AND rule_id IS NULL AND entry_id IS NULL AND purpose = 'DETAIL';
