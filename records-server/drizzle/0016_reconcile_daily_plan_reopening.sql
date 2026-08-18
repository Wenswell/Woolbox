UPDATE `daily_plans`
SET
	`state` = CASE WHEN `first_move_completed_at` IS NULL THEN 'READY' ELSE 'ACTIVE' END,
	`completion_reason` = NULL,
	`current_task_id` = CASE
		WHEN `first_move_completed_at` IS NULL THEN NULL
		WHEN EXISTS (
			SELECT 1
			FROM `todo_steps` AS `step`
			JOIN `todo_tasks` AS `task` ON `task`.`id` = `step`.`task_id`
			WHERE `step`.`task_id` = `daily_plans`.`primary_task_id`
				AND NOT EXISTS (
					SELECT 1 FROM `todo_step_completions` AS `completion`
					WHERE `completion`.`step_id` = `step`.`id`
						AND (
							(`task`.`repeat_mode` = 'ONCE' AND `completion`.`scope` = 'ONCE' AND `completion`.`local_date` IS NULL)
							OR (`task`.`repeat_mode` = 'DAILY' AND `completion`.`scope` = 'DAILY' AND `completion`.`local_date` = `daily_plans`.`local_date`)
						)
				)
		) THEN `primary_task_id`
		ELSE `secondary_task_id`
	END,
	`next_review_at` = CASE
		WHEN `first_move_completed_at` IS NULL THEN NULL
		ELSE CAST(strftime('%s', 'now') AS INTEGER) * 1000 + 3600000
	END,
	`revision` = `revision` + 1,
	`updated_at` = CAST(strftime('%s', 'now') AS INTEGER) * 1000
WHERE `state` = 'ENOUGH'
	AND `completion_reason` = 'COMMITMENTS'
	AND EXISTS (
		SELECT 1
		FROM `todo_steps` AS `step`
		JOIN `todo_tasks` AS `task` ON `task`.`id` = `step`.`task_id`
		WHERE (`step`.`task_id` = `daily_plans`.`primary_task_id` OR `step`.`task_id` = `daily_plans`.`secondary_task_id`)
			AND NOT EXISTS (
				SELECT 1 FROM `todo_step_completions` AS `completion`
				WHERE `completion`.`step_id` = `step`.`id`
					AND (
						(`task`.`repeat_mode` = 'ONCE' AND `completion`.`scope` = 'ONCE' AND `completion`.`local_date` IS NULL)
						OR (`task`.`repeat_mode` = 'DAILY' AND `completion`.`scope` = 'DAILY' AND `completion`.`local_date` = `daily_plans`.`local_date`)
					)
			)
	);
