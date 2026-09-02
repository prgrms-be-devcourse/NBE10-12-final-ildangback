-- ===== challenges : weekdays -> days_of_week =====
ALTER TABLE challenges RENAME COLUMN weekdays TO days_of_week;

UPDATE challenges SET frequency_type = 'DAYS_OF_WEEK' WHERE frequency_type = 'WEEKDAYS';
