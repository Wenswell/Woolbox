CREATE TABLE `today_tasks` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`local_date` text NOT NULL,
	`template_id` integer,
	`source_task_id` integer,
	`title` text NOT NULL,
	`position` integer NOT NULL,
	`completed_at` integer,
	`revision` integer NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`template_id`) REFERENCES `task_templates`(`id`) ON UPDATE no action ON DELETE set null,
	FOREIGN KEY (`source_task_id`) REFERENCES `today_tasks`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE INDEX `today_tasks_date_position_index` ON `today_tasks` (`local_date`,`position`);
--> statement-breakpoint
CREATE UNIQUE INDEX `today_tasks_date_source_unique` ON `today_tasks` (`local_date`,`source_task_id`);
--> statement-breakpoint
CREATE TABLE `today_task_steps` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`task_id` integer NOT NULL,
	`title` text NOT NULL,
	`position` integer NOT NULL,
	`completed_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`task_id`) REFERENCES `today_tasks`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `today_task_steps_task_position_index` ON `today_task_steps` (`task_id`,`position`);
--> statement-breakpoint
INSERT INTO `today_tasks` (`id`, `local_date`, `template_id`, `source_task_id`, `title`, `position`, `completed_at`, `revision`, `created_at`, `updated_at`)
SELECT commitment.`id`, plan.`local_date`, commitment.`template_id`, NULL, commitment.`title`, commitment.`position`,
	CASE
		WHEN EXISTS (SELECT 1 FROM `today_commitment_steps` step WHERE step.`commitment_id` = commitment.`id`)
			AND NOT EXISTS (SELECT 1 FROM `today_commitment_steps` step WHERE step.`commitment_id` = commitment.`id` AND step.`completed_at` IS NULL)
		THEN (SELECT MAX(step.`completed_at`) FROM `today_commitment_steps` step WHERE step.`commitment_id` = commitment.`id`)
		ELSE NULL
	END,
	0, commitment.`created_at`, commitment.`updated_at`
FROM `today_commitments` commitment
JOIN `daily_plans` plan ON plan.`id` = commitment.`plan_id`;
--> statement-breakpoint
INSERT INTO `today_task_steps` (`id`, `task_id`, `title`, `position`, `completed_at`, `created_at`, `updated_at`)
SELECT `id`, `commitment_id`, `title`, `position`, `completed_at`, `created_at`, `updated_at`
FROM `today_commitment_steps`;
--> statement-breakpoint
CREATE TABLE `daily_focus` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`local_date` text NOT NULL,
	`first_move` text NOT NULL,
	`first_move_completed_at` integer,
	`primary_task_id` integer NOT NULL,
	`secondary_task_id` integer,
	`current_task_id` integer,
	`state` text NOT NULL,
	`pause_reason` text,
	`completed_rounds` integer NOT NULL,
	`next_review_at` integer,
	`revision` integer NOT NULL,
	`ended_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`primary_task_id`) REFERENCES `today_tasks`(`id`) ON UPDATE no action ON DELETE restrict,
	FOREIGN KEY (`secondary_task_id`) REFERENCES `today_tasks`(`id`) ON UPDATE no action ON DELETE restrict,
	FOREIGN KEY (`current_task_id`) REFERENCES `today_tasks`(`id`) ON UPDATE no action ON DELETE restrict
);
--> statement-breakpoint
CREATE UNIQUE INDEX `daily_focus_local_date_unique` ON `daily_focus` (`local_date`);
--> statement-breakpoint
INSERT INTO `daily_focus` (`id`, `local_date`, `first_move`, `first_move_completed_at`, `primary_task_id`, `secondary_task_id`, `current_task_id`, `state`, `pause_reason`, `completed_rounds`, `next_review_at`, `revision`, `ended_at`, `created_at`, `updated_at`)
SELECT plan.`id`, plan.`local_date`, plan.`first_move`, plan.`first_move_completed_at`,
	COALESCE(
		CASE WHEN current_task.`role` = 'ADDITIONAL' THEN current_task.`id` END,
		primary_task.`id`,
		current_task.`id`,
		(SELECT MIN(any_task.`id`) FROM `today_commitments` any_task WHERE any_task.`plan_id` = plan.`id`)
	),
	CASE
		WHEN current_task.`role` = 'ADDITIONAL' THEN primary_task.`id`
		ELSE secondary_task.`id`
	END,
	plan.`current_commitment_id`, plan.`state`,
	CASE WHEN plan.`pause_reason` = 'COMMITMENTS_COMPLETED' THEN 'FOCUS_CLEARED' ELSE plan.`pause_reason` END,
	plan.`completed_rounds`, plan.`next_review_at`, plan.`revision`, plan.`ended_at`, plan.`created_at`, plan.`updated_at`
