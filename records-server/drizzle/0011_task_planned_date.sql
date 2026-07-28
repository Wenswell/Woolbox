DELETE FROM `todo_step_windows`;
--> statement-breakpoint
ALTER TABLE `todo_tasks` ADD `planned_date` text;
--> statement-breakpoint
DROP INDEX `todo_tasks_horizon_index`;
--> statement-breakpoint
ALTER TABLE `todo_tasks` DROP COLUMN `horizon`;
--> statement-breakpoint
CREATE INDEX `todo_tasks_planned_date_index` ON `todo_tasks` (`planned_date`);
