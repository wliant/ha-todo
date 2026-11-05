-- A recurrence rule owns the pictures in its own detail (docs/adr/0023).
--
-- Without this the bytes behind a rule's detail are unreachable in a way that
-- appears weeks later: the first generated occurrence would claim them, and
-- deleting that one item would forget them - leaving every future occurrence
-- with a broken picture.
ALTER TABLE stored_image ADD COLUMN rule_id UUID REFERENCES recurrence_rule (id) ON DELETE CASCADE;

CREATE INDEX ix_stored_image_rule ON stored_image (rule_id) WHERE rule_id IS NOT NULL;

-- A picture belongs to an item or to a rule, never both.
ALTER TABLE stored_image ADD CONSTRAINT ck_stored_image_owner
    CHECK (item_id IS NULL OR rule_id IS NULL);
