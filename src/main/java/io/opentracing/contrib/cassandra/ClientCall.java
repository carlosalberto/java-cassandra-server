package io.opentracing.contrib.cassandra;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.mock.MockTracer;
import io.opentracing.propagation.Adapters;
import io.opentracing.propagation.Format;
import io.opentracing.util.ThreadLocalScopeManager;

import static java.util.Collections.singletonMap;

public class ClientCall
{
    public static void main(String [] args)
    {
        // Setup the environment.
        Cluster.Builder builder = Cluster.builder().addContactPoints("127.0.0.1");
        Cluster cluster = builder.build();
        Session session = cluster.connect();
        PreparedStatement prepared = session.prepare(
                "create keyspace if not exists test WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};");
        BoundStatement bound = prepared.bind();
        session.execute(bound);
        session.execute("USE test;");

        // Prepare tracing.
        MockTracer tracer = new MockTracer(new ThreadLocalScopeManager(), MockTracer.Propagator.BINARY);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        
        try (Scope scope = tracer.buildSpan("cassandra-client").startActive(true)) {
            tracer.inject(scope.span().context(), Format.Builtin.BINARY, Adapters.injectBinary(stream));
            ByteBuffer payload = ByteBuffer.wrap(stream.toByteArray());

            // Execute a simple statement and pass a custom payload.
            Statement stmt = new SimpleStatement("SELECT * FROM system_schema.keyspaces;");
            stmt.enableTracing();
            stmt.setOutgoingPayload(singletonMap(OTTracing.OT_TRACE_HEADERS, payload));
            session.execute(stmt);
        }

        // Need to explicitly finish the app.
        System.exit(0);
    }
}
