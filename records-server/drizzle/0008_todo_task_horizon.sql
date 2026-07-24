ALTER TABLE `todo_tasks` ADD `horizon` text DEFAULT 'TODAY' NOT NULL;--> statement-breakpoint
CREATE INDEX `todo_tasks_horizon_index` ON `todo_tasks` (`horizon`);
