/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.importer.provider;

import com.blazebit.data.importer.DataProvider;
import com.csvreader.CsvReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The csv files must be valid. No trailing slash or so.
 * Standard text qualifier is a <code>"</code> but this can be changed to <code>'</code>
 * by qutoting the headers too.
 * @author Christian Beikov
 */
public class CsvDataProvider implements DataProvider {

    private static final Logger log = Logger.getLogger(CsvDataProvider.class.getName());
    private static final char SEPARATOR = ';';
    private CsvReader reader;
    private File file;
    private String[] headers;
    private String simpleClassName;

    public CsvDataProvider(String fileName) throws FileNotFoundException, IOException {
        this(fileName, SEPARATOR);
    }

    public CsvDataProvider(String fileName, char separator) throws FileNotFoundException, IOException {
        this(new File(fileName), separator);
    }

    public CsvDataProvider(File file) throws FileNotFoundException, IOException {
        this(file, SEPARATOR);
    }

    public CsvDataProvider(File file, char separator) throws FileNotFoundException, IOException {
        this.file = file;
        Charset charSet = Charset.forName("UTF-8");
       
        FileInputStream fl = new FileInputStream(file);
        this.reader = new CsvReader(fl, separator,charSet);
        
        this.reader.setTextQualifier('\'');
        this.reader.setEscapeMode(CsvReader.ESCAPE_MODE_BACKSLASH);
        this.reader.readHeaders();
        this.simpleClassName = this.file.getName().split("\\.")[0];
        this.headers = this.reader.getHeaders();
        
        if(this.reader.getRawRecord().charAt(0) == '\'')
            this.reader.setTextQualifier('\'');
        else
            this.reader.setTextQualifier('"');
    }

    @Override
    public String getSimpleClassName() {
        return simpleClassName;
    }

    @Override
    public String[] getFieldNames() {
        return headers;
    }

    @Override
    public DataProvider.Entry next() {
        try {
            if (this.reader.readRecord()) {
              
                    if(0<=Arrays.binarySearch(this.reader.getValues(), "Teststraße")){
                        System.out.println("Found");
            }
                return parseRecord(this.reader.getValues());
            }
        } catch (IOException ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
        }
        
        return null;
    }
    
    private DataProvider.Entry parseRecord(String[] data){
        Map<String, String> simpleFields = new HashMap<String, String>();
        Map<String, Collection<DataProvider.Entry>> complexFields = new HashMap<String, Collection<DataProvider.Entry>>();

        for(int i = 0; i < data.length; i++){
            if ('{' == data[i].charAt(0)) {
                Collection<DataProvider.Entry> subEntries = new ArrayList<DataProvider.Entry>();
                subEntries.add(parseComplex(data[i].toCharArray(), 0, data[i].length() - 1));
                complexFields.put(headers[i], subEntries);
            }else{
                if(headers.length!=data.length){
                    System.out.println("fehler");
                }
                simpleFields.put(headers[i], data[i]);
            }
        }
        
        return new CsvDataProviderEntry(simpleFields, complexFields);
    }
    
