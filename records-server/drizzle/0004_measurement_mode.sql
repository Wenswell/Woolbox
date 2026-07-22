ALTER TABLE `record_items` ADD `mode` text DEFAULT 'ACCUMULATE' NOT NULL;--> statement-breakpoint
CREATE INDEX `record_entries_item_occurred_at_index` ON `record_entries` (`item_id`,`occurred_at`);