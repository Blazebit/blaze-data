/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.importer;

/**
 * This exception can be thrown when a value for a field, within the object
 * generation process, could not be parsed correctly.
 *
 * @author Christian Beikov
 */
class DataParseException extends DataImporterException {

    public DataParseException() {
    }

    public DataParseException(String message) {
        super(message);
    }

    public DataParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataParseException(Throwable cause) {
        super(cause);
    }
    
}
