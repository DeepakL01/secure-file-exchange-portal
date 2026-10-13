package com.college.securefile.exception;

public class DownloadForbiddenException extends RuntimeException {
    public DownloadForbiddenException(String message) {
        super(message);
    }
}
