/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.cfg;


/**
 *
 * @author Christian Beikov
 */
public class ConfigurationException extends RuntimeException{

    public ConfigurationException(Throwable cause) {
        super(cause);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException() {
    }
    
}
