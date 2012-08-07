/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.importer;

import java.util.List;
import java.util.Map;

/**
 *
 * @author Christian Beikov
 */
public interface DataImporter {

    /**
     * Adds the given data provider to be used in the generation process.
     * 
     * @param provider The dataprovider to be added.
     * @throws GeneratorException Is thrown when a dataprovider is added which
     *                            provides entries for a class which could not be found.
     */
    public void add(DataProvider provider) throws DataImporterException;

    /**
     * Generates the objects for all dataproviders and puts the object into the
     * datastorage.
     * 
     * @return The List of the generated objects mapped to their dataproviders.
     * @throws GeneratorException 
     */
    public Map<DataProvider, List> generateObjects() throws DataImporterException;

    /**
     * Generates the objects for the specified class and their dependencies. The
     * dataprovider for the dependencies must be present.
     * 
     * @param clazz The class for which the object should be generated
     * @return The List of the generated objects mapped to their dataproviders.
     * @throws GeneratorException 
     */
    public Map<DataProvider, List> generateObjects(Class clazz) throws DataImporterException;

}
