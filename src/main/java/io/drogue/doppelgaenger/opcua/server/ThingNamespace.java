package io.drogue.doppelgaenger.opcua.server;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFragment;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.drogue.doppelgaenger.opcua.client.Client;

public class ThingNamespace implements AddressSpaceFragment {

    private static final Logger logger = LoggerFactory.getLogger(ThingNamespace.class);

    private static final String NAMESPACE_URI = "https://drogue.io/doppelgänger/things";

    private final Client client;

    private final PropertyNamespace propertyNamespace;

    private final UShort namespaceIndex;

    ThingNamespace(final OpcUaServer server, final PropertyNamespace propertyNamespace, final Client client) {
        this.client = client;
        this.propertyNamespace = propertyNamespace;
        this.namespaceIndex = server.getNamespaceTable().addUri(NAMESPACE_URI);
    }

    @Override
    public AddressSpaceFilter getFilter() {
        return new NamespaceIndexFilter(this.namespaceIndex, false);
    }

    @Override
    public void read(final ReadContext context, final Double maxAge, final TimestampsToReturn timestamps, final List<ReadValueId> readValueIds) {
        logger.debug("read: {}", readValueIds);

        final var result = new ArrayList<DataValue>();
        final var ids = new LinkedList<>(readValueIds);

        completedFuture(null)
                .thenCompose(x -> handleRead(ids, result))
                .whenComplete((x, err) -> {
                    logger.debug("read complete", err);
                    try {
                        if (err != null) {
                            context.failure(StatusCode.BAD);
                        } else {
                            context.success(result);
                        }
                    } catch (final Exception e) {
                        logger.info("Failed to complete read", e);
                    }

                });
    }

    CompletableFuture<Void> handleRead(final LinkedList<ReadValueId> ids, final List<DataValue> result) {
        ReadValueId next = null;
        if (!ids.isEmpty()) {
            next = ids.pop();
        }

        if (next == null) {
            return completedFuture(null);
        } else {
            final var node = ThingNode.fromId(this.client, this, this.propertyNamespace, next.getNodeId());
            return node
                    .readAttribute(next.getAttributeId())
                    .handle((value, err) -> {
                        if (value != null) {
                            result.add(value);
                        } else {
                            result.add(new DataValue(StatusCode.BAD));
                        }
                        return handleRead(ids, result);
                    })
                    // take the future and wait for it
                    .thenCompose(x -> x);
        }
    }

    @Override
    public void write(final WriteContext context, final List<WriteValue> writeValues) {
        context.failure(StatusCode.BAD);
    }

    @Override
    public void onDataItemsCreated(final List<DataItem> dataItems) {
    }

    @Override
    public void onDataItemsModified(final List<DataItem> dataItems) {
    }

    @Override
    public void onDataItemsDeleted(final List<DataItem> dataItems) {
    }

    @Override
    public void onMonitoringModeChanged(final List<MonitoredItem> monitoredItems) {
    }

    @Override
    public void browse(final BrowseContext context, final ViewDescription view, final NodeId nodeId) {
        logger.debug("browse: {}", nodeId);

        if (nodeId.getNamespaceIndex().equals(this.namespaceIndex)) {
            // client node
            completedFuture(ThingNode.fromId(this.client, this, this.propertyNamespace, nodeId))
                    .thenCompose(ThingNode::browse)
                    .whenComplete((result, err) -> {
                        logger.debug("Browse - result: {}", result, err);
                        try {
                            if (result != null) {
                                context.success(result);
                            } else {
                                context.failure(StatusCode.BAD);
                            }
                        } catch (final Exception e) {
                            logger.info("Failed to browse", e);
                        }
                    })
            ;
        } else {
            context.success(List.of());
        }

    }

    @Override
    public void getReferences(final BrowseContext context, final ViewDescription view, final NodeId nodeId) {
        logger.debug("getReferences: {}", nodeId);
        if (nodeId.equals(Identifiers.ObjectsFolder)) {
            context.success(List.of(
                    new Reference(
                            Identifiers.ObjectsFolder,
                            Identifiers.Organizes,
                            thingNodeId("/"),
                            true)));
        } else if (nodeId.getNamespaceIndex().equals(this.namespaceIndex)) {
            // client node
            completedFuture(ThingNode.fromId(this.client, this, this.propertyNamespace, nodeId))
                    .thenCompose(ThingNode::getReferences)
                    .whenComplete((result, err) -> {
                        if (result != null) {
                            context.success(result);
                        } else {
                            context.failure(StatusCode.BAD);
                        }
                    })
            ;

        } else {
            context.success(List.of());
        }
    }

    ExpandedNodeId thingNodeId(final String name) {
        return new ExpandedNodeId(this.namespaceIndex, NAMESPACE_URI, name);
    }
}
