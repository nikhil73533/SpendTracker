package com.example.spendtracker.domain.model;

/** Result of a user-approved statement import committed by the transaction repository. */
public class BulkImportResult {
    private final int requested;
    private final int imported;
    private final int duplicates;
    private final String error;

    public BulkImportResult(int requested, int imported, int duplicates, String error) {
        this.requested = requested;
        this.imported = imported;
        this.duplicates = duplicates;
        this.error = error;
    }

    public int getRequested() { return requested; }
    public int getImported() { return imported; }
    public int getDuplicates() { return duplicates; }
    public String getError() { return error; }
    public boolean isSuccess() { return error == null; }
}
