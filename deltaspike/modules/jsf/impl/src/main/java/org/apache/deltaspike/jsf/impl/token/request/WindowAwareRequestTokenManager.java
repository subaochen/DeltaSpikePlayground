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
package org.apache.deltaspike.jsf.impl.token.request;

import org.apache.deltaspike.core.api.scope.WindowScoped;

import javax.annotation.PreDestroy;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@WindowScoped
public class WindowAwareRequestTokenManager implements Serializable
{
    private static final String REQUEST_TOKEN_SET_KEY = "deltaspike.REQUEST_TOKENS";

    private static final String NEXT_REQUEST_TOKE_KEY = "dsCurrentRequestToken";

    private Set<String> requestTokenSet = new CopyOnWriteArraySet<String>();

    public synchronized void syncActiveTokens()
    {
        FacesContext facesContext = FacesContext.getCurrentInstance();

        //sync tokens
        Set<String> requestTokens =
            (Set<String>)facesContext.getExternalContext().getSessionMap().get(REQUEST_TOKEN_SET_KEY);

        if (requestTokens == null)
        {
            return;
        }

        //we can't use a removed token-event since the window-scope isn't active at that point -> manual cleanup needed
        for (String requestToken : this.requestTokenSet)
        {
            if (!requestTokens.contains(requestToken))
            {
                this.requestTokenSet.remove(requestToken);
            }
        }

        Object currentToken = facesContext.getExternalContext().getRequestMap().get(NEXT_REQUEST_TOKE_KEY);

        if (currentToken instanceof String)
        {
            this.requestTokenSet.add((String)currentToken);
        }
    }

    //in case of session-timeouts there is no benefit to call this method
    //(it's triggered because the window-scope is based on the current session)
    //however, we need @PreDestroy for this method for an earlier cleanup (if needed)
    //e.g. in case of a manually closed window...
    @PreDestroy
    public synchronized void cleanup()
    {
        FacesContext facesContext = FacesContext.getCurrentInstance();

        //just happens in case of a session-timeout -> this pre-destroy method is called, but not needed.
        //the whole session (including window-scoped beans) gets dropped -> it's ok to skip the cleanup at this point.
        if (facesContext == null)
        {
            return;
        }

        Set<String> requestTokens =
            (Set<String>)facesContext.getExternalContext().getSessionMap().get(REQUEST_TOKEN_SET_KEY);

        if (requestTokens == null)
        {
            return;
        }

        //only drop the tokens for the current window
        for (String requestToken : this.requestTokenSet)
        {
            requestTokens.remove(requestToken);
        }

        this.requestTokenSet.clear();
    }
}
