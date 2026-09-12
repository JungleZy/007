-- T11: simulation page/result identity. Run after the InnoDB migration, with every
-- application writer stopped and a verified full backup. MySQL DDL is not transactional.
-- current/base snapshots contain no INSERT/REPLACE rows for these two tables; both
-- nevertheless allow NULL business keys, and neither has the required unique key.
--
-- Deliberately fail closed on ALL duplicate keys, including byte-identical copies.
-- Export/backup affected rows first. Identical copies may be manually consolidated;
-- conflicting key/value contents require an authoritative training record or an
-- explicit human decision. UUID order, MAX(id), firstResult and timestamps not
-- present in this schema are NOT evidence. NULL keys also require manual attribution.
-- No row is deleted, rewritten or arbitrarily selected by this migration.
-- Re-run only after that adjudication; valid existing rows are preserved verbatim.
-- Never run mysql with --force. The guard and all ALTERs share one procedure so a
-- failed preflight cannot fall through into partial constraint installation.

SELECT 'simulation_router_room_page' AS table_name, COUNT(*) AS row_count,
       COALESCE(SUM(room_id IS NULL OR page_number IS NULL OR sort IS NULL), 0) AS null_keys
FROM simulation_router_room_page;
SELECT 'simulation_router_room_page_value' AS table_name, COUNT(*) AS row_count,
       COALESCE(SUM(room_id IS NULL OR user_id IS NULL OR page_number IS NULL), 0) AS null_keys
FROM simulation_router_room_page_value;
SELECT COUNT(*) AS duplicate_page_keys FROM (
    SELECT room_id, page_number, sort FROM simulation_router_room_page
    GROUP BY room_id, page_number, sort HAVING COUNT(*) > 1
) duplicates;
SELECT COUNT(*) AS duplicate_answer_keys FROM (
    SELECT room_id, user_id, page_number FROM simulation_router_room_page_value
    GROUP BY room_id, user_id, page_number HAVING COUNT(*) > 1
) duplicates;

