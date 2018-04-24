package io.opentracing.contrib.cassandra;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

import org.apache.cassandra.tracing.TraceState;
import org.apache.cassandra.tracing.Tracing;
import org.apache.cassandra.utils.UUIDGen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.Binary;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.ThreadLocalScopeManager;

import java.net.MalformedURLException;

public class OTTracing extends Tracing
{
    public static final String OT_TRACE_HEADERS = "ot-headers";

    private static final Logger logger = LoggerFactory.getLogger(OTTracing.class);

    private final Tracer tracer;

    public OTTracing() throws MalformedURLException
    {
        this.tracer = new MockTracer(new ThreadLocalScopeManager(), MockTracer.Propagator.BINARY);
    }

    private OTTraceState getStateImpl()
    {
        TraceState state = get();
        if (state == null)
            return null;

        if (state instanceof OTTraceState)
            return (OTTraceState)state;

        assert false : "TracingImpl states should be of type OTTraceStateImpl";
        return null;
    }

    @Override
    public UUID newSession(UUID sessionId, Map<String, ByteBuffer> customPayload)
    {
        return newSession(sessionId, TraceType.QUERY, customPayload);
    }

    @Override
    public UUID newSession(UUID sessionId, TraceType traceType, Map<String, ByteBuffer> customPayload)
    {
        SpanContext ctx = null;
        ByteBuffer buff = customPayload == null ? null : customPayload.get(OT_TRACE_HEADERS);
        if (buff != null)
            ctx = tracer.extract(Format.Builtin.BINARY, new BinaryAdapter(buff));

        long parentId = -1;
        if (ctx != null)
            parentId = ((MockSpan.MockContext)ctx).spanId();

        Scope scope = tracer.buildSpan("cassandra-server").asChildOf(ctx).startActive(true);
        scope.span().setTag("cassandra.session", sessionId.toString());

        logger.info("Created Span for " + sessionId + " with parent span id " + parentId);

        return super.newSession(sessionId, traceType, customPayload);
    }

    @Override
    protected TraceState newTraceState(InetAddress coordinator, UUID sessionId, TraceType traceType)
    {
        Span span = tracer.activeSpan();
        return new OTTraceState(tracer, span, coordinator, sessionId, traceType);
    }

    @Override
    public void stopSessionImpl()
    {
        OTTraceState state = getStateImpl();
        if (state == null)
            return;

        Span span = state.span;
        assert span == tracer.activeSpan();

        int elapsed = state.elapsed();
        span.setTag("cassandra.elapsed", elapsed);

        tracer.scopeManager().active().close();
        logger.info("Finished span for " + state.sessionId);
        logger.info("  tags = " + ((MockSpan)span).tags());
    }

    @Override
    public TraceState begin(String request, InetAddress client, Map<String, String> parameters)
    {
        assert isTracing();

        OTTraceState state = getStateImpl();
        assert state != null;

        Span span = state.span;
        span.log(request);
        span.log(client.toString());

        MockSpan mockSpan = (MockSpan)span;
        logger.info("Span for session " + state.sessionId + " got logged = " + request);
        logger.info("Span for session " + state.sessionId + " got logged = " + client.toString());

        return state;
    }

    @Override
    public void trace(ByteBuffer sessionId, String message, int ttl)
    {
        UUID sessionUuid = UUIDGen.getUUID(sessionId);
        TraceState state = Tracing.instance.get(sessionUuid);
        state.trace(message);
    }

    class BinaryAdapter implements Binary
    {
        final ByteBuffer src;

        public BinaryAdapter(ByteBuffer src)
        {
            this.src = src;
        }

        public void write(ByteBuffer buffer) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        public int read(ByteBuffer buffer) throws IOException
        {
            if (!src.hasRemaining())
                return -1;

            int readBytes;
            for (readBytes = 0; src.hasRemaining() && buffer.hasRemaining(); readBytes++)
                buffer.put(src.get());

            return readBytes;
        }
    }
}
