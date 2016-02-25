/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.io.network;

import org.apache.flink.runtime.io.network.buffer.BufferPool;
import org.apache.flink.runtime.io.network.buffer.NetworkBufferPool;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockNetworkEnvironment {

	private static NetworkEnvironment networkEnvironment = mock(NetworkEnvironment.class);

	private static NetworkBufferPool networkBufferPool = mock(NetworkBufferPool.class);

	private static TaskEventDispatcher taskEventDispatcher = mock(TaskEventDispatcher.class);

	static {
		try {
			when(networkBufferPool.createBufferPool(anyInt(), anyBoolean())).thenReturn(mock(BufferPool.class));
			when(networkEnvironment.getNetworkBufferPool()).thenReturn(networkBufferPool);

			when(networkEnvironment.getTaskEventDispatcher()).thenReturn(taskEventDispatcher);
		}
		catch (Throwable t) {
			throw new RuntimeException(t.getMessage(), t);
		}
	}

	public static NetworkEnvironment getMock() {
		return networkEnvironment;
	}
}