DROP PROCEDURE IF EXISTS migrate_simulation_page_identity;
DELIMITER $$
CREATE PROCEDURE migrate_simulation_page_identity()
BEGIN
    DECLARE finished boolean DEFAULT false;
    DECLARE target_table varchar(64);
    DECLARE target_column varchar(64);
    DECLARE original_type longtext CHARACTER SET utf8mb4;
    DECLARE original_charset varchar(64);
    DECLARE original_collation varchar(64);
    DECLARE original_nullable varchar(3);
    DECLARE original_default longtext CHARACTER SET utf8mb4;
    DECLARE original_extra varchar(256);
    DECLARE original_comment text CHARACTER SET utf8mb4;
    DECLARE original_generation longtext CHARACTER SET utf8mb4;
    DECLARE quoted_default longtext CHARACTER SET utf8mb4;
    DECLARE quoted_comment longtext CHARACTER SET utf8mb4;
    DECLARE column_definition longtext CHARACTER SET utf8mb4;
    DECLARE columns_to_change CURSOR FOR
        SELECT table_name, column_name, column_type, character_set_name, collation_name,
               is_nullable, column_default, extra, column_comment, generation_expression
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND ((table_name = 'simulation_router_room_page'
                AND column_name IN ('room_id', 'page_number', 'sort') AND is_nullable = 'YES')
            OR (table_name = 'simulation_router_room_page_value'
                AND column_name IN ('room_id', 'user_id', 'page_number') AND is_nullable = 'YES')
            OR (table_name = 'simulation_router_room_page_value'
                AND column_name = 'value' AND data_type <> 'longtext'))
        ORDER BY table_name, ordinal_position;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = true;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE()
                 AND table_name IN ('simulation_router_room', 'simulation_router_room_page',
                                    'simulation_router_room_page_value', 'simulation_router_room_user')
                 AND engine <> 'InnoDB') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'T11 requires InnoDB parent and child tables';
    END IF;
    IF EXISTS (SELECT 1 FROM simulation_router_room_page
               WHERE room_id IS NULL OR page_number IS NULL OR sort IS NULL)
       OR EXISTS (SELECT 1 FROM simulation_router_room_page_value
                  WHERE room_id IS NULL OR user_id IS NULL OR page_number IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'T11 NULL keys: back up and resolve attribution before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM simulation_router_room_page
               GROUP BY room_id, page_number, sort HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM simulation_router_room_page_value
                  GROUP BY room_id, user_id, page_number HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'T11 duplicate keys: back up and adjudicate; no automatic winner';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'simulation_router_room_page'
                 AND index_name = 'uk_simulation_page_room_page_sort'
               GROUP BY index_name
               HAVING MAX(non_unique) <> 0 OR COUNT(*) <> 3 OR COUNT(sub_part) <> 0
                  OR GROUP_CONCAT(column_name ORDER BY seq_in_index) <> 'room_id,page_number,sort')
       OR EXISTS (SELECT 1 FROM information_schema.statistics
                  WHERE table_schema = DATABASE() AND table_name = 'simulation_router_room_page_value'
                    AND index_name = 'uk_simulation_value_room_user_page'
                  GROUP BY index_name
                  HAVING MAX(non_unique) <> 0 OR COUNT(*) <> 3 OR COUNT(sub_part) <> 0
                     OR GROUP_CONCAT(column_name ORDER BY seq_in_index) <> 'room_id,user_id,page_number') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'T11 index name collision: inspect existing constraint definition';
    END IF;

    -- MODIFY does not inherit column-level charset/collation (or the original
    -- numeric type). Reconstruct only changed columns from their actual metadata.
    -- In particular, never rewrite an already-LONGTEXT value column.
    OPEN columns_to_change;
    alter_columns: LOOP
        FETCH columns_to_change INTO target_table, target_column, original_type,
            original_charset, original_collation, original_nullable, original_default,
            original_extra, original_comment, original_generation;
        IF finished THEN
            LEAVE alter_columns;
        END IF;
        SET quoted_default = CONCAT('CONVERT(0x', HEX(original_default), ' USING utf8mb4)');
        SET quoted_comment = IF(FIND_IN_SET('NO_BACKSLASH_ESCAPES', @@SESSION.sql_mode),
            CONCAT('''', REPLACE(original_comment, '''', ''''''), ''''), QUOTE(original_comment));
        SET column_definition = CONCAT('`', target_column, '` ',
            IF(target_column = 'value', 'longtext', original_type),
            IF(original_charset IS NULL, '', CONCAT(' CHARACTER SET `', original_charset,
                '` COLLATE `', original_collation, '`')),
            IF(original_generation = '', '', CONCAT(' GENERATED ALWAYS AS (', original_generation,
                ') ', IF(LOCATE('STORED', original_extra) > 0, 'STORED', 'VIRTUAL'))),
            IF(target_column = 'value' AND original_nullable = 'YES', ' NULL', ' NOT NULL'),
            IF(original_default IS NULL OR original_generation <> '', '',
                CONCAT(' DEFAULT (', IF(LOCATE('DEFAULT_GENERATED', original_extra) > 0,
                    original_default, quoted_default), ')')),
            IF(original_generation = '', CONCAT(' ', REPLACE(original_extra, 'DEFAULT_GENERATED', '')),
                IF(LOCATE('INVISIBLE', original_extra) > 0, ' INVISIBLE', '')),
            ' COMMENT ', quoted_comment);
        SET @t11_column_ddl = CONCAT('ALTER TABLE `', target_table, '` MODIFY COLUMN ', column_definition);
        PREPARE t11_column_statement FROM @t11_column_ddl;
        EXECUTE t11_column_statement;
        DEALLOCATE PREPARE t11_column_statement;
    END LOOP;
    CLOSE columns_to_change;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'simulation_router_room_page'
                     AND index_name = 'uk_simulation_page_room_page_sort') THEN
        ALTER TABLE simulation_router_room_page
            ADD CONSTRAINT uk_simulation_page_room_page_sort UNIQUE (room_id, page_number, sort);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'simulation_router_room_page_value'
                     AND index_name = 'uk_simulation_value_room_user_page') THEN
        ALTER TABLE simulation_router_room_page_value
            ADD CONSTRAINT uk_simulation_value_room_user_page UNIQUE (room_id, user_id, page_number);
    END IF;
END$$
DELIMITER ;
CALL migrate_simulation_page_identity();
DROP PROCEDURE migrate_simulation_page_identity;
