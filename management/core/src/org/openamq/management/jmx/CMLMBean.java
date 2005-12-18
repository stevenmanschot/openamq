/*****************************************************************************
 * Filename    : CMLMBean.java
 * Date Created: ${date}
 *****************************************************************************
 * (c) Copyright JP Morgan Chase Ltd 2005. All rights reserved. No part of
 * this program may be photocopied reproduced or translated to another
 * program language without prior written consent of JP Morgan Chase Ltd
 *****************************************************************************/
package org.openamq.management.jmx;

import org.apache.log4j.Logger;
import org.openamq.schema.cml.FieldType;
import org.openamq.schema.cml.InspectReplyType;

import javax.management.*;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.OpenMBeanInfoSupport;
import java.util.Hashtable;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class CMLMBean implements DynamicMBean
{
    private static final Logger _log = Logger.getLogger(CMLMBean.class);

    /**
     * Name of the attribute for the parent MBean
     */
    public static final String PARENT_ATTRIBUTE = "__parent";

    private OpenMBeanInfoSupport _mbeanInfo;

    private AMQMBeanInfo _extraMbeanInfo;

    private InspectReplyType _inspectReply;

    private CMLMBean _parent;

    private ObjectName _objectName;

    public CMLMBean(CMLMBean parent, OpenMBeanInfoSupport mbeanInfo, AMQMBeanInfo extraMbeanInfo,
                    InspectReplyType inspectReply)
    {
        _mbeanInfo = mbeanInfo;
        _extraMbeanInfo = extraMbeanInfo;
        _inspectReply = inspectReply;
        _parent = parent;
    }

    /**
     * Utility method that populates all the type infos up to the root. Used when
     * constructing the ObjectName.
     * We end up with properties of the form "className", "objectId" in the map.
     * @param leaf the child node. Must not be null. Note that the child types are not populated since the
     * convention is different for the child where instead of "className" the word "type" is
     * used. See the JMX Best Practices document on the Sun JMX website for details.
     * @param properties
     */
    public static void populateAllTypeInfo(Hashtable<String, String> properties, CMLMBean leaf)
    {
        CMLMBean current = leaf.getParent();
        while (current != null)
        {
            properties.put(current.getType(), Integer.toString(current.getObjectId()));
            current = current.getParent();
        }
    }

    public String getType()
    {
        return _inspectReply.getClass1();
    }

    public int getObjectId()
    {
        return _inspectReply.getObject2();
    }

    public InspectReplyType getInspectReply()
    {
        return _inspectReply;
    }

    public CMLMBean getParent()
    {
        return _parent;
    }

    public ObjectName getObjectName()
    {
        return _objectName;
    }

    public void setObjectName(ObjectName objectName)
    {
        _objectName = objectName;
    }

    public Object getAttribute(String attribute)
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        if (PARENT_ATTRIBUTE.equals(attribute))
        {
            if (_parent == null)
            {
                return null;
            }
            else
            {
                return _parent.getObjectName();
            }
        }
        String nsDecl = "declare namespace cml='http://www.openamq.org/schema/cml';";
        FieldType[] fields = (FieldType[]) _inspectReply.selectPath(nsDecl + "$this/cml:field[@name='" +
                                                                    attribute + "']");
        if (fields == null || fields.length == 0)
        {
            throw new AttributeNotFoundException("Attribute " + attribute + " not found");
        }
        else
        {
            OpenMBeanAttributeInfo attrInfo = _extraMbeanInfo.getAttributeInfo(attribute);
            OpenType openType = attrInfo.getOpenType();
            String value = fields[0].getStringValue();
            try
            {
                return createAttributeValue(openType, value, attrInfo.getName());
            }
            catch (MalformedObjectNameException e)
            {
                throw new MBeanException(e);
            }
        }
    }

    private Object createAttributeValue(OpenType openType, String value, String mbeanType)
            throws MalformedObjectNameException
    {
        if (openType.equals(SimpleType.STRING))
        {
            return value;
        }
        else if (openType.equals(SimpleType.BOOLEAN))
        {
            return Boolean.valueOf(value);
        }
        else if (openType.equals(SimpleType.INTEGER))
        {
            return Integer.valueOf(value);
        }
        else if (openType.equals(SimpleType.DOUBLE))
        {
            return Double.valueOf(value);
        }
        else if (openType.equals(SimpleType.OBJECTNAME))
        {
            Hashtable<String, String> props = new Hashtable<String, String>();
            props.put("objectid", value);
            props.put("type", mbeanType);
            // this populates all type info for parents
            populateAllTypeInfo(props, this);
            // add in type info for this level. This information is available from the inspect reply xml fragment
            props.put(_inspectReply.getClass1(), Integer.toString(_inspectReply.getObject2()));
            return new ObjectName(JmxConstants.JMX_DOMAIN, props);
        }
        else
        {
            _log.warn("Unsupported open type: " + openType + " - returning string value");
            return value;
        }
    }

    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException,
                                                         MBeanException, ReflectionException
    {

    }

    public AttributeList getAttributes(String[] attributes)
    {
        AttributeList al = new AttributeList(attributes.length);
        for (String name : attributes)
        {
            try
            {
                Object value = getAttribute(name);
                final Attribute attr = new Attribute(name, value);
                al.add(attr);
            }
            catch (Exception e)
            {
                _log.error("Unable to get value for attribute: " + name, e);
            }
        }
        return al;
    }

    public AttributeList setAttributes(AttributeList attributes)
    {
        return null;
    }

    public Object invoke(String actionName, Object params[], String signature[]) throws MBeanException,
                                                                                        ReflectionException
    {
        return null;
    }

    public MBeanInfo getMBeanInfo()
    {
        return _mbeanInfo;
    }
}
