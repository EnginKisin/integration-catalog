-- Existing PostgreSQL installations: apply once before deploying the payload-content backend.
-- Fresh installations can be created by the existing Hibernate ddl-auto=update setting.
BEGIN;

ALTER TABLE mapping_schemas
    ADD COLUMN IF NOT EXISTS format varchar(16) NOT NULL DEFAULT 'JSON',
    ADD COLUMN IF NOT EXISTS raw_content text,
    ADD COLUMN IF NOT EXISTS file_name varchar(255),
    ADD COLUMN IF NOT EXISTS content_type varchar(255),
    ADD COLUMN IF NOT EXISTS sample_file bytea,
    ADD COLUMN IF NOT EXISTS csv_delimiter varchar(255),
    ADD COLUMN IF NOT EXISTS csv_has_header boolean;

-- Hibernate's schema update does not reliably replace existing enum CHECK constraints.
-- Replace single-column format checks, leaving other integration constraints intact.
DO $$
DECLARE constraint_row record;
BEGIN
    FOR constraint_row IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND c.conkey = ARRAY[a.attnum]
        WHERE c.conrelid = 'integrations'::regclass AND c.contype = 'c'
          AND a.attname IN ('source_payload_format', 'destination_payload_format')
    LOOP
        EXECUTE format('ALTER TABLE integrations DROP CONSTRAINT %I', constraint_row.conname);
    END LOOP;
END $$;

ALTER TABLE integrations ADD CONSTRAINT integrations_source_payload_format_check
    CHECK (source_payload_format IN ('JSON', 'XML', 'IDOC', 'CSV', 'EXCEL'));
ALTER TABLE integrations ADD CONSTRAINT integrations_destination_payload_format_check
    CHECK (destination_payload_format IN ('JSON', 'XML', 'IDOC', 'CSV', 'EXCEL'));

COMMIT;
