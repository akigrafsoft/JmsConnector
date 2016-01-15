package org.akigrafsoft.jmskonnector;

import com.akigrafsoft.knetthreads.ExceptionAuditFailed;
import com.akigrafsoft.knetthreads.konnector.SessionBasedClientKonnectorConfiguration;

public class JmsClientConfiguration extends
		SessionBasedClientKonnectorConfiguration {

	private static String DEFAULT_serverUrl = "tcp://localhost:7222";

	public String serverUrl = DEFAULT_serverUrl;
	public String destinationQueueName;
	public String correlationPattern = null;

	public int responseWaitTimeout = 300;

	/*
	 * public String hostName = null; public int port = -1;
	 */

	@Override
	public void audit() throws ExceptionAuditFailed {
		super.audit();
		if ((serverUrl == null) || serverUrl.equals("")) {
			throw new ExceptionAuditFailed(
					"serverUrl must be provided and non empty, default="
							+ DEFAULT_serverUrl);
		}
		if ((destinationQueueName == null) || destinationQueueName.equals("")) {
			throw new ExceptionAuditFailed(
					"serverUrl must be provided and non empty");
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
