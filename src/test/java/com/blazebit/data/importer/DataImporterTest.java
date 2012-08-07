/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.importer;

import java.io.File;

import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import com.blazebit.data.cfg.Configuration;
import com.blazebit.data.importer.provider.CsvDataProvider;
import com.blazebit.data.importer.storage.JPADataStorage;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

/**
 *
 * @author Christian Beikov
 */
public class DataImporterTest {

    private static final Logger log = Logger.getLogger(DataImporterTest.class.getName());
    private DataImporter importer;
    private EntityManagerFactory fact;
    private EntityManager session;
    private DataStorage storage;
    private boolean commit = false;

//    @Before
    public void setUp() {
        fact = Persistence.createEntityManagerFactory("BlazebitTest");
        session = fact.createEntityManager();
        File configFile = new File(new File("").getAbsolutePath(), "src/main/resources/dataConfig.xml");
        File dataDir = new File(new File("").getAbsolutePath(), "src/main/resources/com/blazebit/web/cms/core/model/data/");

        storage = new JPADataStorage(session);
        importer = new Configuration(configFile).buildImporter(storage);

        try {
            if (dataDir.isDirectory()) {
                for (File f : dataDir.listFiles()) {
                    if (f.getName().endsWith(".csv")) {
                        importer.add(new CsvDataProvider(f));

                    }
                }
            }
        } catch (Exception ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

//    @After
    public void tearDown() {
        if (fact != null) {
            fact.close();
        }
    }

    /**
     * Test of getObjects method, of class GenericObjectGenerator.
     */
//    @Test
    public void testGetObjects() {
        EntityTransaction tx = null;

        try {
            tx = session.getTransaction();
            tx.begin();
            importer.generateObjects();
            storage.flush();

            if (commit) {
                tx.commit();
            } else {
                tx.rollback();
            }
            log.info("Successfully generated objects!");
        } catch (Throwable t) {
            log.log(Level.SEVERE, t.getMessage(), t);
            fail(t.getMessage());

            if (tx != null) {
                tx.rollback();
            }
        } finally {

            if (session != null) {
                session.close();
            }
        }
    }

    public static void main(String[] args) {
        DataImporterTest test = new DataImporterTest();
        test.commit = true;
        test.setUp();
        test.testGetObjects();
        test.tearDown();
    }
}
