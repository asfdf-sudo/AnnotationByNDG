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

package org.springframework.jms;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;

/**
 * Stub JMS Message implementation intended for testing purposes only.
 *
 * @author Mark Fisher
 * @since 4.1
 */
public class StubTextMessage implements TextMessage {

	private String messageId;

	private String text;

	private int deliveryMode = DEFAULT_DELIVERY_MODE;

	private Destination destination;

	private String correlationId;

	private Destination replyTo;

	private String type;

	private long deliveryTime;

	private long timestamp = 0L;

	private long expiration = 0L;

	private int priority = DEFAULT_PRIORITY;

	private boolean redelivered;

	private ConcurrentHashMap<String, Object> properties = new ConcurrentHashMap<>();


	public StubTextMessage() {
	}

	public StubTextMessage(String text) {
		this.text = text;
	}


		 @Nullable public String getText() {
		return this.text;
	}

		public void setText(String text) {
		this.text = text;
	}

		public void acknowledge() {
		throw new UnsupportedOperationException();
	}

		public void clearBody() {
		this.text = null;
	}

		public void clearProperties() {
		this.properties.clear();
	}

		public boolean getBooleanProperty(String name) {
		Object value = this.properties.get(name);
		return (value instanceof Boolean b) ? b : false;
	}

		public byte getByteProperty( @Nullable String name) {
		Object value = this.properties.get(name);
		return (value instanceof Byte b) ? b : 0;
	}

		public double getDoubleProperty(String name) {
		Object value = this.properties.get(name);
		return (value instanceof Double d) ? d : 0;
	}

		public float getFloatProperty(String name) {
		Object value = this.properties.get(name);
		return (value instanceof Float f) ? f : 0;
	}

		public int getIntProperty(String name) {
		Object value = this.properties.get(name);
		return (value instanceof Integer i) ? i : 0;
	}

		public String getJMSCorrelationID() throws JMSException {
		return this.correlationId;
	}

		public byte[] getJMSCorrelationIDAsBytes() {
		return this.correlationId.getBytes();
	}

		public int getJMSDeliveryMode() throws JMSException {
		return this.deliveryMode;
	}

		public Destination getJMSDestination() throws JMSException {
		return this.destination;
	}

		public long getJMSExpiration() throws JMSException {
		return this.expiration;
	}

		public String getJMSMessageID() throws JMSException {
		return this.messageId;
	}

		public int getJMSPriority() throws JMSException {
		return this.priority;
	}

		public boolean getJMSRedelivered() throws JMSException {
		return this.redelivered;
	}

		public Destination getJMSReplyTo() throws JMSException {
		return this.replyTo;
	}

		public long getJMSTimestamp() throws JMSException {
		return this.timestamp;
	}

		public String getJMSType() throws JMSException {
		return this.type;
	}

		public long getJMSDeliveryTime() {
		return this.deliveryTime;
	}

		public long getLongProperty(String name) {
		Object value = this.properties.get(name);
		return (value instanceof Long l) ? l : 0;
	}

		public Object getObjectProperty(String name) throws JMSException {
		return this.properties.get(name);
	}

		public Enumeration<?> getPropertyNames() {
		return this.properties.keys();
	}

		public short getShortProperty( @Nullable String name) {
		Object value = this.properties.get(name);
		return (value instanceof Short s) ? s : 0;
	}

		 @Nullable public String getStringProperty(String name) {
		Object value = this.properties.get(name);
		return (value instanceof String text) ? text : null;
	}

		public boolean propertyExists( @Nullable String name) {
		return this.properties.containsKey(name);
	}

		public void setBooleanProperty(String name, boolean value) {
		this.properties.put(name, value);
	}

		public void setByteProperty(String name, byte value) {
		this.properties.put(name, value);
	}

		public void setDoubleProperty(String name, double value) {
		this.properties.put(name, value);
	}

		public void setFloatProperty(String name, float value) {
		this.properties.put(name, value);
	}

		public void setIntProperty(String name, int value) {
		this.properties.put(name, value);
	}

		public void setJMSCorrelationID( @Nullable String correlationId) throws JMSException {
		this.correlationId = correlationId;
	}

		public void setJMSCorrelationIDAsBytes(byte[] correlationID) {
		this.correlationId = new String(correlationID);
	}

		public void setJMSDeliveryMode(int deliveryMode) {
		this.deliveryMode = deliveryMode;
	}

		public void setJMSDestination(Destination destination) {
		this.destination = destination;
	}

		public void setJMSExpiration(long expiration) {
		this.expiration = expiration;
	}

		public void setJMSMessageID(String id) {
		this.messageId = id;
	}

		public void setJMSPriority(int priority) {
		this.priority = priority;
	}

		public void setJMSRedelivered(boolean redelivered) {
		this.redelivered = redelivered;
	}

		public void setJMSReplyTo(Destination replyTo) throws JMSException {
		this.replyTo = replyTo;
	}

		public void setJMSTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

		public void setJMSType(String type) throws JMSException {
		this.type = type;
	}

		public void setJMSDeliveryTime(long deliveryTime) {
		this.deliveryTime = deliveryTime;
	}

		public void setLongProperty(String name, long value) {
		this.properties.put(name, value);
	}

		public void setObjectProperty( @Nullable String name,  @Nullable Object value) throws JMSException {
		this.properties.put(name, value);
	}

		public void setShortProperty(String name, short value) {
		this.properties.put(name, value);
	}

		public void setStringProperty(String name, String value) {
		this.properties.put(name, value);
	}

		 @Nullable public <T> T getBody(Class<T> c) {
		return null;
	}

			public boolean isBodyAssignableTo(Class c) {
		return false;
	}

}