FROM `daily_plans` plan
LEFT JOIN `today_commitments` primary_task ON primary_task.`plan_id` = plan.`id` AND primary_task.`role` = 'PRIMARY'
LEFT JOIN `today_commitments` secondary_task ON secondary_task.`plan_id` = plan.`id` AND secondary_task.`role` = 'SECONDARY'
LEFT JOIN `today_commitments` current_task ON current_task.`id` = plan.`current_commitment_id`;
--> statement-breakpoint
UPDATE `record_audit_logs`
SET `before_json` = CASE WHEN `before_json` IS NULL THEN NULL ELSE json_remove(json_set(`before_json`, '$.localDate', (SELECT task.`local_date` FROM `today_tasks` task WHERE task.`id` = `record_audit_logs`.`entity_id`), '$.sourceTaskId', NULL), '$.planId', '$.role', '$.cadence') END,
	`after_json` = CASE WHEN `after_json` IS NULL THEN NULL ELSE json_remove(json_set(`after_json`, '$.localDate', (SELECT task.`local_date` FROM `today_tasks` task WHERE task.`id` = `record_audit_logs`.`entity_id`), '$.sourceTaskId', NULL), '$.planId', '$.role', '$.cadence') END
WHERE `entity_type` = 'COMMITMENT';
--> statement-breakpoint
UPDATE `record_audit_logs`
SET `before_json` = CASE WHEN `before_json` IS NULL THEN NULL ELSE json_remove(json_set(`before_json`, '$.taskId', json_extract(`before_json`, '$.commitmentId'), '$.taskTitle', json_extract(`before_json`, '$.commitmentTitle')), '$.commitmentId', '$.commitmentTitle') END,
	`after_json` = CASE WHEN `after_json` IS NULL THEN NULL ELSE json_remove(json_set(`after_json`, '$.taskId', json_extract(`after_json`, '$.commitmentId'), '$.taskTitle', json_extract(`after_json`, '$.commitmentTitle')), '$.commitmentId', '$.commitmentTitle') END
WHERE `entity_type` = 'COMMITMENT_STEP';
--> statement-breakpoint
UPDATE `record_audit_logs`
SET `before_json` = CASE WHEN `before_json` IS NULL THEN NULL ELSE json_remove(json_set(`before_json`, '$.primaryTaskId', (SELECT focus.`primary_task_id` FROM `daily_focus` focus WHERE focus.`id` = `record_audit_logs`.`entity_id`), '$.secondaryTaskId', (SELECT focus.`secondary_task_id` FROM `daily_focus` focus WHERE focus.`id` = `record_audit_logs`.`entity_id`), '$.currentTaskId', json_extract(`before_json`, '$.currentCommitmentId')), '$.currentCommitmentId', '$.commitmentIds') END,
	`after_json` = CASE WHEN `after_json` IS NULL THEN NULL ELSE json_remove(json_set(`after_json`, '$.primaryTaskId', (SELECT focus.`primary_task_id` FROM `daily_focus` focus WHERE focus.`id` = `record_audit_logs`.`entity_id`), '$.secondaryTaskId', (SELECT focus.`secondary_task_id` FROM `daily_focus` focus WHERE focus.`id` = `record_audit_logs`.`entity_id`), '$.currentTaskId', json_extract(`after_json`, '$.currentCommitmentId')), '$.currentCommitmentId', '$.commitmentIds') END
WHERE `entity_type` = 'DAILY_PLAN';
--> statement-breakpoint
UPDATE `record_audit_logs`
SET `entity_type` = CASE `entity_type`
	WHEN 'COMMITMENT' THEN 'TODAY_TASK'
	WHEN 'COMMITMENT_STEP' THEN 'TODAY_TASK_STEP'
	WHEN 'DAILY_PLAN' THEN 'FOCUS'
	ELSE `entity_type`
END,
`action` = CASE `action`
	WHEN 'COMMITMENT_CREATED' THEN 'TODAY_TASK_CREATED'
	WHEN 'COMMITMENT_COMPLETED' THEN 'TODAY_TASK_COMPLETED'
	WHEN 'COMMITMENT_REOPENED' THEN 'TODAY_TASK_REOPENED'
	WHEN 'COMMITMENT_STEP_COMPLETED' THEN 'TODAY_TASK_STEP_COMPLETED'
	WHEN 'COMMITMENT_STEP_REOPENED' THEN 'TODAY_TASK_STEP_REOPENED'
	WHEN 'DAILY_PLAN_CREATED' THEN 'FOCUS_CREATED'
	WHEN 'DAILY_PLAN_UPDATED' THEN 'FOCUS_UPDATED'
	WHEN 'DAILY_PLAN_ENDED' THEN 'FOCUS_ENDED'
	ELSE `action`
END
WHERE `entity_type` IN ('COMMITMENT', 'COMMITMENT_STEP', 'DAILY_PLAN');
--> statement-breakpoint
DROP TABLE `today_commitment_steps`;
--> statement-breakpoint
DROP TABLE `today_commitments`;
--> statement-breakpoint
DROP TABLE `daily_plans`;
