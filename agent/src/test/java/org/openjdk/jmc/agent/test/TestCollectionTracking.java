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
package org.openjdk.jmc.agent.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.Test;
import org.openjdk.jmc.agent.TransformDescriptor;
import org.openjdk.jmc.agent.TransformRegistry;
import org.openjdk.jmc.agent.collections.CollectionTrackingSettings;
import org.openjdk.jmc.agent.collections.CollectionTransformDescriptor;
import org.openjdk.jmc.agent.impl.DefaultTransformRegistry;

/**
 * Tests parsing of the {@code <collectiontracking>} capability element and the injection of the
 * fixed HashMap/ArrayList instrumentation descriptors into the registry.
 */
public class TestCollectionTracking {

	private static final String HASHMAP = "java/util/HashMap"; //$NON-NLS-1$
	private static final String ARRAYLIST = "java/util/ArrayList"; //$NON-NLS-1$

	@Test
	public void testEnabledWithMinSize() throws Exception {
		TransformRegistry registry = fromXml("<jfragent><collectiontracking minsize=\"64\"/></jfragent>"); //$NON-NLS-1$

		CollectionTrackingSettings settings = registry.getCollectionTrackingSettings();
		assertTrue("Collection tracking should be enabled", settings.isEnabled()); //$NON-NLS-1$
		assertEquals(64, settings.getMinSize());

		assertSingleCollectionDescriptor(registry, HASHMAP);
		assertSingleCollectionDescriptor(registry, ARRAYLIST);
	}

	@Test
	public void testDefaultMinSize() throws Exception {
		TransformRegistry registry = fromXml("<jfragent><collectiontracking/></jfragent>"); //$NON-NLS-1$

		CollectionTrackingSettings settings = registry.getCollectionTrackingSettings();
		assertTrue(settings.isEnabled());
		assertEquals(CollectionTrackingSettings.DEFAULT_MIN_SIZE, settings.getMinSize());
	}

	@Test
	public void testMinSizeClampsOverflowToSuppress() throws Exception {
		// A minsize larger than an int is valid per the schema (nonNegativeInteger) but must not fall
		// back to the noisy default; it clamps to Integer.MAX_VALUE (effectively suppressing events).
		TransformRegistry registry = fromXml("<jfragent><collectiontracking minsize=\"10000000000\"/></jfragent>"); //$NON-NLS-1$
		assertEquals(Integer.MAX_VALUE, registry.getCollectionTrackingSettings().getMinSize());
	}

	@Test
	public void testAbsent() throws Exception {
		TransformRegistry registry = fromXml("<jfragent></jfragent>"); //$NON-NLS-1$

		assertFalse("Collection tracking should be disabled when the element is absent", //$NON-NLS-1$
				registry.getCollectionTrackingSettings().isEnabled());
		assertTrue("No HashMap transforms expected when disabled", //$NON-NLS-1$
				registry.getTransformData(HASHMAP).isEmpty());
		assertTrue("No ArrayList transforms expected when disabled", //$NON-NLS-1$
				registry.getTransformData(ARRAYLIST).isEmpty());
	}

	private static void assertSingleCollectionDescriptor(TransformRegistry registry, String className) {
		List<TransformDescriptor> descriptors = registry.getTransformData(className);
		assertEquals("Expected exactly one transform for " + className, 1, descriptors.size()); //$NON-NLS-1$
		assertTrue("Expected a CollectionTransformDescriptor for " + className, //$NON-NLS-1$
				descriptors.get(0) instanceof CollectionTransformDescriptor);
	}

	private static TransformRegistry fromXml(String xml) throws Exception {
		return DefaultTransformRegistry.from(new ByteArrayInputStream(xml.getBytes()));
	}
}
