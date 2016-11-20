CREATE TABLE IF NOT EXISTS `Sources` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `source` VARCHAR(250) NOT NULL UNIQUE,
  PRIMARY KEY (`id`),
  UNIQUE (`source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `Results` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `result` VARCHAR(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO `Results` 
  (`id`, `result`) 
VALUES
  (default, 'SAT'),
  (default, 'UNSAT'),
  (default, 'TIMEOUT');

CREATE TABLE IF NOT EXISTS `Bands` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `band` VARCHAR(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`band`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO `Bands` 
  (`id`, `band`) 
VALUES
  (default, 'OFF'),
  (default, 'LVHF'),
  (default, 'HVHF'),
  (default, 'UHF');

CREATE TABLE IF NOT EXISTS `ProblemTypes` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `problem_type` VARCHAR(250) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`problem_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO `ProblemTypes` 
  (`id`, `problem_type`) 
VALUES
  (default, 'INITIAL_PLACEMENT'),
  (default, 'PROVISIONAL_WINNER_CHECK'),
  (default, 'BID_PROCESSING_HOME_BAND_FEASIBLE'),
  (default, 'BID_PROCESSING_MOVE_FEASIBLE'),
  (default, 'BID_STATUS_UPDATING_HOME_BAND_FEASIBLE'),
  (default, 'UHF_CACHE_PREFILL'),
  (default, 'NOT_NEEDED_UPDATE');

CREATE TABLE IF NOT EXISTS `Interferences` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `interference` VARCHAR(250) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`interference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO `Interferences` 
  (`id`, `interference`) 
VALUES
  (default, 'nov2015');

CREATE TABLE IF NOT EXISTS `Assignments` (
  `id` INT NOT NULL,
  `source_id` INT NOT NULL,
  `assignment` text NOT NULL,
  PRIMARY KEY (`source_id`, `id`),
  INDEX `source_id_index` (`source_id`),
  CONSTRAINT `fk_source` FOREIGN KEY (`source_id`) REFERENCES `Sources` (`id`)
) ENGINE=InnoDB ROW_FORMAT=COMPRESSED DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `Problems` (
  `id` INT NOT NULL,
  `stations` text NOT NULL,
  `round` INT NOT NULL,
  `band_id` INT NOT NULL,
  `result_id` INT NOT NULL,
  `cputime` DOUBLE NOT NULL,
  `walltime` DOUBLE NOT NULL,
  `is_greedy` BIT(1) NOT NULL,
  `is_cached` BIT(1) NOT NULL,
  `problem_type_id` INT NOT NULL,
  `target_station` INT NULL,
  `max_channel` INT NOT NULL,
  `interference_id` INT NOT NULL,
  `name` VARCHAR(100) NULL,
  `assignment_id` INT,
  `source_id` INT NOT NULL,
  PRIMARY KEY (`source_id`, `id`),
  INDEX `source_id_index` (`source_id`),
  CONSTRAINT `fk_band_id` FOREIGN KEY (`band_id`) REFERENCES `Bands` (`id`),
  CONSTRAINT `fk_result_id` FOREIGN KEY (`result_id`) REFERENCES `Results` (`id`),
  CONSTRAINT `fk_problem_type_id` FOREIGN KEY (`problem_type_id`) REFERENCES `ProblemTypes` (`id`),
  CONSTRAINT `fk_interference_id` FOREIGN KEY (`interference_id`) REFERENCES `Interferences` (`id`),
  CONSTRAINT `fk_previous_assignment_id` FOREIGN KEY (`source_id`, `assignment_id`) REFERENCES `Assignments` (`source_id`, `id`)
) ENGINE=InnoDB ROW_FORMAT=COMPRESSED DEFAULT CHARSET=utf8;

# Query to get a specific problem
SELECT `Problems`.`stations`, `Problems`.`name`, `Problems`.`max_channel`, `Problems`.`interference`, `Assignments`.`assignment`
FROM `Problems`
INNER JOIN `Assignments` ON `Problems`.`assignment_id` = `Assignments`.`id` AND `Problems`.`source` = `Assignments`.`source`
WHERE `Problems`.`id` = 100 AND `Problems`.`source` = 'Generator_9';

# Query to get relevant problems
SELECT `Problems`.`id`, `Problems`.`source` FROM Problems WHERE `Problems`.`cached` = 0 AND `Problems`.`greedy` = 0 AND `Problems`.`band` = 'UHF';

# Query to get a specific problem


-- SELECT `Generator_0_Problems`.`stations`, `Generator_0_Problems`.`name`, `Generator_0_Previous_Assignments`.`assignment`
-- FROM `Generator_0_Problems` INNER JOIN `Generator_0_Previous_Assignments`
-- ON `Generator_0_Problems`.`previous_assignment_id` = `Generator_0_Previous_Assignments`.`id` 
-- WHERE `Generator_0_Problems`.`cached` = 0
-- AND `Generator_0_Problems`.`greedy` = 0
-- AND `Generator_0_Problems`.`band` = 'UHF';