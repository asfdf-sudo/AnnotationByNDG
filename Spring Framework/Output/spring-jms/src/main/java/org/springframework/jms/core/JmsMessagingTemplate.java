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

package org.springframework.jms.core;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import java.util.Map;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jms.InvalidDestinationException;
import org.springframework.jms.JmsException;
import org.springframework.jms.support.JmsAccessor;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessagingMessageConverter;
import org.springframework.jms.support.converter.SimpleMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.core.AbstractMessagingTemplate;
import org.springframework.messaging.core.DestinationResolutionException;
import org.springframework.messaging.core.MessagePostProcessor;
import org.springframework.util.Assert;

/**
 * An implementation of {@link JmsMessageOperations}, as a wrapper on top of Spring's
 * traditional {@link JmsTemplate}. Aligned with the {@code spring-messaging} module
 * and {@link org.springframework.messaging.core.GenericMessagingTemplate}.
 *
 * <p>Note: Operations in this interface throw {@link MessagingException} instead of
 * the JMS-specific {@link org.springframework.jms.JmsException}, aligning with the
 * {@code spring-messaging} module and its other client operation handles.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.1
 * @see JmsTemplate
 * @see JmsClient
 */
public class JmsMessagingTemplate extends AbstractMessagingTemplate<Destination>
		implements JmsMessageOperations, InitializingBean {

	private @Nullable JmsOperations jmsTemplate;

	private MessageConverter jmsMessageConverter;

	private boolean converterSet;

	private @Nullable String defaultDestinationName;


	/**
	 * Constructor for use with bean properties.
	 * Requires {@link #setConnectionFactory} or {@link #setJmsTemplate} to be called.
	 */
	public JmsMessagingTemplate() {
		this.jmsMessageConverter = new MessagingMessageConverter();
	}

	/**
	 * Create a {@code JmsMessagingTemplate} instance with the JMS {@link ConnectionFactory}
	 * to use, implicitly building a {@link JmsTemplate} based on it.
	 * @since 4.1.2
	 */
	public JmsMessagingTemplate(ConnectionFactory connectionFactory) {
		this(new JmsTemplate(connectionFactory));
	}

	/**
	 * Create a {@code JmsMessagingTemplate} instance with the {@link JmsTemplate} to use.
	 */
	public JmsMessagingTemplate( @Nullable JmsTemplate jmsTemplate) {
		Assert.notNull(jmsTemplate, "JmsTemplate must not be null");
		this.jmsTemplate = jmsTemplate;
		this.jmsMessageConverter = new MessagingMessageConverter(jmsTemplate.getMessageConverter());
	}

	/**
	 * Create a {@code JmsMessagingTemplate} instance with the {@link JmsOperations} to use.
	 * @since 7.0
	 */
	public JmsMessagingTemplate( @Nullable JmsOperations jmsTemplate) {
		Assert.notNull(jmsTemplate, "JmsTemplate must not be null");
		this.jmsTemplate = jmsTemplate;
		this.jmsMessageConverter = (jmsTemplate instanceof JmsTemplate template ?
				new MessagingMessageConverter(template.getMessageConverter()) : new MessagingMessageConverter());
	}


	/**
	 * Set the ConnectionFactory to use for the underlying {@link JmsTemplate}.
	 * @since 4.1.2
	 */
	public void setConnectionFactory(ConnectionFactory connectionFactory) {
		if (this.jmsTemplate instanceof JmsAccessor accessor) {
			JmsTemplate template = new JmsTemplate(accessor);
			template.setConnectionFactory(connectionFactory);
			this.jmsTemplate = template;
		}
		else {
			this.jmsTemplate = new JmsTemplate(connectionFactory);
		}
	}

	/**
	 * Return the ConnectionFactory that the underlying {@link JmsTemplate} uses.
	 * @since 4.1.2
	 */
	 @Nullable public ConnectionFactory getConnectionFactory() {
		return (this.jmsTemplate instanceof JmsTemplate template ? template.getConnectionFactory() : null);
	}

	/**
	 * Set the {@link JmsTemplate} to use.
	 */
	public void setJmsTemplate(JmsTemplate jmsTemplate) {
		this.jmsTemplate = jmsTemplate;
	}

	/**
	 * Return the configured {@link JmsTemplate}.
	 */
	 @Nullable public JmsTemplate getJmsTemplate() {
		return (this.jmsTemplate instanceof JmsTemplate template ? template : null);
	}

	/**
	 * Set the {@link MessageConverter} to use to convert a {@link Message}
	 * to and from a {@link jakarta.jms.Message}.
	 * <p>By default, a {@link MessagingMessageConverter} is defined using a
	 * {@link SimpleMessageConverter} to convert the payload of the message.
	 * <p>Consider configuring a {@link MessagingMessageConverter} with a different
	 * {@link MessagingMessageConverter#setPayloadConverter(MessageConverter) payload converter}
	 * for more advanced scenarios.
	 * @see org.springframework.jms.support.converter.MessagingMessageConverter
	 */
	public void setJmsMessageConverter(MessageConverter jmsMessageConverter) {
		Assert.notNull(jmsMessageConverter, "MessageConverter must not be null");
		this.jmsMessageConverter = jmsMessageConverter;
		this.converterSet = true;
	}

	/**
	 * Return the {@link MessageConverter} to use to convert a {@link Message}
	 * to and from a {@link jakarta.jms.Message}.
	 */
	public MessageConverter getJmsMessageConverter() {
		return this.jmsMessageConverter;
	}

	/**
	 * Configure the default destination name to use in send methods that don't have
	 * a destination argument. If a default destination is not configured, send methods
	 * without a destination argument will raise an exception if invoked.
	 * @see #setDefaultDestination(Object)
	 */
	public void setDefaultDestinationName(String defaultDestinationName) {
		this.defaultDestinationName = defaultDestinationName;
	}

	/**
	 * Return the configured default destination name.
	 */
	public String getDefaultDestinationName() {
		return this.defaultDestinationName;
	}

		public void afterPropertiesSet() {
		Assert.notNull(this.jmsTemplate, "Property 'connectionFactory' or 'jmsTemplate' is required");
		if (!this.converterSet && this.jmsTemplate instanceof JmsTemplate template) {
			((MessagingMessageConverter) this.jmsMessageConverter).setPayloadConverter(template.getMessageConverter());
		}
	}

	JmsOperations obtainJmsTemplate() {
		Assert.state(this.jmsTemplate != null, "No JmsTemplate set");
		return this.jmsTemplate;
	}


		public void send(Message<?> message) throws MessagingException {
		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			send(defaultDestination, message);
		}
		else {
			send(getRequiredDefaultDestinationName(), message);
		}
	}

		public void convertAndSend(Object payload, Map<String, Object> headers,
			MessagePostProcessor postProcessor) throws MessagingException {

		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			convertAndSend(defaultDestination, payload, headers, postProcessor);
		}
		else {
			convertAndSend(getRequiredDefaultDestinationName(), payload, headers, postProcessor);
		}
	}

		public void send(String destinationName, Message<?> message) throws MessagingException {
		doSend(destinationName, message);
	}

		public void convertAndSend(String destinationName, Object payload) throws MessagingException {
		convertAndSend(destinationName, payload, (Map<String, Object>) null);
	}

		public void convertAndSend(String destinationName, Object payload, Map<String, Object> headers)
			throws MessagingException {

		convertAndSend(destinationName, payload, headers, null);
	}

		public void convertAndSend(String destinationName, Object payload, MessagePostProcessor postProcessor)
			throws MessagingException {

		convertAndSend(destinationName, payload, null, postProcessor);
	}

		public void convertAndSend(String destinationName, Object payload, Map<String, Object> headers,
			MessagePostProcessor postProcessor) throws MessagingException {

		Message<?> message = doConvert(payload, headers, postProcessor);
		send(destinationName, message);
	}

		public Message<?> receive() throws MessagingException {
		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			return receive(defaultDestination);
		}
		else {
			return receive(getRequiredDefaultDestinationName());
		}
	}

		public <T> T receiveAndConvert(Class<T> targetClass) throws MessagingException {
		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			return receiveAndConvert(defaultDestination, targetClass);
		}
		else {
			return receiveAndConvert(getRequiredDefaultDestinationName(), targetClass);
		}
	}

		public Message<?> receive(String destinationName) throws MessagingException {
		return doReceive(destinationName);
	}

		 @Nullable public <T> T receiveAndConvert(String destinationName, Class<T> targetClass) throws MessagingException {
		Message<?> message = doReceive(destinationName);
		if (message != null) {
			return doConvert(message, targetClass);
		}
		else {
			return null;
		}
	}

		public Message<?> receiveSelected(String messageSelector) throws MessagingException {
		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			return receiveSelected(defaultDestination, messageSelector);
		}
		else {
			return receiveSelected(getRequiredDefaultDestinationName(), messageSelector);
		}
	}

		public Message<?> receiveSelected(Destination destination, String messageSelector)
			throws MessagingException {

		return doReceiveSelected(destination, messageSelector);
	}

		public Message<?> receiveSelected(String destinationName, String messageSelector)
			throws MessagingException {

		return doReceiveSelected(destinationName, messageSelector);
	}

		public <T> T receiveSelectedAndConvert(String messageSelector, Class<T> targetClass)
			throws MessagingException {

		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			return receiveSelectedAndConvert(defaultDestination, messageSelector, targetClass);
		}
		else {
			return receiveSelectedAndConvert(getRequiredDefaultDestinationName(), messageSelector, targetClass);
		}
	}

		 @Nullable public <T> T receiveSelectedAndConvert(Destination destination, String messageSelector,
			Class<T> targetClass) throws MessagingException {

		Message<?> message = doReceiveSelected(destination, messageSelector);
		if (message != null) {
			return doConvert(message, targetClass);
		}
		else {
			return null;
		}
	}

		 @Nullable public <T> T receiveSelectedAndConvert(String destinationName, String messageSelector,
			Class<T> targetClass) throws MessagingException {

		Message<?> message = doReceiveSelected(destinationName, messageSelector);
		if (message != null) {
			return doConvert(message, targetClass);
		}
		else {
			return null;
		}
	}

		public Message<?> sendAndReceive(Message<?> requestMessage) throws MessagingException {
		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			return sendAndReceive(defaultDestination, requestMessage);
		}
		else {
			return sendAndReceive(getRequiredDefaultDestinationName(), requestMessage);
		}
	}

		public Message<?> sendAndReceive(String destinationName, Message<?> requestMessage)
			throws MessagingException {

		return doSendAndReceive(destinationName, requestMessage);
	}

		public <T> T convertSendAndReceive(String destinationName, Object request, Class<T> targetClass)
			throws MessagingException {

		return convertSendAndReceive(destinationName, request, null, targetClass);
	}

		public <T> T convertSendAndReceive(Object request, Class<T> targetClass) throws MessagingException {
		return convertSendAndReceive(request, targetClass, null);
	}

		public <T> T convertSendAndReceive(String destinationName, Object request,
			Map<String, Object> headers, Class<T> targetClass) throws MessagingException {

		return convertSendAndReceive(destinationName, request, headers, targetClass, null);
	}

		public <T> T convertSendAndReceive(Object request, Class<T> targetClass,
			MessagePostProcessor postProcessor) throws MessagingException {

		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			return convertSendAndReceive(defaultDestination, request, targetClass, postProcessor);
		}
		else {
			return convertSendAndReceive(getRequiredDefaultDestinationName(), request, targetClass, postProcessor);
		}
	}

		public <T> T convertSendAndReceive(String destinationName, Object request, Class<T> targetClass,
			MessagePostProcessor requestPostProcessor) throws MessagingException {

		return convertSendAndReceive(destinationName, request, null, targetClass, requestPostProcessor);
	}

			 @Nullable public <T> T convertSendAndReceive(String destinationName, Object request,
			Map<String, Object> headers, Class<T> targetClass,
			MessagePostProcessor postProcessor) throws MessagingException {

		Message<?> requestMessage = doConvert(request, headers, postProcessor);
		Message<?> replyMessage = sendAndReceive(destinationName, requestMessage);
		return (replyMessage != null ? (T) getMessageConverter().fromMessage(replyMessage, targetClass) : null);
	}

		public <T> T convertSendAndReceive(Object request, Map<String, Object> headers,
			Class<T> targetClass, MessagePostProcessor postProcessor) throws MessagingException {

		Destination defaultDestination = getDefaultDestination();
		if (defaultDestination != null) {
			return convertSendAndReceive(defaultDestination, request, headers, targetClass, postProcessor);
		}
		else {
			return convertSendAndReceive(getRequiredDefaultDestinationName(), request, headers, targetClass, postProcessor);
		}
	}

		protected void doSend(Destination destination, Message<?> message) {
		try {
			obtainJmsTemplate().send(destination, createMessageCreator(message));
		}
		catch (JmsException ex) {
			throw convertJmsException(ex);
		}
	}

	protected void doSend(String destinationName, Message<?> message) {
		try {
			obtainJmsTemplate().send(destinationName, createMessageCreator(message));
		}
		catch (JmsException ex) {
			throw convertJmsException(ex);
		}
	}

		protected Message<?> doReceive(Destination destination) {
		try {
			jakarta.jms.Message jmsMessage = obtainJmsTemplate().receive(destination);
			return convertJmsMessage(jmsMessage);
		}
		catch (JmsException ex) {
			throw convertJmsException(ex);
		}
	}

	protected Message<?> doReceive(String destinationName) {
		try {
			jakarta.jms.Message jmsMessage = obtainJmsTemplate().receive(destinationName);
			return convertJmsMessage(jmsMessage);
		}
		catch (JmsException ex) {
			throw convertJmsException(ex);
		}
	}

	protected Message<?> doReceiveSelected(Destination destination, String messageSelector) {
		try {
			jakarta.jms.Message jmsMessage = obtainJmsTemplate().receiveSelected(destination, messageSelector);
			return convertJmsMessage(jmsMessage);
		}
		catch (JmsException ex) {
			throw convertJmsException(ex);
		}
	}

	protected Message<?> doReceiveSelected(String destinationName, String messageSelector) {
		try {
			jakarta.jms.Message jmsMessage = obtainJmsTemplate().receiveSelected(destinationName, messageSelector);
			return convertJmsMessage(jmsMessage);
		}
		catch (JmsException ex) {
			throw convertJmsException(ex);
		}
	}

		protected Message<?> doSendAndReceive(Destination destination, Message<?> requestMessage) {
		try {
			jakarta.jms.Message jmsMessage = obtainJmsTemplate().sendAndReceive(
					destination, createMessageCreator(requestMessage));
			return convertJmsMessage(jmsMessage);
		}
		catch (JmsException ex) {
			throw convertJmsException(ex);
		}
	}

	protected Message<?> doSendAndReceive(String destinationName, Message<?> requestMessage) {
		try {
			jakarta.jms.Message jmsMessage = obtainJmsTemplate().sendAndReceive(
					destinationName, createMessageCreator(requestMessage));
			return convertJmsMessage(jmsMessage);
		}
		catch (JmsException ex) {
			throw convertJmsException(ex);
		}
	}

	private MessagingMessageCreator createMessageCreator(Message<?> message) {
		return new MessagingMessageCreator(message, getJmsMessageConverter());
	}

	protected String getRequiredDefaultDestinationName() {
		String name = getDefaultDestinationName();
		if (name == null) {
			throw new IllegalStateException("No 'defaultDestination' or 'defaultDestinationName' specified. " +
					"Check configuration of JmsMessagingTemplate.");
		}
		return name;
	}

	 @Nullable protected Message<?> convertJmsMessage( @Nullable jakarta.jms.Message message) {
		if (message == null) {
			return null;
		}
		try {
			return (Message<?>) getJmsMessageConverter().fromMessage(message);
		}
		catch (Exception ex) {
			throw new MessageConversionException("Could not convert '" + message + "'", ex);
		}
	}

	protected MessagingException convertJmsException( @Nullable JmsException ex) {
		if (ex instanceof org.springframework.jms.support.destination.DestinationResolutionException ||
				ex instanceof InvalidDestinationException) {
			return new DestinationResolutionException(ex.getMessage(), ex);
		}
		if (ex instanceof org.springframework.jms.support.converter.MessageConversionException) {
			return new MessageConversionException(ex.getMessage(), ex);
		}
		// Fallback
		return new MessagingException(ex.getMessage(), ex);
	}


	private static class MessagingMessageCreator implements MessageCreator {

		private final Message<?> message;

		private final MessageConverter messageConverter;

		public MessagingMessageCreator(Message<?> message, MessageConverter messageConverter) {
			this.message = message;
			this.messageConverter = messageConverter;
		}

				public jakarta.jms.Message createMessage(Session session) throws JMSException {
			try {
				return this.messageConverter.toMessage(this.message, session);
			}
			catch (Exception ex) {
				throw new MessageConversionException(
						"Could not convert '" + this.message + "': " + ex.getMessage(), ex);
			}
		}
	}

}
