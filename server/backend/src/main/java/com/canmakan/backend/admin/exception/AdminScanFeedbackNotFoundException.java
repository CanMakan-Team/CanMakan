package com.canmakan.backend.admin.exception;

/** Signals that a requested scan feedback row does not exist. */
public class AdminScanFeedbackNotFoundException extends RuntimeException {

    public AdminScanFeedbackNotFoundException(Long feedbackId) {
        super("Scan feedback not found: " + feedbackId);
    }
}
