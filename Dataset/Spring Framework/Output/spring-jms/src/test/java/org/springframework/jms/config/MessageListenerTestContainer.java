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

package org.springframework.jms.config;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jms.JmsException;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.jms.support.QosSettings;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.destination.DestinationResolver;

/**
 * @author Stephane Nicoll
 */
public class MessageListenerTestContainer implements MessageListenerContainer, InitializingBean, DisposableBean {

	private final JmsListenerEndpoint endpoint;

	private boolean autoStartup = true;

	private boolean startInvoked;

	private boolean initializationInvoked;

	private boolean stopInvoked;

	private boolean destroyInvoked;


	MessageListenerTestContainer(JmsListenerEndpoint endpoint) {
		this.endpoint = endpoint;
	}


	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
	}

	public JmsListenerEndpoint getEndpoint() {
		return this.endpoint;
	}

	public boolean isStarted() {
		return this.startInvoked && this.initializationInvoked;
	}

	public boolean isStopped() {
		return this.stopInvoked && this.destroyInvoked;
	}

		public void start() throws JmsException {
		if (!this.initializationInvoked) {
			throw new IllegalStateException("afterPropertiesSet should have been invoked before start on " + this);
		}
		if (this.startInvoked) {
			throw new IllegalStateException("Start already invoked on " + this);
		}
		this.startInvoked = true;
	}

		public void stop() throws JmsException {
		if (this.stopInvoked) {
			throw new IllegalStateException("Stop already invoked on " + this);
		}
		this.stopInvoked = true;
	}

		public boolean isRunning() {
		return this.startInvoked && !this.stopInvoked;
	}

		public int getPhase() {
		return 0;
	}

		public boolean isAutoStartup() {
		return this.autoStartup;
	}

		public void stop(Runnable callback) {
		this.stopInvoked = true;
		callback.run();
	}

		public void setupMessageListener( @Nullable Object messageListener) {
	}

		 @Nullable public MessageConverter getMessageConverter() {
		return null;
	}

		 @Nullable public DestinationResolver getDestinationResolver() {
		return null;
	}

		public boolean isPubSubDomain() {
		return true;
	}

		public boolean isReplyPubSubDomain() {
		return isPubSubDomain();
	}

		 @Nullable public QosSettings getReplyQosSettings() {
		return null;
	}

		public void afterPropertiesSet() {
		this.initializationInvoked = true;
	}

		public void destroy() {
		if (!this.stopInvoked) {
			throw new IllegalStateException("Stop should have been invoked before " + "destroy on " + this);
		}
		this.destroyInvoked = true;
	}

		public String toString() {
		final StringBuilder sb = new StringBuilder("TestContainer{");
		sb.append("endpoint=").append(this.endpoint);
		sb.append(", startInvoked=").append(this.startInvoked);
		sb.append(", initializationInvoked=").append(this.initializationInvoked);
		sb.append(", stopInvoked=").append(this.stopInvoked);
		sb.append(", destroyInvoked=").append(this.destroyInvoked);
		sb.append('}');
		return sb.toString();
	}

}
