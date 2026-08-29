package com.example.spendtracker.data.sms.model;

/**
 * Outcome status of the SMS parsing pipeline.
 *
 * <p>Each value indicates how far the pipeline progressed and why it stopped.
 */
public enum ParseStatus {

    /** All required fields were extracted and validated successfully. */
    SUCCESS,

    /** The message was parsed but some non-critical fields are missing or low-confidence. */
    PARTIAL_SUCCESS,

    /** The message is clearly not a financial transaction (e.g. balance enquiry, marketing). */
    NOT_TRANSACTION,

    /** The message was identified as promotional / marketing content. */
    PROMOTIONAL,

    /** The message looks transactional but validation failed (inconsistent or missing critical fields). */
    INVALID,

    /** The message appears transactional but no pattern could extract the required fields. */
    UNKNOWN_FORMAT,

    /** The transaction was identified as a duplicate of an already-stored transaction. */
    DUPLICATE,

    /** A transaction was detected but it represents a failed/declined payment — not persisted by default. */
    FAILED_TRANSACTION
}
