/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.deltaspike.servlet.impl.request;

import org.apache.deltaspike.core.api.config.ConfigResolver;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * This filter is just the first step. It needs additional integration like it's done in the JSF-Module.
 * Otherwise leaks are possible.
 * If it isn't used in combination with JSF, it's just an extensible base
 * (since e.g. resource-requests shouldn't create new tokens)
 */
//Attention: keep in sync with DoubleSubmitAwarePhaseListener and DeltaSpikeExternalContextWrapper
public class DoubleSubmitPreventionFilter implements Filter
{
    static final String NEXT_REQUEST_TOKE_KEY = "dsCurrentRequestToken";
    static final String REQUEST_TOKEN_KEY = "deltaspikeRequestToken";

    private static final String APACHE_PREFIX = "org.apache.";
    private static final String FORCE_SESSION_CREATION_KEY = "deltaspike.FORCE_SESSION_CREATION";
    private static final String SYNC_VIA_SESSION_INSTANCE_KEY = "deltaspike.SYNCHRONIZATION_VIA_SESSION_INSTANCE";
    private static final String SESSION_SYNCHRONIZATION_KEY = "deltaspike.SESSION_SYNCHRONIZATION";
    private static final String REQUEST_WITHOUT_TOKEN_VALID_KEY = "deltaspike.REQUEST_WITHOUT_TOKEN_VALID";
    private static final String REQUEST_TOKEN_WITH_PREFIX_KEY = ":" + REQUEST_TOKEN_KEY;
    //for multiple forms on the same page with prependId = false
    private static final String REQUEST_TOKEN_WITH_MANUAL_PREFIX_KEY = "_" + REQUEST_TOKEN_KEY;
    private static final String REQUEST_TOKEN_SET_KEY = "deltaspike.REQUEST_TOKENS";
    private static final String DUPLICATED_SUBMIT_DETECTED_KEY = "deltaspike.DUPLICATED_SUBMIT_DETECTED";
    private static final String DUPLICATED_SUBMIT_DETECTION_ENABLED_KEY = "deltaspike.DSD_ENABLED";

    private static final String JSF_AJAX_REQUEST_HEADER_KEY = "Faces-Request";
    private static final String JSF_AJAX_REQUEST_HEADER_VALUE = "partial/ajax";

    protected boolean forceSessionCreation = true;
    protected boolean synchronizeViaSessionInstance = false;
    protected boolean requestWithoutTokenValid = true;

    @Override
    public void init(FilterConfig config) throws ServletException
    {
        String forceSessionCreationValue =
            config.getServletContext().getInitParameter(APACHE_PREFIX + FORCE_SESSION_CREATION_KEY);
        String syncViaSessionInstanceValue =
            config.getServletContext().getInitParameter(APACHE_PREFIX + SYNC_VIA_SESSION_INSTANCE_KEY);
        String requestWithoutTokenValidValue =
            config.getServletContext().getInitParameter(APACHE_PREFIX + REQUEST_WITHOUT_TOKEN_VALID_KEY);
        
        
        if (forceSessionCreationValue == null)
        {
            forceSessionCreationValue = ConfigResolver.getPropertyValue(FORCE_SESSION_CREATION_KEY, "true");
        }
        if (syncViaSessionInstanceValue == null)
        {
            syncViaSessionInstanceValue = ConfigResolver.getPropertyValue(SYNC_VIA_SESSION_INSTANCE_KEY, "false");
        }
        if (requestWithoutTokenValidValue == null)
        {
            requestWithoutTokenValidValue = ConfigResolver.getPropertyValue(REQUEST_WITHOUT_TOKEN_VALID_KEY, "true");
        }

        this.forceSessionCreation = Boolean.parseBoolean(forceSessionCreationValue.trim());
        this.synchronizeViaSessionInstance = Boolean.parseBoolean(syncViaSessionInstanceValue.trim());
        this.requestWithoutTokenValid = Boolean.parseBoolean(requestWithoutTokenValidValue.trim());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException
    {
        if (!(request instanceof HttpServletRequest))
        {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = ((HttpServletRequest) request).getSession(forceSessionCreation);
        HttpServletRequest httpRequest = (HttpServletRequest)request;

        createNextRequestToken(httpRequest, session);
        httpRequest.setAttribute(DUPLICATED_SUBMIT_DETECTION_ENABLED_KEY, Boolean.TRUE);

        if (!"POST".equalsIgnoreCase(httpRequest.getMethod()))
        {
            chain.doFilter(request, response);
            return;
        }

        /*
         * Handle POST-Requests
         */

        String currentRequestToken = request.getParameter(REQUEST_TOKEN_KEY);

        if (currentRequestToken == null && request.getParameterMap() != null)
        {
            currentRequestToken = findRequestToken(request);
        }

        if (session != null)
        {
            Object lock;

            if (this.synchronizeViaSessionInstance)
            {
                lock = session;
            }
            else
            {
                lock = session.getAttribute(SESSION_SYNCHRONIZATION_KEY);

                if (lock == null)
                {
                    synchronized (session) //might not be enough in rare edge-cases
                    {
                        lock = new Object();
                        session.setAttribute(SESSION_SYNCHRONIZATION_KEY, lock);
                    }
                }
            }

            synchronized (lock)
            {
                if (!isValidRequest(request, session, currentRequestToken))
                {
                    handleDuplicatedSubmit(httpRequest, response, session, chain);
                }
                else
                {
                    chain.doFilter(request, response);
                }
            }
        }
        else
        {
            chain.doFilter(request, response);
        }
    }

    protected String findRequestToken(ServletRequest request)
    {
        for (String parameterKey : ((Map<String, String>)request.getParameterMap()).keySet())
        {
            if (parameterKey.endsWith(REQUEST_TOKEN_WITH_PREFIX_KEY) ||
                parameterKey.endsWith(REQUEST_TOKEN_WITH_MANUAL_PREFIX_KEY))
            {
                return request.getParameter(parameterKey);
            }
        }
        return null;
    }

    protected void createNextRequestToken(HttpServletRequest request, HttpSession session)
    {
        if (session == null || !createRequestTokenFor(request))
        {
            return;
        }

        Set<String> requestTokens = (Set<String>)session.getAttribute(REQUEST_TOKEN_SET_KEY);

        if (requestTokens == null)
        {
            requestTokens = new CopyOnWriteArraySet<String>();
            session.setAttribute(REQUEST_TOKEN_SET_KEY, requestTokens);
        }

        String newToken = UUID.randomUUID().toString().replace("-", "");
        requestTokens.add(newToken);
        request.setAttribute(NEXT_REQUEST_TOKE_KEY, newToken);
    }

    protected boolean createRequestTokenFor(HttpServletRequest request)
    {
        return !request.getRequestURI().contains("javax.faces.resource");
    }

    protected void handleDuplicatedSubmit(HttpServletRequest request,
                                          ServletResponse response,
                                          HttpSession session,
                                          FilterChain chain)
        throws IOException, ServletException
    {
        if (isSynchronizedAjaxRequest(request))
        {
            return;
        }

        //marker which leads to a re-rendering of the page (caused by a page refresh after a form submit)
        //in case of JSF see DoubleSubmitAwarePhaseListener
        request.setAttribute(DUPLICATED_SUBMIT_DETECTED_KEY, Boolean.TRUE);

        Set<String> requestTokens = (Set<String>)session.getAttribute(REQUEST_TOKEN_SET_KEY);

        if (requestTokens != null)
        {
            Set<String> requestTokensBeforeCleanup = new HashSet<String>(requestTokens);

            try
            {
                chain.doFilter(request, response);
            }
            finally
            {
                if (requestTokensBeforeCleanup.equals(requestTokens)) /*same size and content*/
                {
                    //no window specific cleanup was performed -> drop everything
                    //since the page refresh will create a new token anyway, but re-add the current
                    //otherwise we would create a leak
                    requestTokens.clear();
                    requestTokens.add((String)request.getAttribute(NEXT_REQUEST_TOKE_KEY));
                }
            }
        }
        else
        {
            chain.doFilter(request, response);
        }
    }

    protected boolean isValidRequest(ServletRequest request, HttpSession session, String currentRequestToken)
    {
        if (currentRequestToken == null)
        {
            return this.requestWithoutTokenValid;
        }

        Set<String> requestTokens = (Set<String>)session.getAttribute(REQUEST_TOKEN_SET_KEY);
        return requestTokens.remove(currentRequestToken);
    }

    protected boolean isSynchronizedAjaxRequest(HttpServletRequest request)
    {
        //JSF uses client-site AJAX-Request queueing -> no duplicated AJAX-Requests to handle
        return JSF_AJAX_REQUEST_HEADER_VALUE.equals(request.getHeader(JSF_AJAX_REQUEST_HEADER_KEY));
    }

    @Override
    public void destroy()
    {
    }
}
