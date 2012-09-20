/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.importer;

import com.blazebit.reflection.ExpressionUtils;
import com.blazebit.reflection.LazyGetterMethod;
import com.blazebit.reflection.LazySetterMethod;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;

import com.blazebit.data.cfg.Configuration;
import com.blazebit.data.cfg.DataLookup;
import com.blazebit.data.cfg.DataLookupBy;
import com.blazebit.data.cfg.DataProperty;
import com.blazebit.data.importer.DataProvider.Entry;
import com.blazebit.reflection.ReflectionUtils;
import com.blazebit.text.FormatUtils;
import java.util.logging.Logger;

/**
 *
 * @author Christian Beikov
 */
public class GenericDataImporter implements DataImporter {

    private static final Logger log = Logger.getLogger(GenericDataImporter.class.getName());
    private static final boolean DEBUG = false;
    private Configuration config;
    private DataStorage storage;
    private Map<Class, DataProvider> providers = new LinkedHashMap<Class, DataProvider>();
    private Map<DataProvider, List> generationDone = new HashMap<DataProvider, List>();
    // Dataparsing-specific fields
    private DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
    private DateFormat calendarFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private Stack thisObjectStack = new Stack();
    //This tow Properties are used for Expression like this.id
    private Map<Class, List<LazySetterMethod>> lazyActions = new HashMap<Class, List<LazySetterMethod>>();
    private Class currentGenerateObject2Class = null;

    public GenericDataImporter(DataStorage storage, Configuration config) {
        if (storage == null || config == null) {
            throw new NullPointerException();
        }
        this.storage = storage;
        this.config = config;


        if (config.getDateFormat() != null) {
            this.dateFormat = config.getDateFormat();
        }
        if (config.getCalendarFormat() != null) {
            this.calendarFormat = config.getCalendarFormat();
        }
    }

    @Override
    public void add(DataProvider provider) throws DataImporterException {
        try {
            providers.put(Class.forName(config.getPackageName() + "." + provider.getSimpleClassName()), provider);
        } catch (ClassNotFoundException ex) {
            throw new DataImporterException(ex);
        }
    }

    @Override
    public Map<DataProvider, List> generateObjects() throws DataImporterException {
        for (Map.Entry<Class, DataProvider> entry : providers.entrySet()) {
            if (!generationDone.containsKey(entry.getValue())) {
                this.generateObjects(entry.getKey());
            }
        }


        return generationDone;
    }

    @Override
    public Map<DataProvider, List> generateObjects(Class clazz) throws DataImporterException {
        try {
            generateObjects2(clazz);
        } catch (Throwable t) {
            throw new DataImporterException("Could not generate objects for class " + clazz, t);
        }
        return generationDone;
    }

