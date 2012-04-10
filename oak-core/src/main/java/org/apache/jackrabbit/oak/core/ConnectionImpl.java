/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.core;

import org.apache.jackrabbit.mk.MicroKernelFactory;
import org.apache.jackrabbit.mk.api.MicroKernel;
import org.apache.jackrabbit.mk.model.NodeState;
import org.apache.jackrabbit.mk.model.NodeStateEditor;
import org.apache.jackrabbit.mk.model.NodeStore;
import org.apache.jackrabbit.oak.api.AuthInfo;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.Connection;
import org.apache.jackrabbit.oak.api.QueryEngine;
import org.apache.jackrabbit.oak.kernel.KernelNodeStore;
import org.apache.jackrabbit.oak.query.QueryEngineImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.SimpleCredentials;
import java.io.IOException;

/**
 * ConnectionImpl...
 */
public class ConnectionImpl implements Connection {

    /**
     * logger instance
     */
    private static final Logger log = LoggerFactory.getLogger(ConnectionImpl.class);

    private final SimpleCredentials credentials;
    private final String workspaceName;
    private final NodeStore store;
    private final QueryEngine queryEngine;

    private NodeState root;

    public ConnectionImpl(SimpleCredentials credentials, String workspaceName,
            NodeStore store, NodeState root, QueryEngine queryEngine) {
        this.credentials = credentials;
        this.workspaceName = workspaceName;
        this.store = store;
        this.root = root;
        this.queryEngine = queryEngine;
    }

    static Connection createWorkspaceConnection(SimpleCredentials credentials,
            String workspace, boolean create, String microKernelUrl, String revision)
            throws NoSuchWorkspaceException {

        MicroKernel microKernel = MicroKernelFactory.getInstance(microKernelUrl);

        // FIXME: workspace setup must be done elsewhere...
        if (create) {
            String headRev = microKernel.getHeadRevision();
            if (!microKernel.nodeExists('/' + workspace, headRev)) {
                microKernel.commit("/", "+ \"" + workspace + "\" : {}", headRev, null);
            }
        }

        NodeStore store = new KernelNodeStore(microKernel); // TODO: pass revision?
        NodeState wspRoot = store.getRoot().getChildNode(workspace);
        if (wspRoot == null) {
            throw new NoSuchWorkspaceException(workspace);
        }
        QueryEngine queryEngine = new QueryEngineImpl(microKernel);

        return new ConnectionImpl(credentials, workspace, store, wspRoot, queryEngine);
    }

    @Override
    public AuthInfo getAuthInfo() {
        // todo implement getAuthInfo
        return new AuthInfo() {
            @Override
            public String getUserID() {
                return credentials.getUserID();
            }

            @Override
            public String[] getAttributeNames() {
                return credentials.getAttributeNames();
            }

            @Override
            public Object getAttribute(String attributeName) {
                return credentials.getAttribute(attributeName);
            }
        };
    }

    @Override
    public NodeState getCurrentRoot() {
        return root;
    }

    @Override
    public NodeState commit(NodeStateEditor editor) throws CommitFailedException {
        return store.merge(editor, editor.getBaseNodeState());
    }

    @Override
    public NodeStateEditor getNodeStateEditor(NodeState state) {
        return store.branch(state);
    }

    @Override
    public void close() throws IOException {
        // todo implement close
    }

    @Override
    public String getWorkspaceName() {
        return workspaceName;
    }

    @Override
    public Connection getRepositoryConnection() {
        return new ConnectionImpl(credentials, null, store, store.getRoot(), queryEngine);
    }

    @Override
    public QueryEngine getQueryEngine() {
        return queryEngine;
    }

}