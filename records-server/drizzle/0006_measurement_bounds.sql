ALTER TABLE `record_items` ADD `min_value` real;--> statement-breakpoint
ALTER TABLE `record_items` ADD `max_value` real;--> statement-breakpoint
DELETE FROM `record_entries`
WHERE `item_id` IN (SELECT `id` FROM `record_items` WHERE `mode` = 'MEASUREMENT');--> statement-breakpoint
DELETE FROM `record_items` WHERE `mode` = 'MEASUREMENT';--> statement-breakpoint
DELETE FROM `todo_tasks`
WHERE `id` NOT IN (SELECT DISTINCT `task_id` FROM `todo_steps`);
