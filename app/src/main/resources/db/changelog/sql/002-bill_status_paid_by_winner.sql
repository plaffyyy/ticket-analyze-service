ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_status_check;

ALTER TABLE bills
    ADD CONSTRAINT bills_status_check
        CHECK (status IN ('open', 'paid_by_winner', 'settled', 'processing_ocr'));
