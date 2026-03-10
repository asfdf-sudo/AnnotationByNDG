/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.jca;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import javax.transaction.xa.XAResource;

import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;

/**
 * @author Juergen Hoeller
 */
public class StubResourceAdapter implements ResourceAdapter {

		public void start( @Nullable BootstrapContext bootstrapContext) {
	}

		public void stop() {
	}

		public void endpointActivation( @Nullable MessageEndpointFactory messageEndpointFactory,  @Nullable ActivationSpec activationSpec) {
	}

		public void endpointDeactivation( @Nullable MessageEndpointFactory messageEndpointFactory,  @Nullable ActivationSpec activationSpec) {
	}

		 @Nullable public XAResource[] getXAResources( @Nullable ActivationSpec[] activationSpecs) {
		return null;
	}

}
