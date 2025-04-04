/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2025, Kantega AS. All rights reserved.
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
package org.openjdk.jmc.jolokia;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.LinkedList;
import java.util.Optional;

import javax.management.AttributeNotFoundException;
import javax.management.Descriptor;
import javax.management.ImmutableDescriptor;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ObjectName;
import javax.management.modelmbean.DescriptorSupport;
import javax.management.openmbean.TabularData;

import org.jolokia.client.J4pClient;
import org.jolokia.client.jmxadapter.RemoteJmxAdapter;
import org.jolokia.service.serializer.JolokiaSerializer;
import org.jolokia.server.core.service.serializer.SerializeOptions;

/**
 * Make JMC specific adjustments to Jolokia JMX connection. May consider to use the decorator
 * pattern if differences are big, but for now subclass
 */
public class JmcJolokiaJmxConnection extends RemoteJmxAdapter {

	private static final String UNKNOWN = "Unknown"; //$NON-NLS-1$
	private static final String DIAGNOSTIC_OPTIONS = "com.sun.management:type=DiagnosticCommand"; //$NON-NLS-1$
	private static final String PREFIX = "dcmd."; //$NON-NLS-1$
	private static final String IMPACT = PREFIX + "vmImpact"; //$NON-NLS-1$
	private static final String NAME = PREFIX + "name"; //$NON-NLS-1$
	private static final String DESCRIPTION = PREFIX + "description"; //$NON-NLS-1$
	private static final String ARGUMENTS = PREFIX + "arguments"; //$NON-NLS-1$
	private static final String ARGUMENT_NAME = PREFIX + "arg.name"; //$NON-NLS-1$
	private static final String ARGUMENT_DESCRIPTION = PREFIX + "arg.description"; //$NON-NLS-1$
	private static final String ARGUMENT_MANDATORY = PREFIX + "arg.isMandatory"; //$NON-NLS-1$
	private static final String ARGUMENT_TYPE = PREFIX + "arg.type"; //$NON-NLS-1$
	private static final String ARGUMENT_OPTION = PREFIX + "arg.isOption"; //$NON-NLS-1$
	private static final String ARGUMENT_MULITPLE = PREFIX + "arg.isMultiple"; //$NON-NLS-1$

	public JmcJolokiaJmxConnection(J4pClient client) throws IOException {
		super(client);
	}

	@Override
	public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, IOException {
		MBeanInfo mBeanInfo = super.getMBeanInfo(name);
		// the diagnostic options tab and memory relies on descriptor info in MBeanInfo,
		// modify descriptors the first time
		if (DIAGNOSTIC_OPTIONS.equals(name.getCanonicalName())
				&& mBeanInfo.getOperations()[0].getDescriptor() == ImmutableDescriptor.EMPTY_DESCRIPTOR) {

			MBeanOperationInfo[] modifiedOperations = new MBeanOperationInfo[mBeanInfo.getOperations().length];

			for (int i = 0; i < mBeanInfo.getOperations().length; i++) {
				modifiedOperations[i] = stealOrBuildOperationInfo(mBeanInfo.getOperations()[i],
						checkForLocalOperationInfo(name));
			}
			//create a copy with modified operations in place of the original MBeanInfo in the cache
			final MBeanInfo modifiedMBeanInfo = new MBeanInfo(mBeanInfo.getClassName(), mBeanInfo.getDescription(),
					mBeanInfo.getAttributes(), mBeanInfo.getConstructors(), modifiedOperations,
					mBeanInfo.getNotifications());
			this.mbeanInfoCache.put(name, modifiedMBeanInfo);
			return modifiedMBeanInfo;
		}
		return mBeanInfo;
	}

	private Optional<MBeanInfo> checkForLocalOperationInfo(ObjectName name) {
		MBeanInfo localInfo;
		try {
			localInfo = ManagementFactory.getPlatformMBeanServer().getMBeanInfo(name);
		} catch (Exception | NoClassDefFoundError ignore) {
			localInfo = null;
		}
		return Optional.ofNullable(localInfo);
	}

