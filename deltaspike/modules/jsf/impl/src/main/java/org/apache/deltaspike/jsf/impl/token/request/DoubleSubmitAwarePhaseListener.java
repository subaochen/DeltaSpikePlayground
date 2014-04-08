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

import org.apache.deltaspike.core.spi.activation.Deactivatable;
import org.apache.deltaspike.core.util.ClassDeactivationUtils;
import org.apache.deltaspike.core.util.ClassUtils;
import org.apache.deltaspike.jsf.api.listener.phase.JsfPhaseListener;

import javax.faces.component.UIComponent;
import javax.faces.component.html.HtmlInputHidden;
import javax.faces.context.FacesContext;
import javax.faces.event.PhaseEvent;
import javax.faces.event.PhaseId;
import javax.faces.event.PhaseListener;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

//TODO add simple jsf-component as alternative to
//<h:inputHidden id="#{dsCurrentRequestToken.key}" value="#{dsCurrentRequestToken.value}"/>
//optional custom separator (to separate the prefix of #{dsCurrentRequestToken.key}): '_' (and not ':')
@JsfPhaseListener(ordinal = 9000)
public class DoubleSubmitAwarePhaseListener implements PhaseListener, Deactivatable
{
    private static final Logger LOG = Logger.getLogger(DoubleSubmitAwarePhaseListener.class.getName());

    //TODO re-visit: move constants to ds-core
    private static final String DUPLICATED_SUBMIT_DETECTION_ENABLED_KEY = "deltaspike.DSD_ENABLED";
    private static final String DUPLICATED_SUBMIT_DETECTED_KEY = "deltaspike.DUPLICATED_SUBMIT_DETECTED";

    private static final String REQUEST_TOKEN_HOLDER_NAME = "dsCurrentRequestToken";
    private static final String REQUEST_TOKEN_KEY_EXPRESSION = "#{" + REQUEST_TOKEN_HOLDER_NAME + ".key}";

    private final boolean active;

    @Inject
    private WindowAwareRequestTokenManager windowAwareRequestTokenManager;

    public DoubleSubmitAwarePhaseListener()
    {
        //TODO replace with smart-feature deactivation extension (see DELTASPIKE-349)
        boolean doubleSubmitPreventionFilterAvailable = ClassUtils.tryToLoadClassForName(
            "org.apache.deltaspike.servlet.impl.request.DoubleSubmitPreventionFilter") != null;
        this.active = ClassDeactivationUtils.isActivated(getClass()) && doubleSubmitPreventionFilterAvailable;

        if (!this.active)
        {
            LOG.info(getClass().getName() + " is not active -> double-submit-prevention can't get used.");

            if (!doubleSubmitPreventionFilterAvailable)
            {
                LOG.info("To enable double-submit-prevention add the servlet-module from Apache DeltaSpike and " +
                    "<h:inputHidden id=\"#{dsCurrentRequestToken.key}\" value=\"#{dsCurrentRequestToken.value}\"/> " +
                    "to your form(s)");
            }
        }
    }

    @Override
    public void afterPhase(PhaseEvent event)
    {
        if (!this.active) //TODO remove it once DELTASPIKE-349 is done
        {
            return;
        }

        if (event.getPhaseId() == PhaseId.RESTORE_VIEW)
        {
            FacesContext facesContext = event.getFacesContext();

            if (isDuplicateSubmitDetectionEnabled(facesContext))
            {
                if (isDuplicatedPostRequestDetected(facesContext)) //POST
                {
                    this.windowAwareRequestTokenManager.cleanup();
                    facesContext.renderResponse();
                }

                if (!facesContext.isPostback()) //GET
                {
                    //in case of GET requests we can drop all prev. tokens of the current window
                    //because the next rendering process will invalidate them anyway
                    this.windowAwareRequestTokenManager.cleanup();
                }
                this.windowAwareRequestTokenManager.syncActiveTokens();
            }
        }
    }

    @Override
    public void beforePhase(PhaseEvent event)
    {
        if (!this.active) //TODO remove it once DELTASPIKE-349 is done
        {
            return;
        }

        if (event.getPhaseId() == PhaseId.RENDER_RESPONSE)
        {
            FacesContext facesContext = event.getFacesContext();

            if (!facesContext.getPartialViewContext().isAjaxRequest() ||
                !isDuplicateSubmitDetectionEnabled(facesContext))
            {
                return;
            }

            //jsf.js prevents double-submits (submit request n+1 will get the latest request-token updated by request n)
            //however, we need to update all request-tokens in the page
            List<String> tokenComponentIds = findTokenComponentIds(facesContext);

            for (String tokenComponentId : tokenComponentIds)
            {
                facesContext.getPartialViewContext().getRenderIds().add(tokenComponentId);
            }
        }
    }

    @Override
    public PhaseId getPhaseId()
    {
        return PhaseId.ANY_PHASE;
    }

    protected List<String> findTokenComponentIds(FacesContext facesContext)
    {
        String tokenId = facesContext.getApplication().evaluateExpressionGet(
            facesContext, REQUEST_TOKEN_KEY_EXPRESSION, String.class);

        List<String> result = new ArrayList<String>();

        for (UIComponent component : facesContext.getViewRoot().getChildren())
        {
            findTokenComponentId(tokenId, result, component);
        }
        return result;
    }

    protected boolean isTokenHolder(UIComponent component, String tokenId)
    {
        return component instanceof HtmlInputHidden && tokenId.equals(component.getId());
    }

    private void findTokenComponentId(String tokenId, List<String> result, UIComponent component)
    {
        if (isTokenHolder(component, tokenId))
        {
            result.add(component.getClientId());
        }
        else
        {
            for (UIComponent child : component.getChildren())
            {
                findTokenComponentId(tokenId, result, child);
            }
        }
    }

    private boolean isDuplicateSubmitDetectionEnabled(FacesContext facesContext)
    {
        return Boolean.TRUE.equals(facesContext.getExternalContext()
            .getRequestMap().get(DUPLICATED_SUBMIT_DETECTION_ENABLED_KEY));
    }

    private boolean isDuplicatedPostRequestDetected(FacesContext facesContext)
    {
        //see DoubleSubmitPreventionFilter for further details
        return Boolean.TRUE.equals(facesContext.getExternalContext()
            .getRequestMap().get(DUPLICATED_SUBMIT_DETECTED_KEY));
    }
}
