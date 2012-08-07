/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.importer;

/**
 * This exception can be thrown when a dependency could not be resolved
 * because the data for a class on which an action depends on could not be
 * processed.
 *
 * @author Christian Beikov
 */
class DataDependencyException extends DataImporterException{

    public DataDependencyException() {
    }

    public DataDependencyException(String message) {
        super(message);
    }

    public DataDependencyException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataDependencyException(Throwable cause) {
        super(cause);
    }
    
}
