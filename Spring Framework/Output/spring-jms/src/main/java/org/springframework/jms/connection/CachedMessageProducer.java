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

package org.springframework.jms.connection;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import jakarta.jms.CompletionListener;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.QueueSender;
import jakarta.jms.Topic;
import jakarta.jms.TopicPublisher;
import org.jspecify.annotations.Nullable;

/**
 * JMS MessageProducer decorator that adapts calls to a shared MessageProducer
 * instance underneath, managing QoS settings locally within the decorator.
 *
 * @author Juergen Hoeller
 * @since 2.5.3
 */
class CachedMessageProducer implements MessageProducer, QueueSender, TopicPublisher {

	private final MessageProducer target;

	private @Nullable Boolean originalDisableMessageID;

	private @Nullable Boolean originalDisableMessageTimestamp;

	private @Nullable Long originalDeliveryDelay;

	private int deliveryMode;

	private int priority;

	private long timeToLive;


	public CachedMessageProducer(MessageProducer target) throws JMSException {
		this.target = target;
		this.deliveryMode = target.getDeliveryMode();
		this.priority = target.getPriority();
		this.timeToLive = target.getTimeToLive();
	}


		public void setDisableMessageID(boolean disableMessageID) throws JMSException {
		if (this.originalDisableMessageID == null) {
			this.originalDisableMessageID = this.target.getDisableMessageID();
		}
		this.target.setDisableMessageID(disableMessageID);
	}

		public boolean getDisableMessageID() throws JMSException {
		return this.target.getDisableMessageID();
	}

		public void setDisableMessageTimestamp(boolean disableMessageTimestamp) throws JMSException {
		if (this.originalDisableMessageTimestamp == null) {
			this.originalDisableMessageTimestamp = this.target.getDisableMessageTimestamp();
		}
		this.target.setDisableMessageTimestamp(disableMessageTimestamp);
	}

		public boolean getDisableMessageTimestamp() throws JMSException {
		return this.target.getDisableMessageTimestamp();
	}

		public void setDeliveryDelay(long deliveryDelay) throws JMSException {
		if (this.originalDeliveryDelay == null) {
			this.originalDeliveryDelay = this.target.getDeliveryDelay();
		}
		this.target.setDeliveryDelay(deliveryDelay);
	}

		public long getDeliveryDelay() throws JMSException {
		return this.target.getDeliveryDelay();
	}

		public void setDeliveryMode(int deliveryMode) {
		this.deliveryMode = deliveryMode;
	}

		public int getDeliveryMode() {
		return this.deliveryMode;
	}

		public void setPriority(int priority) {
		this.priority = priority;
	}

		public int getPriority() {
		return this.priority;
	}

		public void setTimeToLive(long timeToLive) {
		this.timeToLive = timeToLive;
	}

		public long getTimeToLive() {
		return this.timeToLive;
	}

		public Destination getDestination() throws JMSException {
		return this.target.getDestination();
	}

		public Queue getQueue() throws JMSException {
		return (Queue) this.target.getDestination();
	}

		public Topic getTopic() throws JMSException {
		return (Topic) this.target.getDestination();
	}

		public void send(Message message) throws JMSException {
		this.target.send(message, this.deliveryMode, this.priority, this.timeToLive);
	}

		public void send(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
		this.target.send(message, deliveryMode, priority, timeToLive);
	}

		public void send(Destination destination, Message message) throws JMSException {
		this.target.send(destination, message, this.deliveryMode, this.priority, this.timeToLive);
	}

		public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
		this.target.send(destination, message, deliveryMode, priority, timeToLive);
	}

		public void send(Message message, CompletionListener completionListener) throws JMSException {
		this.target.send(message, this.deliveryMode, this.priority, this.timeToLive, completionListener);
	}

		public void send(Message message, int deliveryMode, int priority, long timeToLive,
			CompletionListener completionListener) throws JMSException {

		this.target.send(message, deliveryMode, priority, timeToLive, completionListener);
	}

		public void send(Destination destination, Message message, CompletionListener completionListener) throws JMSException {
		this.target.send(destination, message, this.deliveryMode, this.priority, this.timeToLive, completionListener);
	}

		public void send(Destination destination, Message message, int deliveryMode, int priority,
			long timeToLive, CompletionListener completionListener) throws JMSException {

		this.target.send(destination, message, deliveryMode, priority, timeToLive, completionListener);

	}

		public void send(Queue queue, Message message) throws JMSException {
		this.target.send(queue, message, this.deliveryMode, this.priority, this.timeToLive);
	}

		public void send(Queue queue, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
		this.target.send(queue, message, deliveryMode, priority, timeToLive);
	}

		public void publish(Message message) throws JMSException {
		this.target.send(message, this.deliveryMode, this.priority, this.timeToLive);
	}

		public void publish(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
		this.target.send(message, deliveryMode, priority, timeToLive);
	}

		public void publish(Topic topic, Message message) throws JMSException {
		this.target.send(topic, message, this.deliveryMode, this.priority, this.timeToLive);
	}

		public void publish(Topic topic, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
		this.target.send(topic, message, deliveryMode, priority, timeToLive);
	}

		public void close() throws JMSException {
		// It's a cached MessageProducer... reset properties only.
		if (this.originalDisableMessageID != null) {
			this.target.setDisableMessageID(this.originalDisableMessageID);
			this.originalDisableMessageID = null;
		}
		if (this.originalDisableMessageTimestamp != null) {
			this.target.setDisableMessageTimestamp(this.originalDisableMessageTimestamp);
			this.originalDisableMessageTimestamp = null;
		}
		if (this.originalDeliveryDelay != null) {
			this.target.setDeliveryDelay(this.originalDeliveryDelay);
			this.originalDeliveryDelay = null;
		}
	}

		public String toString() {
		return "Cached JMS MessageProducer: " + this.target;
	}

}
