/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.jmc.greychart.impl;

import java.awt.Polygon;
import java.util.Iterator;

import org.openjdk.jmc.common.xydata.DataSeries;
import org.openjdk.jmc.common.xydata.IXYData;
import org.openjdk.jmc.greychart.YAxis;

public interface OptimizingProvider {

	public boolean update();

	/**
	 * Sets the range,
	 *
	 * @param start
	 *            the start value.
	 * @param end
	 *            the end value.
	 */
	public void setRange(long start, long end);

	/**
	 * The resolution for which to optimize the samples.
	 */
	public void setResolution(int resolution);

	/**
	 * @param width
	 * @return samples in data series coordinates (not necessarily the same as world coordinates)
	 */
	Iterator<SamplePoint> getSamples(int width);

	Polygon getSamplesPolygon(LongWorldToDeviceConverter xWorldToDevice, WorldToDeviceConverter yWorldToDevice);

	public DataSeries<IXYData<Long, Number>> getDataSeries();

	public OptimizingProvider[] getChildren();

	/**
	 * @return the minimum Y value in the current range/view in world coordinates
	 */
	public double getMinY();

	/**
	 * @return the maximum Y value in the current range/view in world coordinates
	 */
	public double getMaxY();

	public WorldToDeviceConverter getYSampleToDeviceConverterFor(YAxis yAxis);

	/**
	 * @return the minimum X value in the current range/view
	 */
	public long getMinX();

	/**
	 * @return the maximum X value in the current range/view
	 */
	public long getMaxX();

	/**
	 * @return the minimum X value in the entire dataset (regardless of current range)
	 */
	public long getDataMinX();

	/**
	 * @return the maximum X value in the entire dataset (regardless of current range)
	 */
	public long getDataMaxX();

	/**
	 * @return the minimum Y value in the entire dataset (regardless of current range)
	 */
	public double getDataMinY();

	/**
	 * @return the maximum Y value in the entire dataset (regardless of current range)
	 */
	public double getDataMaxY();

	public void setDataChanged(boolean changed);

	public boolean hasDataChanged();

}
