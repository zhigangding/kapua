/*******************************************************************************
 * Copyright (c) 2011, 2017 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *  
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.service.datastore;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.message.KapuaMessage;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.KapuaService;
import org.eclipse.kapua.service.config.KapuaConfigurableService;
import org.eclipse.kapua.service.datastore.client.model.InsertResponse;
import org.eclipse.kapua.service.datastore.model.DatastoreMessage;
import org.eclipse.kapua.service.datastore.model.MessageListResult;
import org.eclipse.kapua.service.datastore.model.StorableId;
import org.eclipse.kapua.service.datastore.model.query.MessageQuery;
import org.eclipse.kapua.service.datastore.model.query.StorableFetchStyle;

/**
 * Service responsible for storing and accessing telemetry data generated by devices.
 * 
 * @since 1.0.0
 */
public interface MessageStoreService extends KapuaService, KapuaConfigurableService {

    /**
     * Store the message
     * 
     * @param message
     * @return
     * @throws KapuaException
     * 
     * @since 1.0.0
     */
    InsertResponse store(KapuaMessage<?, ?> message)
            throws KapuaException;

    /**
     * Find message by identifier
     * 
     * @param scopeId
     * @param id
     * @param fetchStyle
     * @return
     * @throws KapuaException
     * 
     * @since 1.0.0
     */
    DatastoreMessage find(KapuaId scopeId, StorableId id, StorableFetchStyle fetchStyle)
            throws KapuaException;

    /**
     * Query for messages objects matching the given query
     * 
     * @param scopeId
     * @param query
     * @return
     * @throws KapuaException
     * 
     * @since 1.0.0
     */
    MessageListResult query(MessageQuery query)
            throws KapuaException;

    /**
     * Get messages count matching the given query
     * 
     * @param scopeId
     * @param query
     * @return
     * @throws KapuaException
     * 
     * @since 1.0.0
     */
    long count(MessageQuery query)
            throws KapuaException;

    /**
     * Delete message by identifier
     * 
     * @param scopeId
     * @param id
     * @throws KapuaException
     * 
     * @since 1.0.0
     */
    void delete(KapuaId scopeId, StorableId id)
            throws KapuaException;

    /**
     * Delete messages matching the given query
     * 
     * @param scopeId
     * @param query
     * @throws KapuaException
     * 
     * @since 1.0.0
     */
    void delete(MessageQuery query)
            throws KapuaException;
}
