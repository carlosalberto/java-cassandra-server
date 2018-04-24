package io.opentracing.contrib.cassandra;

import java.net.InetAddress;
import java.util.UUID;

import org.apache.cassandra.tracing.TraceState;
import org.apache.cassandra.tracing.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.opentracing.Span;
import io.opentracing.Tracer;

public class OTTraceState extends TraceState
{
    private final static Logger logger = LoggerFactory.getLogger(OTTraceState.class);

    private final Tracer tracer;
    public final Span span; // Follow Cassandra style of public fields.

    public OTTraceState(Tracer tracer, Span span, InetAddress coordinator, UUID sessionId, Tracing.TraceType traceType)
    {
        super(coordinator, sessionId, traceType);
        this.tracer = tracer;
        this.span = span;
    }

    @Override
    protected void traceImpl(String message)
    {
        span.log(message);

        if (logger.isTraceEnabled())
            logger.trace("Adding <{}> to trace events", message);
    }

    @Override
    protected void waitForPendingEvents()
    {
    }
}
