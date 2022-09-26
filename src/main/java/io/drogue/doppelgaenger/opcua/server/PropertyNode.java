package io.drogue.doppelgaenger.opcua.server;

import static java.util.concurrent.CompletableFuture.completedFuture;

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
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyNode {

    private static final Logger logger = LoggerFactory.getLogger(PropertyNode.class);

    private final NodeId nodeId;

    private final String thing;

    private final String name;

    private final PropertyNamespace namespace;

    PropertyNode(final NodeId nodeId, final String thing, final String name, final PropertyNamespace namespace) {
        this.nodeId = nodeId;
        this.thing = thing;
        this.name = name;
        this.namespace = namespace;
    }

    public String getThing() {
        return this.thing;
    }

    public String getName() {
        return this.name;
    }

    public CompletableFuture<List<Reference>> getReferences() {

        final var nodeId = this.namespace.propertyNodeId(this.thing, this.name);

        final var refs = List.of(new Reference(
                nodeId,
                Identifiers.HasTypeDefinition,
                Identifiers.BaseDataVariableType.expanded(),
                true
        ));

        logger.debug("getReferences -> {}", refs);

        return CompletableFuture.completedFuture(refs);
    }

    public CompletableFuture<DataValue> readAttribute(final UInteger attributeId) {
        if (attributeId.equals(AttributeId.NodeId.uid())) {
            return completedFuture(new DataValue(new Variant(this.nodeId)));
        }
        if (attributeId.equals(AttributeId.NodeClass.uid())) {
            return completedFuture(new DataValue(new Variant(NodeClass.Variable)));
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
        if (attributeId.equals(AttributeId.ValueRank.uid())) {
            return completedFuture(new DataValue(new Variant(-1)));
        }
        if (attributeId.equals(AttributeId.DataType.uid())) {
            return completedFuture(new DataValue(new Variant(Identifiers.BaseDataType)));
        }
        if (attributeId.equals(AttributeId.AccessLevel.uid())) {
            return completedFuture(new DataValue(new Variant(UByte.valueOf(1))));
        }
        if (attributeId.equals(AttributeId.UserAccessLevel.uid())) {
            return completedFuture(new DataValue(new Variant(UByte.valueOf(1))));
        }
        if (attributeId.equals(AttributeId.EventNotifier.uid())) {
            return completedFuture(new DataValue(new Variant(UByte.valueOf(0))));
        }
        if (attributeId.equals(AttributeId.MinimumSamplingInterval.uid())) {
            return completedFuture(new DataValue(new Variant(0.0)));
        }

        logger.info("Unhandled read: {}", AttributeId.from(attributeId).map(Object::toString).orElseGet(attributeId::toString));

        return completedFuture(new DataValue(StatusCode.BAD));
    }

    private String getLocalName() {
        return this.name;
    }

}
