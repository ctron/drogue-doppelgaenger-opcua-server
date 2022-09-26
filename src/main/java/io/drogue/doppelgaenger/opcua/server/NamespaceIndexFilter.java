package io.drogue.doppelgaenger.opcua.server;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFilter;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.AddNodesItem;
import org.eclipse.milo.opcua.stack.core.types.structured.AddReferencesItem;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.DeleteNodesItem;
import org.eclipse.milo.opcua.stack.core.types.structured.DeleteReferencesItem;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryUpdateDetails;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;

public class NamespaceIndexFilter implements AddressSpaceFilter {

    private final UShort index;

    private final boolean enableSubscribe;

    NamespaceIndexFilter(final UShort index, final boolean enableSubscribe) {
        this.index = index;
        this.enableSubscribe = enableSubscribe;
    }

    @Override
    public boolean filterBrowse(final OpcUaServer server, final NodeId nodeId) {
        return this.index.equals(nodeId.getNamespaceIndex());
    }

    @Override
    public boolean filterRegisterNode(final OpcUaServer server, final NodeId nodeId) {
        return this.index.equals(nodeId.getNamespaceIndex());
    }

    @Override
    public boolean filterUnregisterNode(final OpcUaServer server, final NodeId nodeId) {
        return this.index.equals(nodeId.getNamespaceIndex());
    }

    @Override
    public boolean filterRead(final OpcUaServer server, final ReadValueId readValueId) {
        return this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterWrite(final OpcUaServer server, final WriteValue writeValue) {
        return this.index.equals(writeValue.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterHistoryRead(final OpcUaServer server, final HistoryReadValueId historyReadValueId) {
        return false;
    }

    @Override
    public boolean filterHistoryUpdate(final OpcUaServer server, final HistoryUpdateDetails historyUpdateDetails) {
        return false;
    }

    @Override
    public boolean filterCall(final OpcUaServer server, final CallMethodRequest callMethodRequest) {
        return false;
    }

    @Override
    public boolean filterOnCreateDataItem(final OpcUaServer server, final ReadValueId readValueId) {
        return this.enableSubscribe && this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterOnModifyDataItem(final OpcUaServer server, final ReadValueId readValueId) {
        return this.enableSubscribe && this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterOnCreateEventItem(final OpcUaServer server, final ReadValueId readValueId) {
        return this.enableSubscribe && this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterOnModifyEventItem(final OpcUaServer server, final ReadValueId readValueId) {
        return this.enableSubscribe && this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterOnDataItemsCreated(final OpcUaServer server, final ReadValueId readValueId) {
        return this.enableSubscribe && this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterOnDataItemsModified(final OpcUaServer server, final ReadValueId readValueId) {
        return this.enableSubscribe && this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterOnDataItemsDeleted(final OpcUaServer server, final ReadValueId readValueId) {
        return this.enableSubscribe && this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterOnEventItemsCreated(final OpcUaServer server, final ReadValueId readValueId) {
        return this.enableSubscribe && this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterOnEventItemsModified(final OpcUaServer server, final ReadValueId readValueId) {
        return this.enableSubscribe && this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterOnEventItemsDeleted(final OpcUaServer server, final ReadValueId readValueId) {
        return this.enableSubscribe && this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterOnMonitoringModeChanged(final OpcUaServer server, final ReadValueId readValueId) {
        return this.enableSubscribe && this.index.equals(readValueId.getNodeId().getNamespaceIndex());
    }

    @Override
    public boolean filterAddNodes(final OpcUaServer server, final AddNodesItem addNodesItem) {
        return false;
    }

    @Override
    public boolean filterDeleteNodes(final OpcUaServer server, final DeleteNodesItem deleteNodesItem) {
        return false;
    }

    @Override
    public boolean filterAddReferences(final OpcUaServer server, final AddReferencesItem addReferencesItem) {
        return false;
    }

    @Override
    public boolean filterDeleteReferences(final OpcUaServer server, final DeleteReferencesItem deleteReferencesItem) {
        return false;
    }
}
