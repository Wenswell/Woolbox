CREATE TABLE `daily_checkins` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`local_date` text NOT NULL,
	`woke_at` integer NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `daily_checkins_local_date_unique` ON `daily_checkins` (`local_date`);
--> statement-breakpoint
CREATE TABLE `hourly_checkins` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`daily_checkin_id` integer NOT NULL,
	`hour` integer NOT NULL,
	`checked_in_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`daily_checkin_id`) REFERENCES `daily_checkins`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `hourly_checkins_date_hour_unique` ON `hourly_checkins` (`daily_checkin_id`,`hour`);
