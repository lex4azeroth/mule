/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.http.internal.listener;

import static org.slf4j.LoggerFactory.getLogger;
import org.mule.api.MessagingException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.processor.MessageProcessor;
import org.mule.execution.AsyncResponseFlowProcessingPhaseTemplate;
import org.mule.execution.ResponseCompletionCallback;
import org.mule.execution.ThrottlingPhaseTemplate;
import org.mule.module.http.internal.domain.InputStreamHttpEntity;
import org.mule.module.http.internal.domain.response.HttpResponse;
import org.mule.module.http.internal.listener.async.HttpResponseReadyCallback;
import org.mule.module.http.internal.listener.async.ResponseStatusCallback;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.slf4j.Logger;

public class HttpMessageProcessorTemplate implements AsyncResponseFlowProcessingPhaseTemplate, ThrottlingPhaseTemplate
{

    public static final int MESSAGE_DISCARD_STATUS_CODE = Integer.valueOf(System.getProperty("mule.transport.http.throttling.discardstatuscode","429"));
    public static final String MESSAGE_DISCARD_MESSAGE_BODY = "API calls exceeded";
    public static final String MESSAGE_DISCARD_REASON_PHRASE = "Too Many Requests";
    public static final String X_RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    public static final String X_RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    public static final String X_RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";


    private static final Logger logger = getLogger(HttpMessageProcessorTemplate.class);
    private MuleEvent sourceMuleEvent;
    private MessageProcessor messageProcessor;
    private HttpResponseReadyCallback responseReadyCallback;
    private HttpResponseBuilder responseBuilder;
    private HttpResponseBuilder errorResponseBuilder;
    private HttpThrottlingHeadersMapBuilder httpThrottlingHeadersMapBuilder = new HttpThrottlingHeadersMapBuilder();

    public HttpMessageProcessorTemplate(MuleEvent sourceMuleEvent,
                                        MessageProcessor messageProcessor,
                                        HttpResponseReadyCallback responseReadyCallback,
                                        HttpResponseBuilder responseBuilder,
                                        HttpResponseBuilder errorResponseBuilder)
    {
        this.sourceMuleEvent = sourceMuleEvent;
        this.messageProcessor = messageProcessor;
        this.responseBuilder = responseBuilder;
        this.errorResponseBuilder = errorResponseBuilder;
        this.responseReadyCallback = responseReadyCallback;
    }

    @Override
    public MuleEvent getMuleEvent() throws MuleException
    {
        return this.sourceMuleEvent;
    }

    @Override
    public MuleEvent routeEvent(MuleEvent muleEvent) throws MuleException
    {
        return messageProcessor.process(muleEvent);
    }

    @Override
    public void afterFailureProcessingFlow(Exception exception)
    {

    }

    @Override
    public void sendResponseToClient(MuleEvent muleEvent, ResponseCompletionCallback responseCompletationCallback) throws MuleException
    {
        try
        {
            final org.mule.module.http.internal.domain.response.HttpResponseBuilder responseBuilder = new org.mule.module.http.internal.domain.response.HttpResponseBuilder();
            addThrottlingHeaders(responseBuilder);
            final HttpResponse httpResponse = this.responseBuilder.build(responseBuilder, muleEvent);
            responseReadyCallback.responseReady(httpResponse, getResponseFailureCallback(responseCompletationCallback, muleEvent));
        }
        catch (Exception e)
        {
            responseCompletationCallback.responseSentWithFailure(e, muleEvent);
        }
    }

    private ResponseStatusCallback getResponseFailureCallback(final ResponseCompletionCallback responseCompletationCallback, final MuleEvent muleEvent)
    {
        return new ResponseStatusCallback()
        {
            @Override
            public void responseSendFailure(Throwable throwable)
            {
                responseCompletationCallback.responseSentWithFailure(getException(throwable), muleEvent);
            }

            @Override
            public void responseSendSuccessfully()
            {
                responseCompletationCallback.responseSentSuccessfully();
            }
        };
    }

    private Exception getException(Throwable throwable)
    {
        if (throwable instanceof Exception)
        {
            return (Exception) throwable;
        }
        return new Exception(throwable);
    }

    @Override
    public void sendFailureResponseToClient(MessagingException messagingException, ResponseCompletionCallback responseCompletationCallback) throws MuleException
    {
        final org.mule.module.http.internal.domain.response.HttpResponseBuilder failureResponseBuilder = new org.mule.module.http.internal.domain.response.HttpResponseBuilder().setStatusCode(500).setReasonPhrase("Internal Server Error");
        addThrottlingHeaders(failureResponseBuilder);
        final HttpResponse response = errorResponseBuilder.build(failureResponseBuilder, messagingException.getEvent());
        responseReadyCallback.responseReady(response, getResponseFailureCallback(responseCompletationCallback, messagingException.getEvent()));
    }

    @Override
    public void discardMessageOnThrottlingExceeded() throws MuleException
    {
        final org.mule.module.http.internal.domain.response.HttpResponseBuilder throttledResponseBuilder = new org.mule.module.http.internal.domain.response.HttpResponseBuilder()
                .setStatusCode(MESSAGE_DISCARD_STATUS_CODE)
                .setReasonPhrase(MESSAGE_DISCARD_REASON_PHRASE)
                .setEntity(new InputStreamHttpEntity(new ByteArrayInputStream(MESSAGE_DISCARD_MESSAGE_BODY.getBytes())));
        addThrottlingHeaders(throttledResponseBuilder);
        responseReadyCallback.responseReady(throttledResponseBuilder.build(), getLogCompletionCallback());
    }

    private void addThrottlingHeaders(org.mule.module.http.internal.domain.response.HttpResponseBuilder throttledResponseBuilder)
    {
        final Map<String, String> throttlingHeaders = getThrottlingHeaders();
        for (String throttlingHeaderName : throttlingHeaders.keySet())
        {
            throttledResponseBuilder.addHeader(throttlingHeaderName, throttlingHeaders.get(throttlingHeaderName));
        }
    }

    private ResponseStatusCallback getLogCompletionCallback()
    {
        return new ResponseStatusCallback()
        {
            @Override
            public void responseSendFailure(Throwable throwable)
            {
                logger.info("Failure sending throttled response " + throwable.getMessage());
                if (logger.isDebugEnabled())
                {
                    logger.debug(throwable.getMessage(), throwable);
                }
            }

            @Override
            public void responseSendSuccessfully()
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("throttled response sent successfully");
                }
            }
        };
    }

    @Override
    public void setThrottlingPolicyStatistics(long remainingRequestInCurrentPeriod, long maximumRequestAllowedPerPeriod, long timeUntilNextPeriodInMillis)
    {
        httpThrottlingHeadersMapBuilder.setThrottlingPolicyStatistics(remainingRequestInCurrentPeriod, maximumRequestAllowedPerPeriod, timeUntilNextPeriodInMillis);
    }

    private Map<String,String> getThrottlingHeaders()
    {
        return httpThrottlingHeadersMapBuilder.build();
    }
}
