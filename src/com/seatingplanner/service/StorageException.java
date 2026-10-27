package com.seatingplanner.service;

/** A file could not be read, parsed or written. The message is suitable for showing to the user. */
public class StorageException extends Exception {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
