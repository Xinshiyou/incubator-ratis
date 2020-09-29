/**
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

package org.apache.ratis.examples.counter.server;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Scanner;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.examples.counter.CounterCommon;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.server.impl.RaftServerImpl;
import org.apache.ratis.server.impl.RaftServerProxy;
import org.apache.ratis.util.NetUtils;

/**
 * Simplest Ratis server, use a simple state machine {@link CounterStateMachine}
 * which maintain a counter across multi server. This server application
 * designed to run several times with different parameters (1,2 or 3). server
 * addresses hard coded in {@link CounterCommon}
 * <p>
 * Run this application three times with three different parameter set-up a
 * ratis cluster which maintain a counter value replicated in each server memory
 */
public final class CounterServer {

	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT %1$tL] [%4$-7s] %5$s %n");
	}

	public static void setLevel(Level targetLevel) {
		Logger root = Logger.getLogger("");
		root.setLevel(targetLevel);
		for (Handler handler : root.getHandlers()) {
			handler.setLevel(targetLevel);
		}
		System.out.println("level set: " + targetLevel.getName());
	}

	private CounterServer() {
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length < 1) {
			System.err.println(
					"Usage: java -cp *.jar org.apache.ratis.examples.counter.server.CounterServer {serverIndex}");
			System.err.println("{serverIndex} could be 1, 2 or 3");
			System.exit(1);
		}

		setLevel(Level.ALL);

		// find current peer object based on application parameter
		RaftPeer currentPeer = CounterCommon.PEERS.get(Integer.parseInt(args[0]) - 1);

		// create a property object
		RaftProperties properties = new RaftProperties();

		// set the storage directory (different for each peer) in RaftProperty object
		File raftStorageDir = new File("./" + currentPeer.getId().toString());
		RaftServerConfigKeys.setStorageDir(properties, Collections.singletonList(raftStorageDir));

		// set the port which server listen to in RaftProperty object
		final int port = NetUtils.createSocketAddr(currentPeer.getAddress()).getPort();
		GrpcConfigKeys.Server.setPort(properties, port);

		// create the counter state machine which hold the counter value
		CounterStateMachine counterStateMachine = new CounterStateMachine();

		// create and start the Raft server
		RaftServer server = RaftServer.newBuilder().setGroup(CounterCommon.RAFT_GROUP).setProperties(properties)
				.setServerId(currentPeer.getId()).setStateMachine(counterStateMachine).build();
		server.start();

		Thread.sleep(1000 * 10);

		final RaftServerProxy proxy = (RaftServerProxy) server;
		RaftGroupId groupId = null;
		for (int index = 0; index < 300; index++) {
			server.getGroupIds().forEach(temp -> {
				System.out.println("GroupID:" + temp + "\tserverID:" + server.getId());
				if (null == groupId) {
					RaftServerImpl raftServerImpl;
					try {
						raftServerImpl = proxy.getImpl(temp);
						System.out.println("Is Leader ? ==>" + raftServerImpl.isAlive() + "\t"
								+ raftServerImpl.isLeader() + "\t" + raftServerImpl.isFollower());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
			System.out.println("INFOS:" + server.getId() + "\tState:" + server.getLifeCycleState() + "\tRPC:"
					+ server.getRpcType().name());
			
			Thread.sleep(2*1000);
		}

		// exit when any input entered
		Scanner scanner = new Scanner(System.in);
		scanner.nextLine();
		server.close();
	}
}
