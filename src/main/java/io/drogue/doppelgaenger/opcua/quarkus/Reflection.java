package io.drogue.doppelgaenger.opcua.quarkus;

import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.AxisScaleEnumeration;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DataChangeTrigger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ExceptionDeviationFormat;
import org.eclipse.milo.opcua.stack.core.types.enumerated.FilterOperator;
import org.eclipse.milo.opcua.stack.core.types.enumerated.HistoryUpdateType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.IdType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ModelChangeStructureVerbMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NamingRuleType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeAttributesMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.OpenFileMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.PerformUpdateType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.eclipse.milo.opcua.stack.core.types.enumerated.SecurityTokenRequestType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.eclipse.milo.opcua.stack.core.types.enumerated.StructureType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TrustListMasks;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * All these classes will be registered for reflection.
 * <p>
 * This is required as Milo will use reflection during deserialization, and native compilation must be aware of those classes.
 */
@RegisterForReflection(targets = {
        ApplicationType.class,
        AxisScaleEnumeration.class,
        BrowseDirection.class,
        BrowseResultMask.class,
        DataChangeTrigger.class,
        DeadbandType.class,
        ExceptionDeviationFormat.class,
        FilterOperator.class,
        HistoryUpdateType.class,
        IdType.class,
        MessageSecurityMode.class,
        ModelChangeStructureVerbMask.class,
        MonitoringMode.class,
        NamingRuleType.class,
        NodeAttributesMask.class,
        NodeClass.class,
        OpenFileMode.class,
        PerformUpdateType.class,
        RedundancySupport.class,
        SecurityTokenRequestType.class,
        ServerState.class,
        StructureType.class,
        TimestampsToReturn.class,
        TrustListMasks.class,
        UserTokenType.class,
})
public class Reflection {
}
