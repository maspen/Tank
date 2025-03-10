package com.intuit.tank.harness;

/*
 * #%L
 * Intuit Tank Agent (apiharness)
 * %%
 * Copyright (C) 2011 - 2015 Intuit Inc.
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import com.google.common.collect.ImmutableMap;
import com.intuit.tank.runner.TestPlanRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.intuit.tank.harness.data.HDTestPlan;
import com.intuit.tank.harness.logging.LogUtil;
import com.intuit.tank.logging.LogEventType;
import com.intuit.tank.vm.api.enumerated.AgentCommand;
import org.apache.logging.log4j.message.ObjectMessage;

public class TestPlanStarter implements Runnable {

    private static final Logger LOG = LogManager.getLogger(TestPlanStarter.class);

    private final Object httpClient;
    private final HDTestPlan plan;
    private final int numThreads;
    private final String tankHttpClientClass;
    private final ThreadGroup threadGroup;
    private final AgentRunData agentRunData;
    private int threadsStarted = 0;
    private final long rampDelay;

    private boolean done = false;

    public TestPlanStarter(Object httpClient, HDTestPlan plan, int numThreads, String tankHttpClientClass, ThreadGroup threadGroup, AgentRunData agentRunData) {
        super();
        this.httpClient = httpClient;
        this.plan = plan;
        this.tankHttpClientClass = tankHttpClientClass;
        this.numThreads = (int) Math.max(1, Math.floor(numThreads * (plan.getUserPercentage() / 100D)));
        this.threadGroup = threadGroup;
        this.agentRunData = agentRunData;
        this.rampDelay = calcRampTime();
    }

    public void run() {
        // start initial users
        int numInitialUsers = agentRunData.getNumStartUsers();
        if (threadsStarted < numInitialUsers && threadsStarted < numThreads) {
            LOG.info(new ObjectMessage(ImmutableMap.of("Message", "Starting initial " + numInitialUsers + " users for plan "
                    + plan.getTestPlanName() + "...")));
            while (threadsStarted < numInitialUsers && threadsStarted < numThreads) {
                createThread(httpClient, threadsStarted);
            }
        }

        // start rest of users sleeping between each interval
        LOG.info(new ObjectMessage(ImmutableMap.of("Message", "Starting ramp of additional " + (numThreads - threadsStarted)
                + " users for plan " + plan.getTestPlanName() + "...")));
        while (!done) {
            if ((threadsStarted - numInitialUsers) % agentRunData.getUserInterval() == 0) {
                try {
                    Thread.sleep(rampDelay);
                } catch (InterruptedException e) {
                    LOG.error(LogUtil.getLogMessage("Error trying to wait for ramp", LogEventType.System), e);
                }
            }
            // Loop while in pause or pause_ramp state
            while (APITestHarness.getInstance().getCmd() == AgentCommand.pause_ramp
            		|| APITestHarness.getInstance().getCmd() == AgentCommand.pause) {
                if (APITestHarness.getInstance().hasMetSimulationTime()) {
                    APITestHarness.getInstance().setCommand(AgentCommand.stop);
                    break;
                } else {
                    try {
                        Thread.sleep(APITestHarness.POLL_INTERVAL);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }
            if ( APITestHarness.getInstance().getCmd() == AgentCommand.stop
            		|| APITestHarness.getInstance().getCmd() == AgentCommand.kill
                    || APITestHarness.getInstance().hasMetSimulationTime()
                    || APITestHarness.getInstance().isDebug()
                    || (agentRunData.getSimulationTimeMillis() == 0 //Run Until: Loops Completed
                        && System.currentTimeMillis() - APITestHarness.getInstance().getStartTime() > agentRunData.getRampTimeMillis())) {
                done = true;
                break;
            }

            if (this.threadsStarted < numThreads) {
                createThread(httpClient, threadsStarted);
            }
        }
        done = true;
    }

    public int getThreadsStarted() {
        return threadsStarted;
    }

    public HDTestPlan getPlan() {
        return plan;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public boolean isDone() {
        return done;
    }

    private long calcRampTime() {
        int ramp = (numThreads - agentRunData.getNumStartUsers());
        if (ramp > 0) {
            return (agentRunData.getRampTimeMillis() *
                    agentRunData.getUserInterval())
                    / ramp;
        } else if (agentRunData.getRampTimeMillis() > 0) {
            LOG.info(LogUtil.getLogMessage("No Ramp - " + rampDelay, LogEventType.System));
        }
        return 1; //Return minimum wait time 1 millisecond
    }

    private void createThread(Object httpClient, int threadNumber) {
        TestPlanRunner session = new TestPlanRunner(httpClient, plan, threadNumber, tankHttpClientClass);
        Thread thread = new Thread(threadGroup, session, "AGENT");
        thread.setDaemon(true);// system won't shut down normally until all user threads stop
        session.setUniqueName(threadGroup.getName() + "-" + thread.getId());
        thread.start();
        APITestHarness.getInstance().threadStarted(thread);
        threadsStarted++;
    }
}
