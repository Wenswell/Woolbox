ALTER TABLE `todo_steps` ADD `position` integer DEFAULT 0 NOT NULL;--> statement-breakpoint
UPDATE `todo_steps`
SET `position` = (
  SELECT COUNT(*) - 1
  FROM `todo_steps` AS `other`
  WHERE `other`.`task_id` = `todo_steps`.`task_id`
    AND (
      `other`.`created_at` < `todo_steps`.`created_at`
      OR (`other`.`created_at` = `todo_steps`.`created_at` AND `other`.`id` <= `todo_steps`.`id`)
    )
);--> statement-breakpoint
CREATE INDEX `todo_steps_task_position_index` ON `todo_steps` (`task_id`,`position`);
