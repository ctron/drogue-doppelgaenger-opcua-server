package io.drogue.doppelgaenger.opcua.server;

import java.util.stream.Stream;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class PropertyNamespaceTest {
    @ParameterizedTest
    @MethodSource("splitNodeIds")
    void testSplitNodeId(final NodeId input, final String[] expected) throws Exception {
        Assertions.assertArrayEquals(expected, PropertyNamespace.splitNodeId(input.getIdentifier()));
    }

    private static Arguments args(final String input, final String... outcome) {
        return Arguments.of(
                new NodeId(0, input),
                outcome.length > 0 ? outcome : null);
    }

    private static Stream<Arguments> splitNodeIds() {
        return Stream.of(
                Arguments.of(new NodeId(0, 0), null),
                args(""),
                args("foo"),
                args("#foo"),
                args("#"),
                args("foo#"),
                args("foo#bar", "foo", "bar"),
                args("foo##bar", "foo#", "bar"),
                args("#foo##bar", "#foo#", "bar"),
                args("#foo##%23bar", "#foo#", "#bar")
        );
    }
}
