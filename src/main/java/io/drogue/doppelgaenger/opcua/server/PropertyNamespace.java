package io.drogue.doppelgaenger.opcua.server;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFragment;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.drogue.doppelgaenger.opcua.ThingsSubscriptionManager;
import io.drogue.doppelgaenger.opcua.client.BasicFeature;
import io.drogue.doppelgaenger.opcua.client.Client;
import io.drogue.doppelgaenger.opcua.client.Thing;

public class PropertyNamespace implements AddressSpaceFragment {

    private static final Logger logger = LoggerFactory.getLogger(PropertyNamespace.class);

    public static final String NAMESPACE_URI = "https://drogue.io/doppelgänger/properties";

    private final UShort namespaceIndex;

    private final ThingsSubscriptionManager subscriptions;

    private final Map<UInteger, ThingsSubscriptionManager.Subscription> dataItems = new ConcurrentHashMap<>();

    private final Client client;

    PropertyNamespace(@NonNull final OpcUaServer server, @NonNull final ThingsSubscriptionManager subscriptions, @NonNull final Client client) {
        this.subscriptions = subscriptions;
        this.namespaceIndex = server.getNamespaceTable().addUri(NAMESPACE_URI);
        this.client = client;
    }

    @Override
    public AddressSpaceFilter getFilter() {
        return new NamespaceIndexFilter(this.namespaceIndex, true);
    }

    @Override
    public void read(final ReadContext context, final Double maxAge, final TimestampsToReturn timestamps, final List<ReadValueId> readValueIds) {
        logger.debug("read: {}", readValueIds);

        final var result = new ArrayList<DataValue>();
        final var ids = new LinkedList<>(readValueIds);

        completedFuture(null)
                .thenCompose(x -> handleRead(ids, result))
                .whenComplete((x, err) -> {

                    logger.debug("read complete: {}", result, err);
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
            final var node = fromId(next.getNodeId());
            if (node == null) {
                logger.info("Failed to parse node ({})", next.getNodeId());
                result.add(new DataValue(StatusCodes.Bad_NodeIdInvalid));
                return handleRead(ids, result);
            } else {
                return node
                        .readAttribute(next.getAttributeId())
                        .handle((value, err) -> {
                            if (err != null) {
                                result.add(new DataValue(StatusCode.BAD));
                            } else {
                                result.add(value);
                            }
                            return handleRead(ids, result);
                        })
                        // take the future and wait for it
                        .thenCompose(x -> x);
            }
        }
    }

    @Override
    public void write(final WriteContext context, final List<WriteValue> writeValues) {
        logger.info("write - {}", writeValues);
        context.failure(StatusCode.BAD);
    }

    @Override
    public void onDataItemsCreated(final List<DataItem> dataItems) {
        logger.debug("onDataItemsCreated: {}", dataItems);

        for (final var item : dataItems) {
            subscribe(item);
        }
    }

    @Override
    public void onDataItemsModified(final List<DataItem> dataItems) {
        logger.debug("onDataItemsModified: {}", dataItems);
    }

    @Override
    public void onDataItemsDeleted(final List<DataItem> dataItems) {
        logger.debug("onDataItemsDeleted: {}", dataItems);

        for (final var item : dataItems) {
            final var dataItem = this.dataItems.remove(item.getId());
            if (dataItem != null) {
                dataItem.close();
            }
        }
    }

    @Override
    public void onMonitoringModeChanged(final List<MonitoredItem> monitoredItems) {
        logger.debug("onMonitoringModeChanged: {}", monitoredItems);
    }

    private void subscribe(final DataItem item) {
        final var node = fromId(item.getReadValueId().getNodeId());

        if (node == null) {
            item.setQuality(new StatusCode(StatusCodes.Bad_NodeIdInvalid));
            return;
        }

        final var name = node.getName();
        final var subscription = this.subscriptions.createSubscription(node.getThing(), name, state -> {
            reportValue(name, item, state);
        });
        // FIXME: we might have more than one subscription on an item
        this.dataItems.put(item.getId(), subscription);
    }

    private static void reportValue(final String name, final DataItem item, final Optional<Thing> state) {
        if (state.isPresent()) {
            final var thing = state.get();

            final BasicFeature merged = thing.mergedState(name).orElse(null);

            logger.debug("reportValue - state: {}, merged: {}", thing, merged);

            if (merged != null) {
                final var value = Values.toDataValue(merged);
                logger.debug("Reporting: {}", value);
                item.setValue(value);
            } else {
                item.setQuality(StatusCode.UNCERTAIN);
            }

        } else {
            item.setQuality(StatusCode.UNCERTAIN);
        }
    }

    @Override
    public void browse(final BrowseContext context, final ViewDescription view, final NodeId nodeId) {
        logger.debug("browse: {}", nodeId);

        context.success(List.of());
    }

    @Override
    public void getReferences(final BrowseContext context, final ViewDescription view, final NodeId nodeId) {
        logger.debug("getReferences: {}", nodeId);

        if (nodeId.getNamespaceIndex().equals(this.namespaceIndex)) {
            // client node

            final var node = fromId(nodeId);
            if (node != null) {
                completedFuture(node)
                        .thenCompose(PropertyNode::getReferences)
                        .whenComplete((result, err) -> {
                            logger.debug("getReferences: {}", result, err);
                            if (result != null) {
                                context.success(result);
                            } else {
                                context.failure(StatusCode.BAD);
                            }
                        });

                // return before calling context
                return;
            }
        }

        context.success(List.of());
    }

    NodeId propertyNodeId(final String thing, final String name) {
        return propertyNodeIdJoin(this.namespaceIndex, thing, name);
    }

    static NodeId propertyNodeIdJoin(final UShort namespaceIndex, final String thing, final String name) {
        final var s = thing + "#" + URLEncoder.encode(name, StandardCharsets.UTF_8);
        return new NodeId(namespaceIndex, s);
    }

    /**
     * Create a node instance from a node id.
     * <p>
     * This is the inverse operation of {@link PropertyNamespace#propertyNodeId(String, String)}.
     *
     * @param id The node id.
     * @return The node, or {@code null} if it wasn't a valid id.
     */
    PropertyNode fromId(final NodeId id) {
        if (!id.getNamespaceIndex().equals(this.namespaceIndex)) {
            return null;
        }

        try {
            final var split = splitNodeId(id.getIdentifier());
            return new PropertyNode(id, split[0], split[1], this, this.client);
        } catch (final Exception e) {
            return null;
        }

    }

    static String[] splitNodeId(final Object idValue) throws Exception {
        if (!(idValue instanceof final String s)) {
            return null;
        }

        final var fragmentPos = s.lastIndexOf('#');
        if (fragmentPos < 0 || fragmentPos + 1 >= s.length()) {
            return null;
        }

        final var thing = s.substring(0, fragmentPos);
        final var feature = s.substring(fragmentPos + 1);
        if (thing.isEmpty() || feature.isEmpty()) {
            return null;
        }

        return new String[] { thing, URLDecoder.decode(feature, StandardCharsets.UTF_8) };
    }
}
