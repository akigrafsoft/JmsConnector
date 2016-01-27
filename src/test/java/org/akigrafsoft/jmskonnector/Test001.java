package org.akigrafsoft.jmskonnector;

import static org.junit.Assert.fail;

import org.junit.Test;

import com.akigrafsoft.knetthreads.ExceptionAuditFailed;
import com.akigrafsoft.knetthreads.ExceptionDuplicate;
import com.akigrafsoft.knetthreads.Message;
import com.akigrafsoft.knetthreads.konnector.Konnector;
import com.akigrafsoft.knetthreads.konnector.KonnectorDataobject;

public class Test001 {

	private void doSleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void test() {

		/*
		 * Properties prop = new Properties(); InputStream in =
		 * Test001.class.getResourceAsStream
		 * ("//home/kmoyse/workspace/JmsConnector/jndi.properties"); try {
		 * prop.load(in); in.close(); } catch (IOException e1) { // TODO
		 * Auto-generated catch block e1.printStackTrace(); }
		 */

		Konnector konnector;
		try {
			konnector = new JmsClientKonnector("TEST");
		} catch (ExceptionDuplicate e) {
			e.printStackTrace();
			fail("creation failed");
			return;
		}

		JmsClientConfiguration config = new JmsClientConfiguration();
		config.setServerUrl("tcp://corentin.kabira.fr:7222");
		config.setDestinationQueueName("requests");

		try {
			konnector.configure(config);
		} catch (ExceptionAuditFailed e) {
			e.printStackTrace();
			fail("configuration failed");
			return;
		}

		konnector.start();

		doSleep(500);

		Message message = new Message();
		KonnectorDataobject dataobject = new KonnectorDataobject(message);
		dataobject.operationMode = KonnectorDataobject.OperationMode.TWOWAY;
		dataobject.operationSyncMode = KonnectorDataobject.SyncMode.ASYNC;
		dataobject.outboundBuffer = "CreateBalance,EMSTest:Id01,100,10,10000,20,balance,EUR,2";

		konnector.handle(dataobject);

		doSleep(500);

		System.out.println(dataobject.inboundBuffer);

		konnector.stop();

	}

}
