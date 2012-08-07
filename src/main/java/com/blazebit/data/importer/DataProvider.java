/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.importer;

import java.util.Collection;
import java.util.Map;

/**
 * A dataprovider provides entries and field names for a specific class type.
 * Field values for an entry are organized in arrays. The indizes of the values
 * must match the indizes of the field names for which the values are intended.
 *
 * @author Christian Beikov
 */
public interface DataProvider {
    /**
     * Returns the simple class name of the entity for which this dataprovider
     * provides data entries.
     * 
     * @return The simple class name
     */
    public String getSimpleClassName();
    /**
     * Returns the names of the fields of the class for which this dataprovider
     * provides data entries.
     * This method is supposed to be called once. The entries provided by this
     * dataprovider must have the same array length as this array.
     * 
     * @return The field names
     */
    public String[] getFieldNames();
    /**
     * Returns a DataProvider entry which represents a single object of the class type
     * for which this dataprovider is for. The values of the simplefields can be mapped directly to
     * their correspoinding fields in the object. Complex fields are nested data structures
     * which may contain also collection elements. For collections there will be one
     * entry which will have the null key.     * 
     * 
     * @return The entry with nested value structure.
     */
    public DataProvider.Entry next();
    
    /**
     * Represents an object of the class type for which this dataprovider is for
     * in a generic form. This generic form can be used for easily fill objects
     * with data.
     */
    interface Entry{
        /**
         * Simple field to value mapping. The key is the fieldname and the value
         * is the value for the field.
         * 
         * @return Simplefield map
         */
        Map<String, String> getSimpleFields();
        /**
         * Similar to simplefield, but the value is a nested structure.
         * There may be only three types of results:
         *  - empty map
         *  - 1-n entries with keys != null (=> Map or Object)
         *  - 1 entry with key == null (=> Collection)
         * 
         * @return Complexfield map
         */
        Map<String, Collection<DataProvider.Entry>> getComplexFields();
    }
}
