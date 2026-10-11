ALTER TABLE reservations
    ADD COLUMN validated_proposal_path VARCHAR(1000) NULL;
ALTER TABLE reservations
    ADD COLUMN proposal_validation_legacy BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve prior decisions without fabricating approved documents. A legacy record
-- still needs an original proposal to satisfy the application approval prerequisite.
UPDATE reservations
SET proposal_validation_legacy = TRUE
WHERE proposal_validated_at IS NOT NULL AND proposal_validated_by IS NOT NULL;

ALTER TABLE reservations
    ADD CONSTRAINT ck_reservations_validated_document CHECK (
        validated_proposal_path IS NULL OR (
            CHAR_LENGTH(TRIM(validated_proposal_path)) > 0
            AND proposal_path IS NOT NULL AND CHAR_LENGTH(TRIM(proposal_path)) > 0
            AND proposal_validated_at IS NOT NULL AND proposal_validated_by IS NOT NULL
        )
    );
ALTER TABLE reservations
    ADD CONSTRAINT ck_reservations_validation_evidence CHECK (
        proposal_validated_at IS NULL OR proposal_validated_by IS NULL
        OR proposal_validation_legacy = TRUE OR validated_proposal_path IS NOT NULL
    );
