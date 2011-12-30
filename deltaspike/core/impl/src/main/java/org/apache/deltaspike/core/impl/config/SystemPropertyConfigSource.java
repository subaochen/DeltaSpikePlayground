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
package org.apache.deltaspike.core.impl.config;

import org.apache.deltaspike.core.spi.config.ConfigSource;

/**
 * {@link ConfigSource} which uses System#getProperty
 */
class SystemPropertyConfigSource extends ConfigSource
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected int getDefaultOrdinal()
    {
        return 100;
    }

    /**
     * The given key gets used for a lookup via System#getProperty
     *
     * @param key for the property
     * @return value for the given key or null if there is no configured value
     */
    @Override
    public String getPropertyValue(String key)
    {
        return System.getProperty(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getConfigName()
    {
        return "system-properties";
    }
}