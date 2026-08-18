ALTER TABLE `daily_plans` ADD `completion_reason` text;--> statement-breakpoint
UPDATE `daily_plans`
SET `completion_reason` = CASE
	WHEN `completed_rounds` >= 4 THEN 'ROUNDS'
	ELSE 'COMMITMENTS'
END
WHERE `state` = 'ENOUGH';
