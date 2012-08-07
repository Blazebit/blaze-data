/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.importer;

/**
 * Base exception for error which occur within the generation of objects.
 * There can be various reasons for this exception to be thrown, because other 
 * exceptions than this thrown should be encapsulated within a DataImporterException.
 *
 * @author Christian Beikov
 */
class DataImporterException extends Exception{

    public DataImporterException(Throwable cause) {
        super(cause);
    }

    public DataImporterException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataImporterException(String message) {
        super(message);
    }

    public DataImporterException() {
    }
    
}
