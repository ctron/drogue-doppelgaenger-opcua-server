package io.drogue.doppelgaenger.opcua.server;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.api.AddressSpaceFragment;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.IdType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

import io.drogue.doppelgaenger.opcua.ThingsSubscriptionManager;
import io.drogue.doppelgaenger.opcua.client.BasicFeature;
import io.drogue.doppelgaenger.opcua.client.Thing;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public class PropertyNamespace implements AddressSpaceFragment {

    private static final Logger logger = LoggerFactory.getLogger(PropertyNamespace.class);

    public static final String NAMESPACE_URI = "https://drogue.io/doppelgänger/properties";

    private final UShort namespaceIndex;

    private final ThingsSubscriptionManager subscriptions;

    private final Map<UInteger, ThingsSubscriptionManager.Subscription> dataItems = new ConcurrentHashMap<>();

    PropertyNamespace(final OpcUaServer server, final ThingsSubscriptionManager subscriptions) {
        this.subscriptions = subscriptions;
        this.namespaceIndex = server.getNamespaceTable().addUri(NAMESPACE_URI);
    }

    @Override
    public AddressSpaceFilter getFilter() {
        return new NamespaceIndexFilter(this.namespaceIndex, true);
    }

    @Override
    public void read(final ReadContext context, final Double maxAge, final TimestampsToReturn timestamps, final List<ReadValueId> readValueIds) {
        logger.info("read: {}", readValueIds);

        final var result = new ArrayList<DataValue>();
        final var ids = new LinkedList<>(readValueIds);

        completedFuture(null)
                .thenCompose(x -> handleRead(ids, result))
                .whenComplete((x, err) -> {
                    logger.info("read complete", err);
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
        logger.info("write - {}", writeValues);
        context.failure(StatusCode.BAD);
    }

    @Override
    public void onDataItemsCreated(final List<DataItem> dataItems) {
        logger.info("onDataItemsCreated: {}", dataItems);

        for (final var item : dataItems) {
            subscribe(item);
        }
    }

    @Override
    public void onDataItemsModified(final List<DataItem> dataItems) {
        logger.info("onDataItemsModified: {}", dataItems);
    }

    @Override
    public void onDataItemsDeleted(final List<DataItem> dataItems) {
        logger.info("onDataItemsDeleted: {}", dataItems);

        for (final var item : dataItems) {
            final var dataItem = this.dataItems.remove(item.getId());
            if (dataItem != null) {
                dataItem.close();
            }
        }
    }

    @Override
    public void onMonitoringModeChanged(final List<MonitoredItem> monitoredItems) {
        logger.info("onMonitoringModeChanged: {}", monitoredItems);
    }

    private void subscribe(final DataItem item) {
        final var node = fromId(item.getReadValueId().getNodeId());

        final var name = node.getName();
        final var subscription = this.subscriptions.createSubscription(node.getThing(), name, state -> {
            reportValue(name, item, state);
        });
        this.dataItems.put(item.getId(), subscription);
    }

    private static void reportValue(final String name, final DataItem item, final Optional<Thing> state) {
        if (state.isPresent()) {
            final var thing = state.get();

            BasicFeature merged = thing.getReportedState().get(name);
            if (merged == null) {
                merged = thing.getSyntheticState().get(name);
            }

            logger.info("reportValue - state: {}, merged: {}", thing, merged);

            if (merged != null) {
                final var value = new DataValue(
                        toVariant(merged.getValue()),
                        StatusCode.GOOD,
                        new DateTime(merged.getLastUpdate().toInstant())
                );
                logger.info("Reporting: {}", value);
                item.setValue(value);
            } else {
                item.setQuality(StatusCode.UNCERTAIN);
            }

        } else {
            item.setQuality(StatusCode.UNCERTAIN);
        }
    }

    /**
     * Convert a Doppelgaenger value (JSON) into an OPC UA variant type.
     *
     * @param value The JSON value.
     * @return The variant type.
     */
    private static Variant toVariant(final JsonElement value) {

        if (value == null || value.isJsonNull()) {
            return new Variant(null);
        }

        if (value.isJsonPrimitive()) {
            final var p = value.getAsJsonPrimitive();
            if (p.isNumber()) {
                return new Variant(p.getAsNumber().doubleValue());
            } else if (p.isBoolean()) {
                return new Variant(p.getAsBoolean());
            } else {
                return new Variant(p.getAsString());
            }
        }

        if (value.isJsonArray()) {
            final var items = value.getAsJsonArray();
            final var array = new Variant[items.size()];
            var i = 0;
            for (final var item : items) {
                array[i] = toVariant(item);
                i++;
            }
            return new Variant(array);
        }

        // FIXME: convert everything else to string for now

        return new Variant(value.toString());

    }

    @Override
    public void browse(final BrowseContext context, final ViewDescription view, final NodeId nodeId) {
        logger.info("browse: {}", nodeId);

        context.success(List.of());
    }

    @Override
    public void getReferences(final BrowseContext context, final ViewDescription view, final NodeId nodeId) {
        logger.info("getReferences: {}", nodeId);

        if (nodeId.getNamespaceIndex().equals(this.namespaceIndex)) {
            // client node

            final var node = fromId(nodeId);
            if (node != null) {
                completedFuture(node)
                        .thenCompose(PropertyNode::getReferences)
                        .whenComplete((result, err) -> {
                            logger.info("getReferences: {}", result, err);
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

        final var json = new JsonObject()
                .put("thing", thing)
                .put("name", name).toBuffer();

        return new NodeId(this.namespaceIndex, ByteString.of(json.getBytes()));
    }

    PropertyNode fromId(final NodeId id) {
        if (!id.getType().equals(IdType.Opaque)) {
            return null;
        }

        try {
            final ByteString s = (ByteString) id.getIdentifier();
            final var json = new JsonObject(Buffer.buffer(s.bytesOrEmpty()));
            final var thing = json.getString("thing");
            final var name = json.getString("name");
            return new PropertyNode(id, thing, name, this);
        } catch (final Exception e) {
            return null;
        }

    }
}
