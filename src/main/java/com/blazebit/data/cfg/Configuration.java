/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.cfg;

import com.blazebit.data.exporter.DataExporter;
import com.blazebit.data.importer.DataImporter;
import com.blazebit.data.importer.DataStorage;
import com.blazebit.data.importer.GenericDataImporter;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

/**
 *
 * @author Christian Beikov
 */
public class Configuration{
    
    private File configFile;
    private DataConfig dataImport;
    private DateFormat dateFormat;
    private DateFormat calendarFormat;
    private String packageName;
    private Map<String, Map<String, DataProperty>> dataClasses = new HashMap<String, Map<String, DataProperty>>();
    private Map<String, String[]> dataDependencies = new HashMap<String, String[]>();
    
    public Configuration(File configFile){
        this.configFile = configFile;
    }
    
    public DateFormat getDateFormat(){
        if(dateFormat == null){
            dateFormat = new SimpleDateFormat(getDataImport().getDateFormat());
        }
        
        return dateFormat;
    }
    
    public DateFormat getCalendarFormat(){
        if(calendarFormat == null){
            calendarFormat = new SimpleDateFormat(getDataImport().getCalendarFormat());
        }
        
        return calendarFormat;
    }
    
    public String getPackageName(){
        if(packageName == null){
            packageName = getDataImport().getPackageName();
        }
        
        return packageName;
    }

    public Map<String, DataProperty> getDataProperties(Class clazz) {
        Map<String,DataProperty> dataProperties = dataClasses.get(clazz.getSimpleName());
        
        if(dataProperties == null){
            dataProperties = new HashMap<String, DataProperty>();
            dataClasses.put(clazz.getSimpleName(), dataProperties);
            
            for(DataClass dc : getDataImport().getDataClass())
                if(dc.getName().equals(clazz.getSimpleName())){
                    for(DataProperty dp : dc.getDataProperty()){
                        dataProperties.put(dp.getName(), dp);
                    }
                    break;
                }
        }
        
        return dataProperties;
    }

    public String[] getDefinedDependencies(Class clazz) {
        String[] dependencies = dataDependencies.get(clazz.getSimpleName());
        
        if(dependencies == null){
            dependencies = new String[0];
            for(DataClass dc : getDataImport().getDataClass()){
                if(dc.getName().equals(clazz.getSimpleName())){
                    if(dc.getDependsOn() != null){
                        dependencies = dc.getDependsOn().split(",");
                    }
                    break;
                }
            }
            dataDependencies.put(clazz.getSimpleName(), dependencies);
        }
        
        return dependencies;
    }
    
    public DataImporter buildImporter(DataStorage ds){
        return new GenericDataImporter(ds, this);
    }
    
    public DataExporter buildExporter(){
        throw new UnsupportedOperationException("Not yet implemented");
    }
    
    protected DataConfig getDataImport(){
        if(dataImport == null){
            try {
                JAXBContext jc = JAXBContext.newInstance("com.blazebit.data.cfg");
                Unmarshaller unmarshaller = jc.createUnmarshaller();

                dataImport = (DataConfig) unmarshaller.unmarshal(configFile);
            } catch (JAXBException ex) {
                throw new ConfigurationException(ex);
            }
        }
        
        return dataImport;
    }
}
