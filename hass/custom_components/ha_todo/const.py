"""Names shared across the integration."""

DOMAIN = "ha_todo"

CONF_URL = "url"
CONF_USERNAME = "username"
CONF_PASSWORD = "password"
CONF_VERIFY_SSL = "verify_ssl"
CONF_SCAN_INTERVAL = "scan_interval"

DEFAULT_SCAN_INTERVAL = 60
MIN_SCAN_INTERVAL = 30
MAX_SCAN_INTERVAL = 600

#: How many items one list sensor publishes. A bound, not a page: a household
#: list is a few dozen rows, and an unbounded attribute is pushed to every
#: connected browser on every poll.
MAX_PUBLISHED_ITEMS = 200

SERVICE_ADD_ITEM = "add_item"
SERVICE_COMPLETE_ITEM = "complete_item"
SERVICE_LAPSE_ITEM = "lapse_item"
SERVICE_REOPEN_ITEM = "reopen_item"
SERVICE_ADD_COLLECTION_ENTRY = "add_collection_entry"
SERVICE_RECORD_TRACKER_READING = "record_tracker_reading"

ATTR_LIST = "list"
ATTR_ITEM = "item"
ATTR_DUE_DATE = "due_date"
ATTR_DUE_DATETIME = "due_datetime"
ATTR_COLLECTION = "collection"
ATTR_TRACKER = "tracker"
ATTR_NAME = "name"
ATTR_DESCRIPTION = "description"
ATTR_TAGS = "tags"
ATTR_AMOUNT = "amount"
ATTR_NOTE = "note"
ATTR_PERIOD_START = "period_start"
