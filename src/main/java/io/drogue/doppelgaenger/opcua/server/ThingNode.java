package io.drogue.doppelgaenger.opcua.server;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.IdType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.drogue.doppelgaenger.opcua.client.BasicFeature;
import io.drogue.doppelgaenger.opcua.client.Client;
import io.drogue.doppelgaenger.opcua.client.Thing;

/**
 * Node based on remote thing.
 */
public class ThingNode {

    private static final Logger logger = LoggerFactory.getLogger(ThingNode.class);

    private final Client client;

    private final ThingNamespace namespace;

    private final PropertyNamespace propertyNamespace;

    private final NodeId nodeId;

    private final String name;

    public ThingNode(final Client client, final ThingNamespace namespace, final PropertyNamespace propertyNamespace, final NodeId nodeId, final String name) {
        this.client = client;
        this.namespace = namespace;
        this.propertyNamespace = propertyNamespace;
        this.nodeId = nodeId;
        this.name = name;
    }

    public static ThingNode fromId(final Client client, final ThingNamespace namespace, final PropertyNamespace propertyNamespace, final NodeId id) {
        if (id.getType().equals(IdType.String)) {
            return new ThingNode(client, namespace, propertyNamespace, id, id.getIdentifier().toString());
        }
        throw new IllegalArgumentException();
    }

    public CompletableFuture<DataValue> readAttribute(final UInteger attributeId) {
        if (attributeId.equals(AttributeId.NodeId.uid())) {
            return completedFuture(new DataValue(new Variant(this.nodeId)));
        }
        if (attributeId.equals(AttributeId.NodeClass.uid())) {
            return completedFuture(new DataValue(new Variant(NodeClass.Object)));
        }
        if (attributeId.equals(AttributeId.BrowseName.uid())) {
            return completedFuture(new DataValue(new Variant(new QualifiedName(this.nodeId.getNamespaceIndex(), this.getLocalName()))));
        }
        if (attributeId.equals(AttributeId.DisplayName.uid())) {
            return completedFuture(new DataValue(new Variant(LocalizedText.english(this.getLocalName()))));
        }
        if (attributeId.equals(AttributeId.Description.uid())) {
            return completedFuture(new DataValue(new Variant(LocalizedText.NULL_VALUE)));
        }

        return completedFuture(new DataValue(StatusCode.BAD));
    }

    public String getLocalName() {
        return this.name;
    }

    public CompletableFuture<List<Reference>> browse() {
        return this.client.get(this.name)
                .thenApply(optThing -> optThing
                        .map(this::browseThing)
                        .orElseGet(List::of));
    }

    private List<Reference> browseThing(final Thing thing) {

        final var refs = new LinkedList<Reference>();

        final var state = thing.mergedState();

        // children
        ofNullable(state.remove("$children"))
                .flatMap(BasicFeature::asObject)
                .map(c -> {
                    logger.debug("Children: {}", c);
                    return c;
                })
                .stream().flatMap(c -> c.keySet().stream())
                .forEach(n -> {
                    refs.add(new Reference(
                            this.nodeId,
                            Identifiers.Organizes,
                            this.namespace.thingNodeId(n),
                            true
                    ));

                });

        // regular properties

        for (final var entry : state.entrySet()) {
            if (entry.getKey().startsWith("$")) {
                continue;
            }

            refs.add(new Reference(
                    this.nodeId,
                    Identifiers.Organizes,
                    this.propertyNamespace.propertyNodeId(this.name, entry.getKey()).expanded(),
                    true
            ));

        }

        // return result

        return refs;
    }

    public CompletableFuture<List<Reference>> getReferences() {
        return CompletableFuture.completedFuture(List.of(new Reference(
                this.nodeId,
                Identifiers.HasTypeDefinition,
                Identifiers.FolderType.expanded(),
                true
        )));
    }

}
