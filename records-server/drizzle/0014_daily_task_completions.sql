CREATE TABLE `todo_step_completions` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`step_id` integer NOT NULL,
	`scope` text NOT NULL,
	`local_date` text,
	`completed_at` integer NOT NULL,
	FOREIGN KEY (`step_id`) REFERENCES `todo_steps`(`id`) ON UPDATE no action ON DELETE cascade,
	CONSTRAINT "todo_step_completions_scope_date_check" CHECK(("todo_step_completions"."scope" = 'ONCE' AND "todo_step_completions"."local_date" IS NULL) OR ("todo_step_completions"."scope" = 'DAILY' AND "todo_step_completions"."local_date" IS NOT NULL))
);
--> statement-breakpoint
CREATE UNIQUE INDEX `todo_step_completions_once_unique` ON `todo_step_completions` (`step_id`) WHERE "todo_step_completions"."scope" = 'ONCE';--> statement-breakpoint
CREATE UNIQUE INDEX `todo_step_completions_daily_unique` ON `todo_step_completions` (`step_id`,`local_date`) WHERE "todo_step_completions"."scope" = 'DAILY';--> statement-breakpoint
CREATE INDEX `todo_step_completions_step_id_index` ON `todo_step_completions` (`step_id`);--> statement-breakpoint
INSERT INTO `todo_step_completions` (`step_id`, `scope`, `local_date`, `completed_at`)
SELECT `id`, 'ONCE', NULL, `completed_at`
FROM `todo_steps`
WHERE `completed_at` IS NOT NULL;--> statement-breakpoint
ALTER TABLE `todo_tasks` ADD `repeat_mode` text DEFAULT 'ONCE' NOT NULL;--> statement-breakpoint
ALTER TABLE `todo_tasks` DROP COLUMN `completed_at`;--> statement-breakpoint
ALTER TABLE `todo_steps` DROP COLUMN `completed_at`;
