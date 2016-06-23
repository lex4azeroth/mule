/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.el.mvel.datatype;

import static java.nio.charset.StandardCharsets.UTF_16;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mule.mvel2.MVEL.compileExpression;
import static org.mule.runtime.api.metadata.MimeType.JSON;
import static org.mule.runtime.core.el.mvel.MessageVariableResolverFactory.MESSAGE_PAYLOAD;
import static org.mule.runtime.core.el.mvel.MessageVariableResolverFactory.PAYLOAD;
import static org.mule.tck.junit4.matcher.DataTypeMatcher.like;

import org.mule.mvel2.ParserContext;
import org.mule.mvel2.compiler.CompiledExpression;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.el.mvel.MVELExpressionLanguage;
import org.mule.tck.junit4.AbstractMuleContextTestCase;

import org.junit.Test;

public class PayloadExpressionDataTypeResolverTestCase extends AbstractMuleContextTestCase
{

    public static final String CUSTOM_ENCODING = UTF_16.name();

    private final PayloadExpressionDataTypeResolver dataTypeResolver = new PayloadExpressionDataTypeResolver();

    @Test
    public void returnsPayloadDataType() throws Exception
    {
        doPayloadDataTypeTest(PAYLOAD);
    }

    @Test
    public void returnsMessagePayloadDataType() throws Exception
    {
        doPayloadDataTypeTest(MESSAGE_PAYLOAD);
    }

    private void doPayloadDataTypeTest(String expression) throws Exception
    {
        final DataType expectedDataType = DataType.builder().type(String.class).mimeType(JSON).encoding(CUSTOM_ENCODING).build();

        MVELExpressionLanguage expressionLanguage = (MVELExpressionLanguage) muleContext.getExpressionLanguage();
        final CompiledExpression compiledExpression = (CompiledExpression) compileExpression(expression, new ParserContext(expressionLanguage.getParserConfiguration()));

        MuleEvent testEvent = getTestEvent(TEST_MESSAGE);

        testEvent.setMessage(testEvent.getMessage().transform(msg -> {
            msg.setPayload(TEST_MESSAGE, expectedDataType);
            return msg;
        }));

        assertThat(dataTypeResolver.resolve(testEvent, compiledExpression), like(String.class, JSON, CUSTOM_ENCODING));
    }
}
