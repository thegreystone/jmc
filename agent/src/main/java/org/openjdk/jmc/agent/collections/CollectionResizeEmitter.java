/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The contents of this file are subject to the terms of either the Universal Permissive License
 * v 1.0 as shown at https://oss.oracle.com/licenses/upl
 *
 * or the following license:
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openjdk.jmc.agent.collections;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

import org.openjdk.jmc.agent.util.IOToolkit;
import org.openjdk.jmc.agent.util.TypeUtils;

import jdk.jfr.FlightRecorder;

/**
 * Registers the {@link CollectionResizeEvent} and emits it for the instrumented collections.
 * <p>
 * The woven resize methods live in bootstrap-loaded classes that cannot see {@code jdk.jfr}, so
 * they call the {@link org.openjdk.jmc.agent.collections.bootstrap.CollectionEventBridge} (defined
 * into the bootstrap loader), which forwards here via a {@link MethodHandle}. The {@code minsize}
 * threshold, the JFR enablement check and any future rate limiter all live in {@link #emit}.
 */
public final class CollectionResizeEmitter {

	private static final String BRIDGE_CLASS_NAME = "org.openjdk.jmc.agent.collections.bootstrap.CollectionEventBridge"; //$NON-NLS-1$
	private static final String BRIDGE_RESOURCE = "/org/openjdk/jmc/agent/collections/bootstrap/CollectionEventBridge.class"; //$NON-NLS-1$

	// Re-entrancy guard: committing an event can itself resize a HashMap, recursing back into emit().
	private static final ThreadLocal<Boolean> IN_EMIT = ThreadLocal.withInitial(() -> Boolean.FALSE);

	// Effectively disabled until init() installs a real threshold.
	private static volatile int minSize = Integer.MAX_VALUE;
	private static volatile boolean initialized;

	private CollectionResizeEmitter() {
	}

	/**
	 * Initializes collection resize tracking: registers the JFR event, defines the bootstrap bridge
	 * and wires it to this emitter. Must be called before the collection classes are retransformed.
	 *
	 * @param configuredMinSize
	 *            the minimum collection size (entry count) before an event is emitted.
	 * @throws Exception
	 *             if the bridge could not be defined or wired up.
	 */
	public static synchronized void init(int configuredMinSize) throws Exception {
		if (initialized) {
			return;
		}
		minSize = configuredMinSize;

		FlightRecorder.register(CollectionResizeEvent.class);

		// Define the bridge into the bootstrap loader (null loader) so bootstrap-loaded collections can
		// call it - no bootstrap jar, and it works for both premain and agentmain.
		byte[] bridgeBytes = readBridgeBytes();
		Class<?> bridge = TypeUtils.defineClass(BRIDGE_CLASS_NAME, bridgeBytes, 0, bridgeBytes.length, null, null);
		if (bridge == null) {
			throw new IllegalStateException("Failed to define the collection event bridge in the bootstrap loader"); //$NON-NLS-1$
		}

		MethodType bridgeType = MethodType.methodType(void.class, Object.class, long.class, Object.class, Object.class);
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		MethodHandle resizeHandle = lookup.findStatic(CollectionResizeEmitter.class, "onResize", bridgeType); //$NON-NLS-1$

		Method install = bridge.getMethod("install", MethodHandle.class); //$NON-NLS-1$
		install.invoke(null, resizeHandle);

		initialized = true;
	}

	/**
	 * @return {@code true} once the bridge is defined and wired. Weaving is only safe when this is
	 *         {@code true}, else the woven {@code INVOKESTATIC} references an undefined class.
	 */
	public static boolean isInstalled() {
		return initialized;
	}

	/**
	 * Retunes the emit threshold on an already-installed emitter (used by the JMX retune path).
	 */
	public static void setMinSize(int newMinSize) {
		minSize = newMinSize;
	}

	/**
	 * @return the current emit threshold (minimum collection size before an event is emitted).
	 */
	public static int getMinSize() {
		return minSize;
	}

	/**
	 * Entry point for collection resizes. Called (via the bootstrap bridge and a
	 * {@link MethodHandle}) from the instrumented resize methods.
	 */
	public static void onResize(Object collection, long size, Object oldArray, Object newArray) {
		emit(collection, size, oldArray, newArray);
	}

	private static void emit(Object collection, long size, Object oldArray, Object newArray) {
		// Cheapest check first.
		if (size < minSize) {
			return;
		}
		if (IN_EMIT.get()) {
			return;
		}
		long oldCapacity = capacity(oldArray);
		long newCapacity = capacity(newArray);
		if (newCapacity <= oldCapacity) {
			// Not an actual growth (e.g. HashMap.resize() returns the same table at MAXIMUM_CAPACITY).
			return;
		}
		// Extension point for an adaptive subsampler (e.g. a PID rate limiter). Omitted from the OSS agent.
		IN_EMIT.set(Boolean.TRUE);
		try {
			CollectionResizeEvent event = new CollectionResizeEvent();
			if (!event.shouldCommit()) {
				return;
			}
			event.collectionClass = collection.getClass();
			event.id = System.identityHashCode(collection) & 0xFFFFFFFFL;
			event.size = size;
			event.oldCapacity = oldCapacity;
			event.newCapacity = newCapacity;
			event.commit();
		} catch (Throwable t) {
			// Never let instrumentation break the instrumented collection.
		} finally {
			// remove(), not set(FALSE), to avoid a lingering ThreadLocalMap entry per emitting thread.
			IN_EMIT.remove();
		}
	}

	private static long capacity(Object array) {
		return array == null ? 0L : Array.getLength(array);
	}

	private static byte[] readBridgeBytes() throws Exception {
		try (InputStream in = CollectionResizeEmitter.class.getResourceAsStream(BRIDGE_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("Could not find the collection event bridge resource: " //$NON-NLS-1$
						+ BRIDGE_RESOURCE);
			}
			return IOToolkit.readFully(in, -1, true);
		}
	}
}
