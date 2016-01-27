package org.akigrafsoft.jmskonnector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.QueueConnectionFactory;
import javax.jms.TextMessage;

import com.akigrafsoft.knetthreads.ExceptionDuplicate;
import com.akigrafsoft.knetthreads.konnector.KonnectorConfiguration;
import com.akigrafsoft.knetthreads.konnector.KonnectorDataobject;
import com.akigrafsoft.knetthreads.konnector.SessionBasedClientKonnector;

public class JmsClientKonnector extends SessionBasedClientKonnector implements
		javax.jms.MessageListener {

	class QueueConn {
		javax.jms.QueueConnection connection;
		javax.jms.QueueSession session;

		javax.jms.MessageProducer producer;

		javax.jms.Queue destination;
		javax.jms.MessageConsumer consumer;
	}

	private QueueConnectionFactory m_factory;

	private String m_destinationQueueName;

	public enum MessageCorrelationPattern {
		MessageId, CorrelationId
	}

	private MessageCorrelationPattern m_correlationPattern = MessageCorrelationPattern.MessageId;

	private int m_responseWaitTimes = 5;
	private int m_responseWaitTimeout;

	private AtomicInteger m_counter = new AtomicInteger();

	// private Object m_messageMapLock = new Object();
	private ConcurrentHashMap<String, KonnectorDataobject> m_messageMap = new ConcurrentHashMap<String, KonnectorDataobject>();

	public JmsClientKonnector(String name) throws ExceptionDuplicate {
		super(name);
		m_factory = null;
	}

	@Override
	public void doLoadConfig(KonnectorConfiguration config) {
		super.doLoadConfig(config);

		JmsClientConfiguration l_config = (JmsClientConfiguration) config;

		m_factory = new com.tibco.tibjms.TibjmsQueueConnectionFactory(
				l_config.getServerUrl());

		m_destinationQueueName = l_config.getDestinationQueueName();

		if ((l_config.getCorrelationPattern() != null)
				&& l_config.getCorrelationPattern().equalsIgnoreCase(
						"CorrelationId")) {
			m_correlationPattern = MessageCorrelationPattern.CorrelationId;
		}

		m_responseWaitTimeout = l_config.getResponseWaitTimeout()
				/ m_responseWaitTimes;

		if (m_responseWaitTimeout == 0)
			m_responseWaitTimeout = 5;
	}

	@Override
	protected void execute(KonnectorDataobject dataobject, Session session) {
		QueueConn jms_session = (QueueConn) session.getUserObject();
		TextMessage message;
		String l_id = null;
		try {
			message = jms_session.session.createTextMessage();

			// It would be better to use correlation pattern
			// but this means the server shall copy it to the response message
			// and this is not the case here they wanted to use messageId
			// pattern
			if (m_correlationPattern == MessageCorrelationPattern.CorrelationId) {
				l_id = dataobject.getMatchingId();
				if ((l_id == null) || (l_id.equals(""))) {
					l_id = "ID:" + this.getName().toUpperCase() + ":"
							+ session.getId() + ":"
							+ m_counter.incrementAndGet();
				}
				message.setJMSCorrelationID(l_id);
			}

			message.setJMSReplyTo(jms_session.destination);

			message.setText(dataobject.outboundBuffer);

			// System.out.println("getJMSMessageID = "+message.getJMSMessageID());
			// System.out.println("getJMSCorrelationID = "+message.getJMSCorrelationID());

			// m_messageMap.put(l_id, dataobject);

		} catch (JMSException e) {
			e.printStackTrace();
			this.notifyNetworkError(dataobject, session, e.getMessage());
			return;
		}

		if (m_correlationPattern == MessageCorrelationPattern.CorrelationId) {
			m_messageMap.put(l_id, dataobject);
		}

		try {
			jms_session.producer.send(message);
		} catch (JMSException e) {
			e.printStackTrace();
			this.notifyNetworkError(dataobject, session, e.getMessage());
		}

		if (m_correlationPattern == MessageCorrelationPattern.CorrelationId) {
			return;
		}

		try {

			//
			// In the message Id pattern we have to implement an active receiver
			// with message selector based on JMSMessageID set by send method
			// this is very bad for performance as this locks a session waiting
			// for a response and if time expires no one will retrieve the
			// message later on.
			javax.jms.QueueReceiver receiver = jms_session.session
					.createReceiver(jms_session.destination,
							"JMSCorrelationID='" + message.getJMSMessageID()
									+ "'");
			Message responseMessage = null;
			for (int i = 0; i < m_responseWaitTimes; i++) {
				responseMessage = receiver.receive(m_responseWaitTimeout);
				if (responseMessage != null) {
					break;
				}
			}

			// do not forget to close the receiver here !
			// otherwise the JMS manager will start consuming big CPU
			receiver.close();

			if (responseMessage == null) {
				notifyNetworkError(
						dataobject,
						session,
						"waiting time expired for message id<"
								+ message.getJMSMessageID() + ">");
				return;
			}

			if (responseMessage instanceof TextMessage) {
				TextMessage txtMsg = (TextMessage) responseMessage;
				try {
					dataobject.inboundBuffer = txtMsg.getText();
				} catch (JMSException e) {
					String reason = "getText failed(" + e.getMessage() + ")";
					ActivityLogger.warn(buildActivityLog(
							dataobject.getMessage(), reason));
					notifyNetworkError(dataobject, session, reason);
					return;
				}
			} else {
				ActivityLogger.warn(buildActivityLog(dataobject.getMessage(),
						"responseMessage is not a TextMessage"));
				notifyNetworkError(dataobject, session,
						"responseMessage is not a TextMessage");
				return;
			}

			if (ActivityLogger.isInfoEnabled())
				ActivityLogger
						.info(buildActivityLog(dataobject.getMessage(),
								"response received <"
										+ dataobject.inboundBuffer + ">"));

			notifyExecuteCompleted(dataobject);
		} catch (JMSException e) {
			e.printStackTrace();
			this.notifyNetworkError(dataobject, session, e.getMessage());
		}
	}

	@Override
	public void onMessage(Message responseMessage) {

		// Note that this is not used for MessageId pattern,
		// This is only for CorrelationId pattern

		KonnectorDataobject dataobject = null;
		String l_id;
		try {
			l_id = responseMessage.getJMSCorrelationID();
			dataobject = m_messageMap.remove(l_id);
		} catch (JMSException e) {
			ActivityLogger.warn(buildActivityLog(null,
					"getJMSCorrelationID failed(" + e.getMessage() + ")"));
			return;
		}

		if (dataobject == null) {
			ActivityLogger.warn(buildActivityLog(null,
					"dataobject not found for JMSCorrelationID<" + l_id + ">"));
			return;
		}

		assert (dataobject != null);

		if (responseMessage instanceof TextMessage) {
			TextMessage txtMsg = (TextMessage) responseMessage;
			try {
				dataobject.inboundBuffer = txtMsg.getText();
			} catch (JMSException e) {
				ActivityLogger.warn(buildActivityLog(dataobject.getMessage(),
						"getText failed(" + e.getMessage() + ")"));
				return;
			}

			if (ActivityLogger.isInfoEnabled())
				ActivityLogger
						.info(buildActivityLog(dataobject.getMessage(),
								"onMessage received <"
										+ dataobject.inboundBuffer + ">"));

		} else {
			ActivityLogger.warn(buildActivityLog(dataobject.getMessage(),
					"responseMessage is not a TextMessage"));
			return;
		}

		notifyExecuteCompleted(dataobject);
	}

	@Override
	protected void createSession(Session session)
			throws ExceptionCreateSessionFailed {

		QueueConn jms_session = new QueueConn();

		try {
			jms_session.connection = m_factory.createQueueConnection();
			jms_session.session = jms_session.connection.createQueueSession(
					false, javax.jms.Session.AUTO_ACKNOWLEDGE);
			jms_session.producer = jms_session.session
					.createProducer(jms_session.session
							.createQueue(m_destinationQueueName));

			jms_session.destination = jms_session.session
					.createTemporaryQueue();

			if (m_correlationPattern == MessageCorrelationPattern.CorrelationId) {
				// For correlationId pattern a listener common to all sessions
				// is used
				jms_session.consumer = jms_session.session
						.createConsumer(jms_session.destination);
				jms_session.consumer.setMessageListener(this);
			}
		} catch (JMSException e) {
			e.printStackTrace();
			throw new ExceptionCreateSessionFailed(e.getMessage());
		}

		session.setUserObject(jms_session);
	}

	@Override
	public void async_startSession(Session session) {
		QueueConn jms_session = (QueueConn) session.getUserObject();
		try {
			jms_session.connection.start();
		} catch (JMSException e) {
			e.printStackTrace();
			this.sessionDied(session);
			return;
		}
		this.sessionStarted(session);
	}

	@Override
	protected void async_stopSession(Session session) {
		QueueConn jms_session = (QueueConn) session.getUserObject();
		try {
			jms_session.connection.close();
		} catch (JMSException e) {
			e.printStackTrace();
		}
		this.sessionStopped(session);
	}
}
