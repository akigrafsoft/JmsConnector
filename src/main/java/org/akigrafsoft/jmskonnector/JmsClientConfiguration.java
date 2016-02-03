package org.akigrafsoft.jmskonnector;

import com.akigrafsoft.knetthreads.ExceptionAuditFailed;
import com.akigrafsoft.knetthreads.konnector.SessionBasedClientKonnectorConfiguration;

/**
 * Configuration class for {@link JmsClientKonnector}
 * <p>
 * <b>This MUST be a Java bean</b>
 * </p>
 * 
 * @author kmoyse
 * 
 */
public final class JmsClientConfiguration extends SessionBasedClientKonnectorConfiguration {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5875956400137823344L;

	private static String DEFAULT_serverUrl = "tcp://localhost:7222";

	private String serverUrl = DEFAULT_serverUrl;
	private String destinationQueueName;
	private String correlationPattern = null;

	private int responseWaitTimeout = 300;

	// ------------------------------------------------------------------------
	// Java Bean

	public String getServerUrl() {
		return serverUrl;
	}

	public void setServerUrl(String serverUrl) {
		this.serverUrl = serverUrl;
	}

	public String getDestinationQueueName() {
		return destinationQueueName;
	}

	public void setDestinationQueueName(String destinationQueueName) {
		this.destinationQueueName = destinationQueueName;
	}

	public String getCorrelationPattern() {
		return correlationPattern;
	}

	public void setCorrelationPattern(String correlationPattern) {
		this.correlationPattern = correlationPattern;
	}

	public int getResponseWaitTimeout() {
		return responseWaitTimeout;
	}

	public void setResponseWaitTimeout(int responseWaitTimeout) {
		this.responseWaitTimeout = responseWaitTimeout;
	}

	// ------------------------------------------------------------------------
	// Configuration
	@Override
	public void audit() throws ExceptionAuditFailed {
		super.audit();
		if ((serverUrl == null) || serverUrl.equals("")) {
			throw new ExceptionAuditFailed("serverUrl must be provided and non empty, default=" + DEFAULT_serverUrl);
		}
		if ((destinationQueueName == null) || destinationQueueName.equals("")) {
			throw new ExceptionAuditFailed("serverUrl must be provided and non empty");
		}
		if ((correlationPattern != null)) {
			if (!correlationPattern.equalsIgnoreCase("MessageId")
					&& !correlationPattern.equalsIgnoreCase("CorrelationId")) {
				throw new ExceptionAuditFailed(
						"if provided, correlationPattern must be either MessageId(default) or CorrelationId");
			}
		}
	}
}
