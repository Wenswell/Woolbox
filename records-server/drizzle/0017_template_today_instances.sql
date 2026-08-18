DELETE FROM `record_audit_logs` WHERE `entity_type` IN ('TASK', 'STEP', 'DAILY_PLAN');
--> statement-breakpoint
DROP TABLE `daily_plans`;
--> statement-breakpoint
DROP TABLE `todo_step_completions`;
--> statement-breakpoint
DROP TABLE `todo_steps`;
--> statement-breakpoint
DROP TABLE `todo_tasks`;
--> statement-breakpoint
CREATE TABLE `task_templates` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`title` text NOT NULL,
	`cadence` text NOT NULL,
	`archived` integer DEFAULT false NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `task_templates_cadence_archived_index` ON `task_templates` (`cadence`,`archived`);
--> statement-breakpoint
CREATE TABLE `task_template_steps` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`template_id` integer NOT NULL,
	`title` text NOT NULL,
	`position` integer NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`template_id`) REFERENCES `task_templates`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `task_template_steps_template_position_index` ON `task_template_steps` (`template_id`,`position`);
--> statement-breakpoint
CREATE TABLE `daily_plans` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`local_date` text NOT NULL,
	`first_move` text NOT NULL,
	`first_move_completed_at` integer,
	`current_commitment_id` integer,
	`state` text NOT NULL,
	`pause_reason` text,
	`completed_rounds` integer NOT NULL,
	`next_review_at` integer,
	`revision` integer NOT NULL,
	`ended_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `daily_plans_local_date_unique` ON `daily_plans` (`local_date`);
--> statement-breakpoint
CREATE TABLE `today_commitments` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`plan_id` integer NOT NULL,
	`template_id` integer,
	`role` text NOT NULL,
	`position` integer NOT NULL,
	`title` text NOT NULL,
	`cadence` text NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`plan_id`) REFERENCES `daily_plans`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`template_id`) REFERENCES `task_templates`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE INDEX `today_commitments_plan_position_index` ON `today_commitments` (`plan_id`,`position`);
--> statement-breakpoint
CREATE TABLE `today_commitment_steps` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`commitment_id` integer NOT NULL,
	`title` text NOT NULL,
	`position` integer NOT NULL,
	`completed_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`commitment_id`) REFERENCES `today_commitments`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `today_commitment_steps_commitment_position_index` ON `today_commitment_steps` (`commitment_id`,`position`);
