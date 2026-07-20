CREATE TABLE `record_items` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`name` text NOT NULL,
	`description` text NOT NULL,
	`value_type` text NOT NULL,
	`default_value` real,
	`step_value` real NOT NULL,
	`unit` text NOT NULL,
	`goal_value` real,
	`goal_period` text,
	`priority` integer NOT NULL,
	`archived` integer DEFAULT false NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `record_items_name_unique` ON `record_items` (`name` COLLATE NOCASE);
--> statement-breakpoint
CREATE TABLE `record_windows` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`item_id` integer NOT NULL,
	`start_minute` integer NOT NULL,
	`end_minute` integer NOT NULL,
	FOREIGN KEY (`item_id`) REFERENCES `record_items`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `record_windows_item_id_index` ON `record_windows` (`item_id`);
--> statement-breakpoint
CREATE TABLE `record_entries` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`item_id` integer NOT NULL,
	`occurred_at` integer NOT NULL,
	`local_date` text NOT NULL,
	`local_minute` integer NOT NULL,
	`value` real NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`item_id`) REFERENCES `record_items`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `record_entries_item_id_index` ON `record_entries` (`item_id`);
--> statement-breakpoint
CREATE INDEX `record_entries_local_date_minute_index` ON `record_entries` (`local_date`,`local_minute`);