    private DataProvider.Entry parseComplex(char[] part, int start, int end){
        Map<String, String> simpleFields = new HashMap<String, String>();
        Map<String, Collection<DataProvider.Entry>> complexFields = new HashMap<String, Collection<DataProvider.Entry>>();
        int depth = 0;
                
        if(equalsNextAlphaChar('{', part, start + 1)){
            // Collection
            Collection<DataProvider.Entry> subEntries = new ArrayList<DataProvider.Entry>();
            int elementIndex = start;
            
            for(int i = start + 1; i < end; i++){
                if('{' == part[i]){
                    depth++;
                }else if('}' == part[i]){
                    depth--;
                }
                if((',' == part[i] || '}' == part[i]) && depth == 0){
                    if('}' == part[i])
                        subEntries.add(parseComplex(part, indexOfNextAlphaChar(part, elementIndex + 1), i));
                    
                    if(',' == part[i])
                        elementIndex = indexOfNextAlphaChar(part, i + 1) - 1;
                }
            }
            
            complexFields.put(null, subEntries);
        }else{
            // Map or Object
            String key = null;
            int colonIndex = -1;
            boolean complexContent = false;
            StringBuilder keySb = new StringBuilder();
            StringBuilder valueSb = new StringBuilder();
            
            for(int i = start + 1; i < end; i++){
                if('{' == part[i]){
                    depth++;
                }else if('}' == part[i]){
                    depth--;
                }
                
                if(':' == part[i] && depth == 0){
                    colonIndex = i;
                    complexContent = equalsNextAlphaChar('{', part, i + 1);
                    key = keySb.toString().trim();
                }else if((',' == part[i] || '}' == part[i] || (!complexContent && i + 1 == end)) && depth == 0){
                    if(complexContent){
                        Collection<DataProvider.Entry> subEntries = new ArrayList<DataProvider.Entry>();
                        subEntries.add(parseComplex(part, indexOfNextAlphaChar(part, colonIndex + 1), i));
                        complexFields.put(key, subEntries);
                    }else if(key != null){
                        simpleFields.put(key, removeQualifiers(valueSb.toString()));
                    }
                    
                    key = null;
                    colonIndex = -1;
                    complexContent = false;
                    keySb.setLength(0);
                    valueSb.setLength(0);
                }else if(colonIndex < 0 && '\t' != part[i] && depth == 0){
                    keySb.append(part[i]);
                }else if(colonIndex > -1 && !complexContent){
                    valueSb.append(part[i]);
                }
            }
        }
        
        return new CsvDataProviderEntry(simpleFields, complexFields);
    }
    
    private static boolean equalsNextAlphaChar(char needle, char[] haystack, int startIndex){
        int index = indexOfNextAlphaChar(haystack, startIndex);
        return index > -1 && haystack[index] == needle;
    }
    
    private static int indexOfNextAlphaChar(char[] haystack, int startIndex){
        for(int i = startIndex; i < haystack.length; i++){
            if(Character.isSpaceChar(haystack[i]))
                continue;
            return i;
        }
        
        return -1;
    }

    private static String removeQualifiers(String text) {
        text = text.trim();
        int start = text.startsWith("\"") ? 1 : 0;
        int end = text.endsWith("\"") ? text.length() - 1 : text.length();
        return text.length() > 2 ? text.substring(start, end) : text;
    }
    
    private static class CsvDataProviderEntry implements DataProvider.Entry{

        private Map<String, String> simpleFields;
        private Map<String, Collection<DataProvider.Entry>> complexFields;

        public CsvDataProviderEntry(Map<String, String> simpleFields, Map<String, Collection<DataProvider.Entry>> complexFields) {
            this.simpleFields = simpleFields;
            this.complexFields = complexFields;
        }
        
        @Override
        public Map<String, String> getSimpleFields() {
            return simpleFields;
        }

        @Override
        public Map<String, Collection<DataProvider.Entry>> getComplexFields() {
            return complexFields;
        }

        @Override
        public String toString() {
            return getString(0);
        }
        
        private String getString(int tabs) {
            StringBuilder sb = new StringBuilder();
            
            for(Map.Entry<String,String> entry : simpleFields.entrySet()){
                for(int i = 0; i < tabs; i++)
                    sb.append("\t");
                sb.append(entry.getKey()).append(" : ").append(entry.getValue()).append("\n");
            }
            
            for(Map.Entry<String,Collection<DataProvider.Entry>> entry : complexFields.entrySet()){
                if(entry.getKey() == null){
                    for(int i = 0; i < tabs; i++)
                        sb.append("\t");
                    sb.append("Collection :{").append("\n");
                }else{
                    for(int i = 0; i < tabs; i++)
                        sb.append("\t");
                    sb.append(entry.getKey()).append(" :{").append("\n");
                }
                    
                for(DataProvider.Entry e : entry.getValue())
                    sb.append(((CsvDataProviderEntry)e).getString(tabs + 1));
                
                for(int i = 0; i < tabs; i++)
                    sb.append("\t");
                sb.append("}").append("\n");
            }
            return sb.toString();
        }
        
        
        
    }
    
}
