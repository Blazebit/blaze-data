//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.3 in JDK 1.6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2011.07.17 at 08:09:40 PM CEST 
//


package com.blazebit.data.cfg;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;choice maxOccurs="unbounded" minOccurs="0">
 *         &lt;element ref="{}dataProperty"/>
 *         &lt;element ref="{}dataLookup"/>
 *       &lt;/choice>
 *       &lt;attribute name="expression" type="{http://www.w3.org/2001/XMLSchema}boolean" />
 *       &lt;attribute name="name" use="required" type="{http://www.w3.org/2001/XMLSchema}NCName" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "dataPropertyOrDataLookup"
})
@XmlRootElement(name = "dataProperty")
public class DataProperty {

    @XmlElements({
        @XmlElement(name = "dataProperty", type = DataProperty.class),
        @XmlElement(name = "dataLookup", type = DataLookup.class)
    })
    protected List<Object> dataPropertyOrDataLookup;
    @XmlAttribute
    protected Boolean expression;
    @XmlAttribute(required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlSchemaType(name = "NCName")
    protected String name;

    public DataProperty(List<Object> dataPropertyOrDataLookup, String name) {
        this.dataPropertyOrDataLookup = dataPropertyOrDataLookup;
        this.name = name;
    }

    public DataProperty() {
        
    }
    
    /**
     * Gets the value of the dataPropertyOrDataLookup property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the dataPropertyOrDataLookup property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDataPropertyOrDataLookup().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DataProperty }
     * {@link DataLookup }
     * 
     * 
     */
    public List<Object> getDataPropertyOrDataLookup() {
        if (dataPropertyOrDataLookup == null) {
            dataPropertyOrDataLookup = new ArrayList<Object>();
        }
        return this.dataPropertyOrDataLookup;
    }

    /**
     * Gets the value of the expression property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isExpression() {
        if(expression == null){
            return false;
        }
        return expression;
    }

    /**
     * Sets the value of the expression property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setExpression(Boolean value) {
        this.expression = value;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        if(name == null)
            return "";
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

}
