/*
 * Copyright 2011 Blazebit
 */
package com.blazebit.data.importer.storage;

import com.blazebit.data.importer.DataStorage;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 * Simple JPA implementation of a datastorage.
 *
 * @author Christian Beikov
 */
public class JPADataStorage implements DataStorage {

    private static final Logger log = Logger.getLogger(JPADataStorage.class.getName());
    private static final boolean DEBUG = false;
    private EntityManager session;
    private transient Class lastClass;
    private Stack savedObject = new Stack();
    private int saveCounter = 0;

    public JPADataStorage(EntityManager session) {
        this.session = session;
    }

    @Override
    public Serializable getByField(Class clazz, String fieldName, Serializable fieldValue) {
        return (Serializable) session.createQuery(getCriteria(clazz, fieldName, fieldValue)).getSingleResult();
    }

    @Override
    public Serializable getByFields(Class clazz, Map<String, Serializable> valueMap) {
        return (Serializable) session.createQuery(getCriteria(clazz, valueMap)).getSingleResult();
    }

    @Override
    public List<Serializable> getListByField(Class clazz, String fieldName, Serializable fieldValue) {
        return session.createQuery(getCriteria(clazz, fieldName, fieldValue)).getResultList();
    }

    @Override
    public List<Serializable> getListByFields(Class clazz, Map<String, Serializable> valueMap) {
        return session.createQuery(getCriteria(clazz, valueMap)).getResultList();
    }

    private CriteriaQuery getCriteria(Class clazz, Map<String, Serializable> valueMap) {
        if (session == null) {
            throw new IllegalStateException("Session not connected!");
        }
        flush();

        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery cq = cb.createQuery();
        Root r = cq.from(clazz);
        cq.select(r);
        List<Predicate> predicates = new ArrayList<Predicate>();

        for (Map.Entry<String, Serializable> entry : valueMap.entrySet()) {
            if (entry.getValue() instanceof Collection) {
                predicates.add(r.get(entry.getKey()).in((Collection) entry.getValue()));
            } else {
                predicates.add(cb.equal(r.get(entry.getKey()), entry.getValue()));
            }
        }
        
        cq.where(cb.and(predicates.toArray(new Predicate[0])));
        return cq;
    }

    private CriteriaQuery getCriteria(Class clazz, String fieldName, Serializable fieldValue) {
        if (session == null) {
            throw new IllegalStateException("Session not connected!");
        }
        flush();

        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery cq = cb.createQuery();
        Root r = cq.from(clazz);
        cq.select(r);
        
        if (fieldValue instanceof Collection) {
            return cq.where(r.get(fieldName).in((Collection) fieldValue));
        } else {
            return cq.where(cb.equal(r.get(fieldName), fieldValue));
        }
    }

    @Override
    public Serializable getById(Class clazz, Serializable id) {
        if (session == null) {
            throw new IllegalStateException("Session not connected!");
        }
        flush();
        return (Serializable) session.find(clazz, id);
    }

    @Override
    public Serializable saveObject(Serializable object) {
        Serializable ret = null;
        
        if (session == null) {
            throw new IllegalStateException("Session not connected!");
        }
        try {
            if (lastClass == null) {
                lastClass = object.getClass();
            }
            if (!lastClass.equals(object.getClass())) {
                log.log(Level.FINE, "Flushing " + lastClass);
                flush();
            }
            lastClass = object.getClass();
            
            if(DEBUG){
                if (stackContainsObject(object)) {
                    log.log(Level.FINE, "This was already created!");
                }
            }
            
            ret = session.merge(object);

            if (++saveCounter > 10) {
                flush();
            }
            
            if(DEBUG){
                if (object != null) {
                    savedObject.push(object);
                }
            }
            
            return ret;
        } catch (RuntimeException e) {
            throw e;
        }
    }

    @Override
    public Class getIdentifierType(Class clazz) {
        try {
            return session.getEntityManagerFactory().getMetamodel().entity(clazz).getIdType().getJavaType();
        } catch (NullPointerException nl) {
            return null;
        }

    }

    @Override
    public void close() {
        session.close();
        session = null;
    }

    public void flush() {
        if (session == null) {
            throw new IllegalStateException("Session not connected!");
        }
        log.log(Level.FINE, "MANUAL FLUSH " + lastClass);
        session.flush();
        session.clear();
        saveCounter = 0;
    }

    @Override
    public boolean isManaged(Class clazz) {
        try{
            return session.getEntityManagerFactory().getMetamodel().entity(clazz) != null;
        }catch(IllegalArgumentException ex){
            return false;
        }
    }

    private boolean stackContainsObject(Serializable object) {
        Class clazz = object.getClass();
        Method[] getters = getNoneIdAndNoneCollectionGetters(clazz);

        try {
            for (Object o : savedObject) {
                if (clazz.isInstance(o)) {
                    boolean equals = true;
                    for (Method m : getters) {
                        Object o1 = m.invoke(object);
                        Object o2 = m.invoke(o);
                        
                        if((o1 == null && o2 != null) || (o1 != null && o2 == null)){
                            equals = false;
                            break;
                        } else if(o1 == null && o2 == null){
                            continue;
                        } else if(!m.invoke(object).equals(m.invoke(o))){
                            equals = false;
                            break;
                        }
                    }
                    
                    if(!equals)
                        continue;
                    return true;
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        
        return false;
    }

    private Method[] getNoneIdAndNoneCollectionGetters(Class clazz) {
        List<Method> methods = new ArrayList<Method>();
        Class idType = getIdentifierType(clazz);
        String idName = session.getEntityManagerFactory().getMetamodel().entity(clazz).getId(idType).getName();
        
        if(!idType.equals(int.class) && !idType.equals(Integer.class))
            idName = null;
        
        for(Method m : clazz.getMethods()){
            if(m.getName().startsWith("get") && !m.getName().substring(3).equalsIgnoreCase(idName)){
                if(Set.class.equals(m.getReturnType()) || Map.class.equals(m.getReturnType()))
                    continue;
                methods.add(m);
            }
        }
        
        return methods.toArray(new Method[0]);
    }
}
