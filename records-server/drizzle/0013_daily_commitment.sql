CREATE TABLE `daily_plans` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`local_date` text NOT NULL,
	`first_move` text NOT NULL,
	`first_move_completed_at` integer,
	`primary_task_id` integer,
	`secondary_task_id` integer,
	`current_task_id` integer,
	`state` text NOT NULL,
	`completed_rounds` integer NOT NULL,
	`next_review_at` integer,
	`revision` integer NOT NULL,
	`ended_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`primary_task_id`) REFERENCES `todo_tasks`(`id`) ON UPDATE no action ON DELETE set null,
	FOREIGN KEY (`secondary_task_id`) REFERENCES `todo_tasks`(`id`) ON UPDATE no action ON DELETE set null,
	FOREIGN KEY (`current_task_id`) REFERENCES `todo_tasks`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE UNIQUE INDEX `daily_plans_local_date_unique` ON `daily_plans` (`local_date`);--> statement-breakpoint
DROP INDEX `todo_tasks_planned_date_index`;--> statement-breakpoint
ALTER TABLE `todo_tasks` DROP COLUMN `planned_date`;