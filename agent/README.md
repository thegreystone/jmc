# The JMC Agent
The JMC agent allows users to add JFR instrumentation declaratively to a running program. The agent can, for example, be used to add flight recorder events to third party code for which the source is not available.

To build and run the agent you will need JDK 17 or later.

## Building the agent
To build the agent, simply use maven in the agent folder.

```bash
mvn clean package
```

## Running the agent
The agent can be tried out using the included example program.

Here is an example for running the example program on JDK 17 or later:

```bash
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -XX:+FlightRecorder -javaagent:target/agent-1.1.0-SNAPSHOT.jar=target/test-classes/org/openjdk/jmc/agent/test/jfrprobes_template.xml -cp target/agent-1.1.0-SNAPSHOT.jar:target/test-classes/ org.openjdk.jmc.agent.test.InstrumentMe
```

The `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED` option is required so that the agent can define the generated JFR event classes.

## Collection resize tracking
In addition to the declarative JFR probes, the agent has a built-in capability aimed at two things:

* **Right-sizing**: finding good starting sizes (initial capacities) for your collections so you can
pre-size them and avoid a lot of resizing of the array backing the collection (and the array copying
and rehashing that each resize incurs); and 
* **leak hunting**: spotting slowly leaking collections that keep growing without bound. 

It emits a `jdk.jmc.CollectionResize` event (category *JMC Agent / Collections*) whenever a collection's backing array is resized: a collection that resizes repeatedly
is a candidate for a larger initial capacity, while one that keeps growing across the run is a likely
memory leak. Each event carries the collection's identity and a stack trace, so you can identify
which collection is responsible and where to pre-size it, or stop leaking into it. The currently
tracked collections are `HashMap` (which also covers `LinkedHashMap`, `HashSet` and `LinkedHashSet`),
`ArrayList`, `Vector`, `Hashtable`, `PriorityQueue` and `ArrayDeque`.

It is enabled by adding a `collectiontracking` element to the agent configuration:

```xml
<jfragent>
	<collectiontracking minsize="128"/>
</jfragent>
```

The only setting is `minsize` (default 128): the minimum collection size (entry count) that must be
reached before a resize event is emitted, which suppresses the noise from small collections resizing
early in their life. Each event records the collection's runtime class, a stable identity hash of the
instance (to correlate resizes of the same collection over time), the entry count, and the old and
new capacities.

Collection tracking is best enabled at agent **startup** (`premain`). It instruments hot core JDK
classes, so the one-time weave is cheap at startup, but enabling it at runtime — via dynamic attach
(`agentmain`) or by pushing a `collectiontracking` element over JMX (see below) — retransforms
already-JIT-compiled core classes and therefore risks a significant **deoptimization storm**. That is
allowed (the operator opts into the cost), but generally prefer startup.

It can also be disabled at runtime: a JMX config push that omits the `collectiontracking` element (or
a full revert) unweaves the collection classes. But **prefer turning the data off by disabling the
`jdk.jmc.CollectionResize` event in your recording** — unweaving retransforms the same hot core
classes and causes a second deopt storm, whereas toggling the event is free. The instrumentation is
otherwise harmless when the event is disabled (a quick threshold check per resize).

## Interacting with the agent
At runtime the agent can be used to modify the transformed state of a class. To specify a desired state, supply the defineEventProbes function with an XML description of event probes to add, keep or modify, and leave out all those that should be reverted to their pre-instrumentation versions.

### Using a security manager
When running with a security manager, the 'control' Management Permission must be granted to control the agent through the MBean. To set fine grained permissions for authenticated remote users, see [here](https://docs.oracle.com/javadb/10.10.1.2/adminguide/radminjmxenablepolicy.html#radminjmxenablepolicy) and [here](https://docs.oracle.com/javase/7/docs/technotes/guides/management/agent.html#gdeup).

## Known Issues
* The full converter support is still to be merged into the open source repo
* Support for emitting an event only on exception has yet to be implemented
