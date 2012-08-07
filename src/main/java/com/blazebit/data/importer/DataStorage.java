/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.importer;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * A datastorage is long living and can provide some information for managed
 * classes but also persist objects.
 *
 * @author Christian Beikov
 */
public interface DataStorage {

    /**
     * Returns the object of the type <code>clazz</code> with the given id.
     * 
     * @param clazz The type of the object which should be returned.
     * @param id The id of the object which should be returned.
     * @return The found object or null.
     */
    public Serializable getById(Class clazz, Serializable id);

    /**
     * Returns the object of the type <code>clazz</code> which matches the condition
     * <code>object.fieldName == fieldValue</code>.
     * 
     * @param clazz The type of the object which should be returned.
     * @param fieldName The name of the field which should be used for comparaison.
     * @param fieldValue The value which a field has to have to fullfill the condition.
     * @return The object or null.
     */
    public Serializable getByField(Class clazz, String fieldName, Serializable fieldValue);

    public Serializable getByFields(Class clazz, Map<String, Serializable> valueMap);
    
    public List<Serializable> getListByField(Class clazz, String fieldName, Serializable fieldValue);

    public List<Serializable> getListByFields(Class clazz, Map<String, Serializable> valueMap);

    /**
     * Returns the class type of the identifier of a class within the datastorage.
     * 
     * @param clazz The class of which the identifier type should be returned.
     * @return The class of the identifier.
     */
    public Class getIdentifierType(Class clazz);

    /**
     * Persists the given object in the datastorage.
     * 
     * @param object The object to persist.
     */
    public Serializable saveObject(Serializable object);

    /**
     * Closes the datastorage
     * 
     */
    public void close();
    
    /**
     * Manual flush the datastorage
     */
    public void flush();
    
    public boolean isManaged(Class clazz);
}