    /**
     * This is the main method which generates the objects and recursively calls
     * itself when dependencies exist.
     * 
     * @param clazz The class of the objects which will be generated.
     * @return true if the generation was successfull, false if dataprovider was not found
     * @throws GeneratorException is thrown when an error occurred.
     */
    private boolean generateObjects2(Class clazz) throws DataImporterException {
        if (!storage.isManaged(clazz)) {
            return true;
        }
        DataProvider provider = providers.get(clazz);
        Map<String, Object> dpMap = (Map) config.getDataProperties(clazz);

        for (String dependsOn : config.getDefinedDependencies(clazz)) {
            try {
                currentGenerateObject2Class = clazz;
                generateObjects2(Class.forName(config.getPackageName() + "." + dependsOn.trim()));
            } catch (ClassNotFoundException ex) {
                log.log(Level.SEVERE, ex.getMessage(), ex);
                throw new DataImporterException(ex);
            }
        }



        if (provider == null) {
            throw new NullPointerException("Unknown data provider for class " + clazz);
        } else if (generationDone.containsKey(provider)) {
            return true;
        }

        DataProvider.Entry entry = null;

        while ((entry = provider.next()) != null) {
            Object toGenerate = null;


            try {
                toGenerate = clazz.newInstance();
                thisObjectStack.push(toGenerate);
                populateFieldValues(toGenerate, entry, clazz, dpMap);

                toGenerate = storage.saveObject((Serializable) toGenerate);
            } catch (Throwable ex) {
                log.log(Level.SEVERE, ex.getMessage(), ex);
                throw new DataImporterException(ex);
            }
            List<LazySetterMethod> list = lazyActions.get(clazz);
            if (list != null) {

                for (LazySetterMethod action : list) {
                    try {
                        action.invoke();
                    } catch (InvocationTargetException ex) {
                        java.util.logging.Logger.getLogger(GenericDataImporter.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IllegalAccessException ex) {
                        java.util.logging.Logger.getLogger(GenericDataImporter.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    toGenerate = storage.saveObject((Serializable) toGenerate);
                }

            }

            if (generationDone.get(provider) == null) {
                generationDone.put(provider, new ArrayList());
            }

            thisObjectStack.pop();

            if (DEBUG) {
                generationDone.get(provider).add(toGenerate);
            }
        }
        return true;
    }

    /**
     * This method set the populateObject with Objects wich 
     * are generate by the Entry und decide which art of Object has to be 
     * generated. 
     * @param populateObject The object wich should be set with the values of the
     * Entry
     * @param entry The values for the Proerties of populateObject
     * @param clazz The Class of populateObject
     * @param dpMap A Configuration Map
     * @throws DataParseException
     * @throws DataDependencyException
     * @throws DataImporterException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     * @throws InvocationTargetException 
     */
    private void populateFieldValues(Object populateObject, DataProvider.Entry entry, Class clazz, Map<String, Object> dpMap) throws ParseException, DataDependencyException, DataImporterException, InstantiationException, IllegalAccessException, ClassNotFoundException, InvocationTargetException {
        if (populateObject == null) {
            return;
        }

        Map<String, String> simple = entry.getSimpleFields();
        Map<String, Collection<DataProvider.Entry>> complex = entry.getComplexFields();
        Map<String, Object> combined = new HashMap<String, Object>();
        List<LazySetterMethod> lazySetActions = new ArrayList<LazySetterMethod>();
        combined.putAll(simple);
        combined.putAll(complex);

        Map<String, Method> setterMap = getSetter(clazz, combined.keySet().toArray());

        for (Map.Entry<String, Object> e : combined.entrySet()) {
            Method setter = setterMap.get(e.getKey());

            Class fieldType = setter.getParameterTypes()[0];
            Object dpOrDataLookupBy = dpMap.get(e.getKey());

            if (e.getValue() instanceof String) {
                String nullString = (String) e.getValue();
                if (nullString.toLowerCase().equals("null") || nullString.isEmpty()) {
                    continue;
                }

            }

            if (dpOrDataLookupBy != null) {
                // Config

                if (dpOrDataLookupBy instanceof DataProperty) {
                    DataProperty dp = (DataProperty) dpOrDataLookupBy;

                    if (dp.isExpression()) {
                        // Evaluate and assign value at the end
                        String fieldNamesValue = entry.getSimpleFields().get(dp.getName());
                        Object thisObject = thisObjectStack.peek();

                        lazySetActions.add(new LazySetterMethod(populateObject, dp.getName(), new Object[]{new LazyGetterMethod(thisObject, fieldNamesValue)}));
                    } else {
                        DataLookup dl = getDataLookupForProperty(dp);

                        if (dl != null) {
                            Map<String, Serializable> dlbValues = new HashMap<String, Serializable>();
                            Class fromClass = Class.forName(config.getPackageName() + "." + dl.getFrom());
                            generateObjects2(fromClass);
                            // Evaluates dataLookupBy elements

                            for (DataLookupBy dlb : dl.getDataLookupBy()) {
                                Serializable dlbFieldValue = null;
                                Class dlbFieldClass = getClassPropertyClass(fromClass, dlb.getName());

                                if (FormatUtils.isParseableType(dlbFieldClass)) {
                                    if (entry.getSimpleFields().containsKey(dp.getName())) {
                                        dlbFieldValue = (Serializable) FormatUtils.getParsedValue(dlbFieldClass, entry.getSimpleFields().get(dp.getName()), calendarFormat);
                                    } else {
                                        Map<String, Serializable> subValueMap = (Map) (((DataProvider.Entry) entry.getComplexFields().get(dp.getName()).iterator().next()).getSimpleFields());

                                        for (Map.Entry<String, Serializable> subValueMapEntry : subValueMap.entrySet()) {
                                            Class subFieldClass = ReflectionUtils.getFieldType(fromClass, subValueMapEntry.getKey());

                                            if (!FormatUtils.isParseableType(subFieldClass)) {
                                                generateObjects2(subFieldClass);
                                                dlbValues.put(subValueMapEntry.getKey(), subValueMapEntry.getValue());
                                            } else {
                                                dlbValues.put(subValueMapEntry.getKey(), FormatUtils.getParsedValue(subFieldClass, (String) subValueMapEntry.getValue()));
                                            }
                                        }

                                    }
                                } else {
                                    try {
                                        if (!dlbFieldClass.equals(Set.class)) {
                                            dlbFieldValue = (Serializable) dlbFieldClass.newInstance();
                                        }
                                    } catch (Exception eee) {
                                        throw new DataImporterException(eee);
                                    }

                                    if (dlb.isComposite() != null) {
                                        //@todo Composite id parsing!!
                                        // We have to populate the values to the composite id object

                                        // Christian:
                                        // The following expression was valid for me in debug mode
                                        // we could make it prettier but first is has to work ^^
                                        Collection<Entry> entries = entry.getComplexFields().get(dp.getName()).iterator().next().getComplexFields().get(dlb.getName());

                                        if (entries != null) {
                                            for (Entry subEntry : entries) {
                                                if (dlb.getDataPropertyOrDataLookup().size() == 1 && dlb.getDataPropertyOrDataLookup().get(0) instanceof DataLookup) {
                                                    // Christian:
                                                    // Added Support for DataLookup right within DataLookupBy
                                                    Object temporaryHolder = dlbFieldClass.newInstance();
                                                    Map<String, Object> temporaryDpMap = new HashMap<String, Object>();
                                                    DataProperty temporaryDp = new DataProperty();
                                                    temporaryDp.setName(dlb.getName());
                                                    temporaryDp.getDataPropertyOrDataLookup().add(dlb.getDataPropertyOrDataLookup().get(0));
                                                    temporaryDpMap.put(dlb.getName(), temporaryDp);
                                                    populateFieldValues(temporaryHolder, subEntry, dlbFieldClass, temporaryDpMap);

                                                    // Get the generated value out of the holder
                                                    dlbFieldValue = (Serializable) getGetter(dlbFieldClass, new String[]{dlb.getName()})[0].invoke(temporaryHolder);
                                                } else {
                                                    populateFieldValues(dlbFieldValue, subEntry, dlbFieldClass, getDataPropertyMap(dlb.getDataPropertyOrDataLookup()));
                                                }
                                            }

                                            // ViewField does need that
                                            // @todo: Check where it should be generally
                                            storage.flush();
                                        } else {
                                        }
                                        //throw new UnsupportedOperationException("COMPOSITE ID PARSING IS NOT YET IMPLEMENTED");
                                    } else {
                                        // Recursion for dataLookupBy sub lookups
                                        if (dlb.getDataPropertyOrDataLookup() != null && !dlb.getDataPropertyOrDataLookup().isEmpty()) {
                                            generateObjects2(dlbFieldClass);
                                            Entry nextEntry = ((Entry) ((List) e.getValue()).get(0));

                                            if (nextEntry.getComplexFields().get(null) != null) {
                                                nextEntry = nextEntry.getComplexFields().get(null).iterator().next();
                                            }

                                            try {
                                                if (!nextEntry.getComplexFields().isEmpty()) {
                                                    populateFieldValues(dlbFieldValue, nextEntry.getComplexFields().get(dlb.getName()).iterator().next(), dlbFieldClass, getDataPropertyMap(dlb.getDataPropertyOrDataLookup()));
                                                }
                                            } catch (RuntimeException ex) {
                                                throw ex;
                                            }
                                        }
                                    }
                                }
                                if (dlbFieldValue != null) {
                                    String idValue = "";
                                    if (FormatUtils.isParseableType(dlbFieldValue.getClass()) || dlb.isComposite() != null) {
                                        dlbValues.put(dlb.getName(), dlbFieldValue);
                                    } else {
                                        //We know that the Object for the Where clause is a Complex Object
                                        //so we must load this from the database!
                                        generateObjects2(dlbFieldValue.getClass());
                                        Object value = null;

                                        if (simple.containsKey(dlb.getName())) {
                                            idValue = simple.get(dlb.getName());
                                            try {
                                                if (idValue != null && !idValue.equals("null")) {
                                                    value = storage.getById(dlbFieldValue.getClass(), idValue);
                                                }
                                            } catch (RuntimeException ex) {
                                                throw ex;
                                            }
                                        } else {
                                            List l = (List) e.getValue();
                                            DataProvider.Entry subEntry = (DataProvider.Entry) l.toArray()[0];

                                            if (subEntry.getSimpleFields().containsKey(dlb.getName())) {
                                                idValue = subEntry.getSimpleFields().get(dlb.getName());
                                                value = storage.getById(dlbFieldValue.getClass(), idValue);
                                            } else {
                                                throw new DataImporterException("You have to set the datalookupby to composite in the configuration for the field " + dlb.getName() + " for the class " + clazz.getSimpleName() + "!");
//                                                Object temporaryHolder = fromClass.newInstance();
//                                                Map<String, Object> temporaryDpMap = new HashMap<String, Object>();
//                                                DataProperty temporaryDp = new DataProperty(dlb.getDataPropertyOrDataLookup(), dlb.getName());
//                                                temporaryDpMap.put(dlb.getName(), temporaryDp);
//                                                populateFieldValues(temporaryHolder, subEntry, fromClass, temporaryDpMap);
//
//                                                value = new LazyGetterMethod(temporaryHolder, dlb.getName()).invoke();
                                            }
                                        }
                                        dlbValues.put(dlb.getName(), (Serializable) value);

                                    }
                                }
                            }

                            // Get from storage by attributes
                            Object o = null;

                            generateObjects2(fromClass);

                            try {
                                if (clazz.equals(fromClass)) {
                                    // This is a recursive association and we will probably need to flush the storage
                                    storage.flush();
                                }



                                if (dl != null && setter.getParameterTypes()[0].equals(Set.class)) {
                                    Class genericType = (Class) ((ParameterizedType) setter.getGenericParameterTypes()[0]).getActualTypeArguments()[0];
                                    Set set = (Set) ExpressionUtils.getValue(populateObject, e.getKey());

                                    if (((Entry) ((List) e.getValue()).get(0)).getComplexFields().isEmpty() && ((Entry) ((List) e.getValue()).get(0)).getSimpleFields().isEmpty()) {
                                        continue;
                                    }

                                    List<Entry> entries = (List<Entry>) e.getValue();

                                    for (Entry entr : entries) {
                                        Object temporaryHolder = genericType.newInstance();
                                        List<DataLookupBy> list = dl.getDataLookupBy();

                                        for (DataLookupBy dlb : list) {
                                            if (dlb.getDataPropertyOrDataLookup() != null && dlb.getDataPropertyOrDataLookup().size() > 0) {
                                                Map<String, Object> temporaryDpMap = new HashMap<String, Object>();
                                                DataProperty temporaryDp = new DataProperty();
                                                temporaryDp.setName(dlb.getName());
                                                temporaryDp.getDataPropertyOrDataLookup().add(dlb.getDataPropertyOrDataLookup().get(0));
                                                temporaryDpMap.put(dlb.getName(), temporaryDp);
                                                Entry nextEntry = entr;

                                                if (entr.getComplexFields().get(null) != null) {
                                                    nextEntry = entr.getComplexFields().get(null).iterator().next();
                                                }

                                                populateFieldValues(temporaryHolder, nextEntry, genericType, temporaryDpMap);
                                            } else {
                                                Entry nextEntry = entr;

                                                if (entr.getComplexFields().get(null) != null) {
                                                    nextEntry = entr.getComplexFields().get(null).iterator().next();
                                                }
                                                new LazySetterMethod(temporaryHolder, dlb.getName(), new Object[]{nextEntry.getSimpleFields().get(dlb.getName())}).invoke();
                                            }
                                        }


                                        Map<String, Method> getter = this.getGetter(temporaryHolder.getClass());
                                        Map<String, Serializable> whereObj = new HashMap<String, Serializable>();
                                        for (String name : getter.keySet()) {
                                            Object check = getter.get(name).invoke(temporaryHolder);
                                            if (check != null) {

                                                if (check instanceof Collection) {
                                                    if (((Collection) check).isEmpty()) {
                                                        continue;
                                                    }

                                                } else if (check instanceof Map) {
                                                    if (((Map) check).isEmpty()) {
                                                        continue;
                                                    }

                                                } else if (name.toLowerCase().endsWith("id") && check instanceof Number) {
                                                    if (((Number) check).intValue() == 0) {
                                                        continue;
                                                    }
                                                }
                                                whereObj.put(name, (Serializable) getter.get(name).invoke(temporaryHolder));
                                            }
                                        }

                                        generateObjects2(genericType);
                                        Object setEntry = storage.getByFields(genericType, whereObj);
                                        set.add(setEntry);
                                    }
                                    continue;

                                }



                                o = storage.getByFields(fromClass, dlbValues);
                            } catch (Exception as) {
                                throw new DataImporterException(as);
                            }
                            //If there is no Object in the Database found
                            if (o == null) {
                                throw new DataImporterException("Object from type " + fromClass + " with the lookup map " + dlbValues + " could not be found.");
                            }

                            //@todo I think this is wright!
                            //setter.invoke(populateObject, o);


                            if (dl.getFetch() != null) {
                                // Fetch the value from the property into the field
                                try {
                                    o = ExpressionUtils.getValue(o, dl.getFetch());
                                } catch (Exception ex) {
                                    log.log(Level.SEVERE, "This should not happen!", ex);
                                    throw new DataImporterException(ex);
                                }
                            }

                            // Assign the object to the field
                            if (Set.class.equals(setter.getParameterTypes()[0])) {
                                String[] fieldsForGetter = new String[1];
                                fieldsForGetter[0] = (String) e.getKey();
                                Method[] getter = getGetter(populateObject.getClass(), fieldsForGetter);
                                Set objectSet = (Set) getter[0].invoke(populateObject);

                                if (objectSet == null) {
                                    objectSet = new HashSet();
                                }

                                objectSet.add(o);
                                setter.invoke(populateObject, objectSet);
                            } else {
                                try {
                                    setter.invoke(populateObject, o);
                                } catch (Exception eas) {
                                    throw new DataImporterException(eas);
                                }
                            }

                        } else {
                            // Config no Lookup
                            populateComplexStructure(populateObject, fieldType, setter, e, getDataPropertyMap(dp.getDataPropertyOrDataLookup()));
                        }
                    }
                } else {
                    DataLookupBy dlb = (DataLookupBy) dpOrDataLookupBy;

                    if (!dlb.getDataPropertyOrDataLookup().isEmpty()) {
                        // Create Object
                        Map<String, Object> dpMapSub = getDataPropertyMap(dlb.getDataPropertyOrDataLookup());
                        Object o = fieldType.newInstance();
                        populateFieldValues(o, entry.getComplexFields().get(dlb.getName()).iterator().next(), fieldType, dpMapSub);
                    }
                }
            } else {
                if (simple.containsKey(e.getKey())) {
                    if (FormatUtils.isParseableType(fieldType)) {
                        //Native or Wrapper
                        populateNativeOrWrapper(populateObject, setter, fieldType, e.getValue().toString());
                    } else {
                        //Get by ID

                        populateById(populateObject, setter, fieldType, e.getValue().toString());
                    }
                } else {
                    populateComplexStructure(populateObject, fieldType, setter, e, dpMap);
                }
            }
        }
        //Give the Expression to the generateObjects2
        if (!lazySetActions.isEmpty()) {
            lazyActions.put(currentGenerateObject2Class, lazySetActions);
        }




    }

    /**
     * Decide wich Complex Object has to be generate, und generate this.
     * @param populateObject    The Object wich has to be invoke with the Data.
     * @param fieldType This is the Type of the Object wich has to be generate.
     * @param setter    The Method wich has to be invoke with the generated Object.
     * @param e The Map.Entry wich provied access to the Data.
     * @param conf  Map wich gives you Information about the Config.
     * @throws DataImporterException
     * @throws DataParseException
     * @throws DataDependencyException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     * @throws InvocationTargetException 
     */
    private void populateComplexStructure(Object populateObject, Class fieldType, Method setter, Map.Entry<String, Object> e, Map<String, Object> conf) throws DataImporterException, ParseException, DataDependencyException, InstantiationException, IllegalAccessException, ClassNotFoundException, InvocationTargetException {
        if (Map.class.equals(fieldType)) {
            populateMap(populateObject, setter,
                    ((DataProvider.Entry) (((Collection) e.getValue()).toArray()[0])), conf);
        } else if (Set.class.equals(fieldType)) {
            // Set
            populateSet(populateObject, setter,
                    ((DataProvider.Entry) (((Collection) e.getValue()).toArray()[0])), conf);
        } else {
            Class valueType = setter.getParameterTypes()[0];
            Object valueObject = null;
            try {
                valueObject = valueType.newInstance();
            } catch (InstantiationException ie) {
                throw ie;
            }
            populateFieldValues(valueObject, ((DataProvider.Entry) (((Collection) e.getValue()).toArray()[0])), valueType, conf);

            if (storage.isManaged(valueType)) {
                try {
                    valueObject = storage.saveObject((Serializable) valueObject);
                } catch (Exception eee) {
                    //For duplicates
                    throw new DataImporterException(eee);
                }
            }

            setter.invoke(populateObject, valueObject);
        }
    }

    /**
     * A Method wich parsed the Values of an Entry and generate a Set for the 
     * populateObject.
     * @param populateObject    The objects wich has the Set as a Property.
     * @param set   The method to invoke the generated Set.
     * @param entry Data in a Entry
     * @param conf Configuration
     * @throws DataParseException
     * @throws DataDependencyException
     * @throws DataImporterException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     * @throws InvocationTargetException 
     */
    private void populateSet(Object populateObject, Method set, DataProvider.Entry entry, Map<String, Object> conf) throws ParseException, DataDependencyException, DataImporterException, InstantiationException, IllegalAccessException, ClassNotFoundException, InvocationTargetException {

        Set data = new HashSet();
        Class genericType = (Class) ((ParameterizedType) set.getGenericParameterTypes()[0]).getActualTypeArguments()[0];
        Serializable o = null;
        List<DataProvider.Entry> entries = (List<DataProvider.Entry>) entry.getComplexFields().get(null);
        if (entries != null) {
            for (DataProvider.Entry ent : entries) {
                try {
                    o = (Serializable) genericType.newInstance();
                } catch (Exception ex) {
                    log.log(Level.SEVERE, ex.getMessage(), ex);
                    throw new DataImporterException(ex);
                }
                //@todo give Conf
                Class type = storage.getIdentifierType(genericType);

                populateFieldValues(o, ent, genericType, conf);
                try {
                    o = storage.saveObject(o);
                } catch (Exception none) {
                    throw new DataImporterException(none);
                }
                data.add(o);

            }
        }
        try {
            set.invoke(populateObject, data);


        } catch (Exception ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
            throw new DataImporterException(ex);
        }

    }

    /**
     * A Method wich parsed the Values of an Entry and generate a Map for the 
     * populateObject.
     * @param populateObject The objects wich has the Map as a Property.
     * @param set   The method to invoke the generated Map.
     * @param entry Data in a Entry
     * @param conf Configuration
     * @throws DataImporterException
     * @throws DataParseException
     * @throws DataDependencyException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     * @throws InvocationTargetException 
     */
    private void populateMap(Object populateObject, Method set, DataProvider.Entry entry, Map<String, Object> conf) throws DataImporterException, ParseException, DataDependencyException, InstantiationException, IllegalAccessException, ClassNotFoundException, InvocationTargetException {
        Class genericKey = (Class) ((ParameterizedType) set.getGenericParameterTypes()[0]).getActualTypeArguments()[0];
        Class genericValue = (Class) ((ParameterizedType) set.getGenericParameterTypes()[0]).getActualTypeArguments()[1];
        Map<String, Collection<Entry>> map = entry.getComplexFields();

        Map toSet = new HashMap();
        for (String key : map.keySet()) {
            Object keyObject = null;
            //Get Key
            if (!FormatUtils.isParseableType(genericKey)) {
                Class identifierType = storage.getIdentifierType(genericKey);
                Object o = null;

                try {
                    o = FormatUtils.getParsedValue(identifierType, key, calendarFormat);
                } catch (ParseException ex) {
                    log.log(Level.SEVERE, ex.getMessage(), ex);
                    throw new DataImporterException(ex);
                }

                generateObjects2(genericKey);
                keyObject = storage.getById(genericKey, (Serializable) o);
            } else {
                keyObject = FormatUtils.getParsedValue(genericKey, key, calendarFormat);
            }

            //Create Value
            Object valueObject = null;

            try {
                valueObject = genericValue.newInstance();
            } catch (InstantiationException ex) {
                log.log(Level.SEVERE, ex.getMessage(), ex);
                throw new DataImporterException(ex);
            }

            populateFieldValues(valueObject, ((DataProvider.Entry) ((ArrayList) map.get(key)).toArray()[0]), genericValue, conf);
            toSet.put(keyObject, valueObject);
        }
        try {
            set.invoke(populateObject, toSet);


        } catch (Exception ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
            throw new DataImporterException(ex);
        }
    }

    /**
     * This Mehtod return the Class of an Property
     * @param clazz
     * @param propertyName
     * @return 
     */
    private Class getClassPropertyClass(Class clazz, String propertyName) {
        //log.log(Level.SEVERE, "Class: " + clazz + ", property: " + propertyName);
        String[] ar = new String[1];
        ar[0] = propertyName;
        Method[] setter = null;

        try {
            setter = getSetter(clazz, ar);
        } catch (RuntimeException re) {
            throw re;
        }
        return setter[0].getParameterTypes()[0];
    }

    /**
     * This Method generate a Native or Wrapper Object for populateObject and
     * invoke the Object.
     * @param populateObject    Object wich contains the Native or Wrapper.
     * @param setter    The Method to set the generated Object.
     * @param fieldType The Type of the generated Object.
     * @param fieldValue The Value of the generated Object.
     * @throws DataImporterException 
     */
    private void populateNativeOrWrapper(Object populateObject, Method setter, Class fieldType, String fieldValue) throws DataImporterException {
        try {
            Object o = FormatUtils.getParsedValue(fieldType, fieldValue, calendarFormat);

            // Set null value
            if (o == null) {
                setValue(populateObject, setter, null);
            }

            // Setting the java type value, e.g. String, Date, Calendar, etc.
            setValue(populateObject, setter, o);

        } catch (ParseException ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
            throw new DataImporterException(ex);
        }

    }

    /**
     * This Method search by the ID in DB and invoke the Result into populateObject.
     * @param populateObject    Object wich contains the Native or Wrapper.
     * @param setter    The Method to set the generated Object.
     * @param fieldType The Type of the generated Object.
     * @param fieldValue  The Value of the generated Object.
     * @throws DataImporterException 
     */
    private void populateById(Object populateObject, Method setter, Class fieldType, String fieldValue) throws DataImporterException {
        Class identifierType = storage.getIdentifierType(fieldType);
        Object o = null;

        try {
            o = FormatUtils.getParsedValue(identifierType, fieldValue, calendarFormat);
            //log.log(Level.SEVERE, o.toString());
        } catch (Exception ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
            throw new DataImporterException(ex);
        }
        generateObjects2(fieldType);
        Object val = null;
        try {
            val = storage.getById(fieldType, (Serializable) o);
        } catch (Exception ea) {
            throw new DataImporterException(ea);
        }


        try {
            setter.invoke(populateObject, val);
            //@todo: ich habe eine vermutung
            // wenn populateObject val zugewiesen bekommt, dann muss val auch populateObject zugewiesen bekommen
            // es muss in der klasse von val nach der methode setXXX(populateObject.class)
            // oder in der klasse von val nach der methode Set<populateObject.class>getXXX()
            // gesucht werden und das objekt muss auch auf dieser seite hinzugefügt werden.
            // danach muss val persistiert werden.
        } catch (Exception ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
            throw new DataImporterException(ex);
        }
    }

    /**
     * Set an Object value into object, by invoke the Method m.
     * @param object Object wich contains the value as a Property.
     * @param m The Method to set the generated Object.
     * @param value  The Value that has to be set.
     * @throws DataImporterException 
     */
    private void setValue(Object object, Method m, Object value) throws DataImporterException {
        try {
            Object o = null;
            if (value == null) {
                m.invoke(object, o);
                return;
            }

            if (value instanceof GregorianCalendar) {
                m.invoke(object, value);
                return;
            }
            if (value instanceof String) {

                String s = (String) value;
                if ("NULL".equals(s.toUpperCase()) || "".equals(s)) {

                    m.invoke(object, o);
                    return;
                }

                if (m.getParameterTypes()[0].equals(Integer.TYPE)) {
                    m.invoke(object, Integer.parseInt((String) value));


                } else if (m.getParameterTypes()[0].equals(Integer.class)) {
                    m.invoke(object, Integer.parseInt((String) value));
                } else if (m.getParameterTypes()[0].equals(Long.class) || m.getParameterTypes()[0].equals(Long.TYPE)) {
                    m.invoke(object, Long.parseLong((String) value));
                } else if (m.getParameterTypes()[0].equals(Boolean.class) || m.getParameterTypes()[0].equals(Boolean.TYPE)) {
                    m.invoke(object, Boolean.parseBoolean((String) value));
                } else {

                    m.invoke(object, value);
                }
            } else {
                m.invoke(object, value);
            }
        } catch (Exception ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
            throw new DataImporterException(ex);
        }
    }

    /**
     * This Method returns all Setter for the Properties wich are in fields of a Class.
     * @param objectClass The Class wich contains the setter.
     * @param fields Propertynames of the setter you want.
     * @return Map wich key = propertyname and value = setter.
     */
    private Map<String, Method> getSetter(Class objectClass, Object[] fields) {

        Map<String, Method> setterMap = new HashMap<String, Method>();
        for (int i = 0; i < fields.length; i++) {
            for (Method m : objectClass.getMethods()) {
                if (m.getName().startsWith("set") && m.getName().substring(3).equalsIgnoreCase((String) fields[i])) {
                    Class[] types = m.getParameterTypes();

                    if (types.length != 1) {
                        throw new IllegalArgumentException("This is not a valid setter");
                    }
                    setterMap.put((String) fields[i], m);
                    break;
                }
            }

            if (setterMap.get((String) fields[i]) == null) {
                throw new IllegalArgumentException("Setter not found for field " + fields[i] + " on class " + objectClass);
            }
        }

        return setterMap;
    }

    /**
     * This Method returns all Getter for the Properties wich are in fields of a Class.
     * @param objectClass The Class wich contains the getter.
     * @param fields Propertynames of the getter you want.
     * @return Map wich key = propertyname and value = getter.
     */
    private Map<String, Method> getGetter(Class objectClass, Object[] fields) {

        Map<String, Method> setterMap = new HashMap<String, Method>();
        for (int i = 0; i < fields.length; i++) {
            for (Method m : objectClass.getMethods()) {
                if (m.getName().startsWith("get") && !m.getName().equals("getClass")) {
                    String name = m.getName().substring(3);
                    name = name.substring(0, 1).toLowerCase() + name.substring(1);
                    setterMap.put(name, m);
                } else if (m.getName().startsWith("is") && !m.getName().equals("getClass")) {
                    String name = m.getName().substring(2);
                    name = name.substring(0, 1).toLowerCase() + name.substring(1);
                    setterMap.put(name, m);
                }
            }

            if (setterMap.get((String) fields[i]) == null) {
                throw new IllegalArgumentException("Getter not found for field " + fields[i] + " on class " + objectClass);
            }
        }

        return setterMap;
    }

    private Map<String, Method> getGetter(Class objectClass) {

        Map<String, Method> setterList = new HashMap<String, Method>();

        for (Method m : objectClass.getMethods()) {
            if (m.getName().startsWith("get") && !m.getName().equals("getClass")) {
                String name = m.getName().substring(3);
                name = name.substring(0, 1).toLowerCase() + name.substring(1);
                setterList.put(name, m);
            } else if (m.getName().startsWith("is") && !m.getName().equals("getClass")) {
                String name = m.getName().substring(2);
                name = name.substring(0, 1).toLowerCase() + name.substring(1);
                setterList.put(name, m);
            }

        }

        return setterList;
    }

    /**
     *This Method returns all Setter for the Properties wich are in fields of a Class.
     * @param objectClass The Class wich contains the setter.
     * @param fields Propertynames of the setter you want.
     * @return Sorted array Property Index of fields = Method Index in the return Array.
     */
    private Method[] getSetter(Class objectClass, String[] fields) {

        Method[] setter = new Method[fields.length];
        for (int i = 0; i < fields.length; i++) {
            for (Method m : objectClass.getMethods()) {
                if (m.getName().startsWith("set") && m.getName().substring(3).equalsIgnoreCase((String) fields[i])) {
                    Class[] types = m.getParameterTypes();

                    if (types.length != 1) {
                        throw new IllegalArgumentException("This is not a valid setter");
                    }
                    setter[i] = m;
                    break;
                }
            }

            if (setter[i] == null) {
                throw new IllegalArgumentException("Setter not found for field " + fields[i] + " on class " + objectClass.getSimpleName());
            }
        }

        return setter;
    }

    /**
     *This Method returns all Getter for the Properties wich are in fields of a Class.
     * @param objectClass The Class wich contains the getter.
     * @param fields Propertynames of the getter you want.
     * @return Sorted array Property Index of fields = Method Index in the return Array.
     */
    private Method[] getGetter(Class objectClass, String[] fields) {

        Method[] setter = new Method[fields.length];
        for (int i = 0; i < fields.length; i++) {
            for (Method m : objectClass.getMethods()) {

                if (m.getName().startsWith("get") && !m.getName().equals("getClass")) {
                    String name = m.getName().substring(3);
                    name = name.substring(0, 1).toLowerCase() + name.substring(1);
                    setter[i] = m;
                } else if (m.getName().startsWith("is") && !m.getName().equals("getClass")) {
                    String name = m.getName().substring(2);
                    name = name.substring(0, 1).toLowerCase() + name.substring(1);
                    setter[i] = m;
                }
            }

            if (setter[i] == null) {
                throw new IllegalArgumentException("Getter not found for field " + fields[i] + " on class " + objectClass);
            }
        }

        return setter;
    }

    /**
     * This Method convert all Dataproperties in col to a Map.
     * @param col
     * @return 
     */
    private Map<String, Object> getDataPropertyMap(Collection col) {
        Map<String, Object> m = new HashMap<String, Object>();
        if (col == null) {
            return m;
        }
        for (Object o : col) {
            if (o instanceof DataProperty) {
                DataProperty p = (DataProperty) o;
                m.put(p.getName(), p);
            }
        }
        return m;
    }

    /**
     * Return the DataLookup of an DataProperty.
     * @param dp
     * @return 
     */
    private DataLookup getDataLookupForProperty(DataProperty dp) {

        if (dp == null) {
            return null;
        }

        for (Object o : dp.getDataPropertyOrDataLookup()) {
            if (o instanceof DataLookup) {
                return (DataLookup) o;
            }
        }
        return null;
    }

    /**
     * 
     * @param dp
     * @param values
     * @return 
     */
    private Map<String, Serializable> getDataLookupByMap(DataLookup dp, Map<String, String> values) {
        Map<String, Serializable> m = new HashMap<String, Serializable>();

        for (DataLookupBy dlb : dp.getDataLookupBy()) {
            m.put(dlb.getName(), values.get(dlb.getName()));
        }

        return m;
    }
}
