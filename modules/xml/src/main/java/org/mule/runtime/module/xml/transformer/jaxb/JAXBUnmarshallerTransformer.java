/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.xml.transformer.jaxb;

import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.core.api.lifecycle.InitialisationException;
import org.mule.runtime.core.api.transformer.TransformerException;
import org.mule.runtime.core.config.i18n.CoreMessages;
import org.mule.runtime.core.transformer.AbstractTransformer;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;

import org.w3c.dom.Node;

/**
 * Allows un-marshaling of XML generated by JAXB to a Java object graph. By default
 * the returnType for this transformer is {@link Object}. If a specific returnType is
 * set and no external {@link javax.xml.bind.JAXBContext} is set on the transformer,
 * then a {@link javax.xml.bind.JAXBContext} will be created using the returnType.
 *
 * @since 3.0
 */
public class JAXBUnmarshallerTransformer extends AbstractTransformer
{
    protected JAXBContext jaxbContext;

    public JAXBUnmarshallerTransformer()
    {
        registerSourceType(DataType.STRING);
        registerSourceType(DataType.fromType(Writer.class));
        registerSourceType(DataType.fromType(File.class));
        registerSourceType(DataType.fromType(URL.class));
        registerSourceType(DataType.fromType(Node.class));
        registerSourceType(DataType.INPUT_STREAM);
        registerSourceType(DataType.fromType(Source.class));
        registerSourceType(DataType.fromType(XMLStreamReader.class));
        registerSourceType(DataType.fromType(XMLEventReader.class));
    }

    public JAXBUnmarshallerTransformer(JAXBContext jaxbContext, DataType<?> returnType)
    {
        this();
        this.jaxbContext = jaxbContext;
        setReturnDataType(returnType);
    }

    @Override
    public void initialise() throws InitialisationException
    {
        super.initialise();
        if (jaxbContext == null)
        {
            if(Object.class.equals(getReturnDataType().getType()))
            {
                throw new InitialisationException(CoreMessages.objectIsNull("jaxbContext"), this);
            }
            else
            {
                try
                {
                    jaxbContext = JAXBContext.newInstance(getReturnDataType().getType());
                }
                catch (JAXBException e)
                {
                    throw new InitialisationException(e, this);
                }
            }
        }
    }

    @Override
    protected Object doTransform(Object src, String outputEncoding) throws TransformerException
    {
        try
        {
            final Unmarshaller u = jaxbContext.createUnmarshaller();
            Object result = null;
            if (src instanceof String)
            {
                result = u.unmarshal(new StringReader((String) src));
            }
            else if (src instanceof File)
            {
                result = u.unmarshal((File) src);
            }
            else if (src instanceof URL)
            {
                result = u.unmarshal((URL) src);
            }
            else if (src instanceof InputStream)
            {
                result = u.unmarshal((InputStream) src);
            }
            else if (src instanceof Node)
            {
                result = u.unmarshal((Node) src, getReturnDataType().getType());
            }
            else if (src instanceof Source)
            {
                result = u.unmarshal((Source) src, getReturnDataType().getType());
            }
            else if (src instanceof XMLStreamReader)
            {
                result = u.unmarshal((XMLStreamReader) src, getReturnDataType().getType());
            }
            else if (src instanceof XMLEventReader)
            {
                result = u.unmarshal((XMLEventReader) src, getReturnDataType().getType());
            }
            if (result != null)
            {
                // If we get a JAXB element, return its contents
                if (result instanceof JAXBElement)
                {
                    result = ((JAXBElement)result).getValue();
                }
            }
            return result;
        }
        catch (Exception e)
        {
            throw new TransformerException(this, e);
        }
    }

    public JAXBContext getJaxbContext()
    {
        return jaxbContext;
    }

    public void setJaxbContext(JAXBContext jaxbContext)
    {
        this.jaxbContext = jaxbContext;
    }
}