	@Override
	public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
			throws InstanceNotFoundException, MBeanException, IOException {

		if (params != null) {
			for (int i = 0; i < params.length; i++) {
				Object object = params[i];
				if (object instanceof TabularData) {
					try {
						params[i] = new JolokiaSerializer().serialize(object, new LinkedList<String>(),
								SerializeOptions.DEFAULT);
					} catch (AttributeNotFoundException ignore) {
					}
				}

			}
		}
		return super.invoke(name, operationName, params, signature);
	}

	/**
	 * Build MBeanOperationInfo by taking information from the corresponding MBean in the local JVM
	 * for a more precise signature. If it is not available locally, attempt to construct it from
	 * the metadata from Jolokia.
	 * 
	 * @param original
	 *            MBeanInfo from Jolokia list.
	 * @param localInfo
	 *            MBeanInfo from this JVM to use for getting descriptor.
	 * @return Descriptor
	 */
	private MBeanOperationInfo stealOrBuildOperationInfo(MBeanOperationInfo original, Optional<MBeanInfo> localInfo) {
		return localInfo.map(info -> checkForMatchingLocalOperation(original, info))// first attempt to get descriptor from local copy
				.orElseGet(() -> reverseEngineerOperationInfo(original));// if not, reverse engineer descriptor from operation info
	}

	private MBeanOperationInfo checkForMatchingLocalOperation(MBeanOperationInfo original, MBeanInfo info) {
		for (MBeanOperationInfo localOperation : info.getOperations()) {
			if (localOperation.getName().equals(original.getName())) {
				if (localOperation.getSignature().length == original.getSignature().length) {
					for (int i = 0; i < original.getSignature().length; i++) {
						MBeanParameterInfo param = original.getSignature()[i];
						if (!param.getType().equals(localOperation.getSignature()[i].getType())) {
							break;
						} else if (i == original.getSignature().length - 1) {
							// whole signature matches, use as replacement
							return localOperation;
						}
					}
				}
			}
		}
		return null;
	}

	private MBeanOperationInfo reverseEngineerOperationInfo(MBeanOperationInfo original) {
		DescriptorSupport result = new DescriptorSupport();
		result.setField(NAME, original.getName());
		result.setField(DESCRIPTION, original.getDescription());
		result.setField(IMPACT, UNKNOWN);
		result.setField(ARGUMENTS, buildArguments(original.getSignature()));
		return new MBeanOperationInfo(original.getName(), original.getDescription(), original.getSignature(),
				original.getReturnType(), MBeanOperationInfo.UNKNOWN, result);
	}

	private Descriptor buildArguments(MBeanParameterInfo[] signature) {
		DescriptorSupport parameters = new DescriptorSupport();
		for (MBeanParameterInfo parameter : signature) {
			parameters.setField(parameter.getName(), buildArgument(parameter));
		}
		return parameters;
	}

	private Descriptor buildArgument(MBeanParameterInfo parameter) {
		DescriptorSupport result = new DescriptorSupport();
		result.setField(ARGUMENT_NAME, parameter.getName());
		boolean isMultiple = parameter.getType().startsWith("["); //$NON-NLS-1$
		result.setField(ARGUMENT_MULITPLE, String.valueOf(isMultiple));
		String type = parameter.getType();
		if (isMultiple) {
			if (type.startsWith("[L")) { //$NON-NLS-1$
				type = type.substring(2);
			} else {
				type = type.substring(1);
			}

		}
		// probably more reverse mapping of types should be done here, but we hope it is
		// sufficient
		result.setField(ARGUMENT_TYPE, type);
		result.setField(ARGUMENT_DESCRIPTION, parameter.getDescription());
		result.setField(ARGUMENT_MANDATORY, "false"); //$NON-NLS-1$
		result.setField(ARGUMENT_OPTION, "false"); //$NON-NLS-1$
		return result;
	}

	@Override
	public boolean isInstanceOf(ObjectName objectName, String type) throws InstanceNotFoundException, IOException {
		if ("java.lang.management.OperatingSystemMXBean".equals(type) //$NON-NLS-1$
				&& "com.sun.management.internal.OperatingSystemImpl" //$NON-NLS-1$
						.equals(this.getMBeanInfo(objectName).getClassName())) {
			return true;
		}
		try {
			return super.isInstanceOf(objectName, type);
		} catch (NoClassDefFoundError | UnsatisfiedLinkError e) {
			//Handle this until it is fixed in jolokia https://github.com/jolokia/jolokia/issues/666
			return false;
		}
	}

}
