/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.jmc.agent;

import javax.management.openmbean.CompositeData;

import org.openjdk.jmc.agent.impl.AbstractConvertable;
import org.openjdk.jmc.agent.util.TypeUtils;
import org.openjdk.jmc.agent.util.expression.ExpressionResolver;
import org.openjdk.jmc.agent.util.expression.IllegalSyntaxException;
import org.openjdk.jmc.agent.util.expression.ReferenceChain;

public class Field extends AbstractConvertable implements Attribute {

	private final String name;
	private final String expression;
	private final String fieldName;
	private final String description;
	private final String contentType;
	private final String relationKey;

	private Class<?> resolvingCaller;
	private ReferenceChain referenceChain;

	public Field(String name, String expression, String description, String contentType, String relationKey,
			String converterClassName) {
		super(converterClassName);
		this.name = name;
		this.expression = expression;
		this.description = description;
		this.contentType = contentType;
		this.relationKey = relationKey;
		this.fieldName = TypeUtils.deriveIdentifierPart(name);
	}

	public static Field from(CompositeData cd) {
		return new Field((String) cd.get("name"), (String) cd.get("expression"), (String) cd.get("description"),
				(String) cd.get("contentType"), (String) cd.get("relationKey"), (String) cd.get("converterDefinition"));
	}

	@Override
	public String getName() {
		return this.name;
	}

	public String getExpression() {
		return expression;
	}

	@Override
	public String getFieldName() {
		return this.fieldName;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	@Override
	public String getContentType() {
		return this.contentType;
	}

	@Override
	public String getRelationKey() {
		return this.relationKey;
	}

	public ReferenceChain resolveReferenceChain(Class<?> callerClass) throws IllegalSyntaxException {
		if (!callerClass.equals(resolvingCaller)) {
			resolvingCaller = callerClass;
			referenceChain = ExpressionResolver.solve(callerClass, expression);
		}

		return referenceChain;
	}
}
