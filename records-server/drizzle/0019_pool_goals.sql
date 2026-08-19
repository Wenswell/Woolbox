CREATE TABLE `goals` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`title` text NOT NULL,
	`description` text NOT NULL,
	`unit` text NOT NULL,
	`default_value` real,
	`step_value` real NOT NULL,
	`start_date` text NOT NULL,
	`end_date` text NOT NULL,
	`archived` integer DEFAULT false NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `goal_subgoals` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`goal_id` integer NOT NULL,
	`title` text NOT NULL,
	`target_value` real NOT NULL,
	`position` integer NOT NULL,
	`completed_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`goal_id`) REFERENCES `goals`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `goal_subgoals_goal_position_index` ON `goal_subgoals` (`goal_id`,`position`);
--> statement-breakpoint
CREATE TABLE `goal_entries` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`goal_id` integer NOT NULL,
	`occurred_at` integer NOT NULL,
	`local_date` text NOT NULL,
	`local_minute` integer NOT NULL,
	`value` real NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`goal_id`) REFERENCES `goals`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `goal_entries_goal_occurred_at_index` ON `goal_entries` (`goal_id`,`occurred_at`);
--> statement-breakpoint
CREATE INDEX `goal_entries_local_date_minute_index` ON `goal_entries` (`local_date`,`local_minute`);
