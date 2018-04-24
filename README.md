# OpenTracing Cassandra Server Instrumentation
**Experimental** OpenTracing instrumentation for Cassandra. This work is intended to validate both the new `BINARY` format [here](https://github.com/opentracing/opentracing-java/pull/252), and to eventually lead to OT support in the Cassandra server side. Inspiration taken from [previous work](http://thelastpickle.com/blog/2015/12/07/using-zipkin-for-full-stack-tracing-including-cassandra.html).

## Requirements.

Prior to building this plugin, you will need to do the next steps:

1. Get Cassandra and set it up to run it locally.
1. Get [opentracing-java](https://github.com/opentracing/opentracing-java).
2. cd `opentracing-java` and `git fetch origin pull/252/head:binary_format_proposal` to fetch the branch containing the latest `BINARY` format support.
3. `git checkout binary_format_proposal && mvn clean install` to create the jars for `opentracing-api` and the other modules, as well as performing their installation in the local Maven repository. Observe they will be labeled with the 0.31.1-SNAPSHOT version (so no messing up wit the stable 0.31 installation, of any).


## Running.

Once these steps have been completed, it's time to build and test the plugin:

1. `mvn clean && mvn package` to produce a jar containing both the client call and the server plugin.
2. `cp target/opentracing-cassandra-server-0.0.1-SNAPSHOT.jar /your/path/to/apache-cassandra-3.11.2/lib/`  to copy the plugin to the Cassandra lib directory.
3. Copy the `opentracing` jars to the same location (`/your/path/to/apache-cassandra-3.11.2/lib/`). That is, `opentracing-api/target/opentracing-api-0.31.1-SNAPSHOT.jar` and the others for `opentracing-noop` and `opentracing-mock`). These are the dependencies of our plugin so we need to have them around.
4. Start Cassandra specifying our custom tracing class: `JVM_OPTS="-Dcassandra.custom_tracing_class=io.opentracing.contrib.cassandra.OTTracing" bin/cassandra -f`.
5. Once Cassandra is running locally, from this directory type `mvn exec:java`, which will invoke our `ClientCall.main()` code, which will issue a simple query with tracing enabled, and passing a custom payload (containing the `Span` context).

This should show the next lines in the Cassandra terminal:

```sh
OTTracing.java:73 - Created Span for 6b71ea80-4a10-11e8-8842-d5881f757428 with parent span id 2
OTTracing.java:116 - Span for session 6b71ea80-4a10-11e8-8842-d5881f757428 got logged = Execute CQL3 query
OTTracing.java:117 - Span for session 6b71ea80-4a10-11e8-8842-d5881f757428 got logged = /127.0.0.1
OTTracing.java:99 - Finished span for 6b71ea80-4a10-11e8-8842-d5881f757428
OTTracing.java:100 -   tags = {cassandra.session=6b71ea80-4a10-11e8-8842-d5881f757428, cassandra.elapsed=23087}
```

This will show the plugin properly managed to extract the payload and use the resulting `SpanContext` as parent of our own (`with parent span id 2`), new `Span` on the server side.

## Binary payload.

Cassandra supports custom plugins to replace their own tracing layer, through the usage of data contained in a `ByteBuffer` object. This happens both in the client and the server (plugin) side:

```java
    // Client issuing the request.
    tracer.inject(scope.span().context(), Format.Builtin.BINARY, Adapters.injectBinary(stream));
    ByteBuffer payload = ByteBuffer.wrap(stream.toByteArray());

    // Execute a simple statement and pass a custom payload.
    Statement stmt = new SimpleStatement("SELECT * FROM system_schema.keyspaces;");
    stmt.enableTracing();
    stmt.setOutgoingPayload(singletonMap(OTTracing.OT_TRACE_HEADERS, payload));
```

```java
    // Server
    @Override
    public UUID newSession(UUID sessionId, TraceType traceType, Map<String, ByteBuffer> customPayload)
    {
        SpanContext ctx = null;
        ByteBuffer buff = customPayload == null ? null : customPayload.get(OT_TRACE_HEADERS);
        if (buff != null)
            ctx = tracer.extract(Format.Builtin.BINARY, new BinaryAdapter(buff));
```
