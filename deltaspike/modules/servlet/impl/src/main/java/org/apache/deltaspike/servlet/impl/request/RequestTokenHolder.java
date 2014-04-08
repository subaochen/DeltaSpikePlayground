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

import org.apache.deltaspike.core.api.common.DeltaSpike;
import org.apache.deltaspike.servlet.api.request.RequestToken;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletRequest;

@Dependent
@Named(DoubleSubmitPreventionFilter.NEXT_REQUEST_TOKE_KEY)
public class RequestTokenHolder implements RequestToken
{
    @Inject
    @DeltaSpike
    private ServletRequest servletRequest;

    public String getKey()
    {
        return DoubleSubmitPreventionFilter.REQUEST_TOKEN_KEY;
    }

    public void setKey(String key)
    {
        //do nothing
    }

    @Override
    public String getValue()
    {
        return (String)servletRequest.getAttribute(DoubleSubmitPreventionFilter.NEXT_REQUEST_TOKE_KEY);
    }

    public void setValue(String value)
    {
        //do nothing
    }
}
