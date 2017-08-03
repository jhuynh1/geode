/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.session.tests;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;

import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.cli.util.CommandStringBuilder;
import org.apache.geode.test.junit.categories.DistributedTest;

/**
 * Base class for test of session replication.
 *
 * This class contains all of the tests of session replication functionality. Subclasses of this
 * class configure different containers in order to run these tests against specific containers.
 */
@Category({DistributedTest.class})
public class TestBug {

  File serverGemfire = new File("/Users/danuta/Documents/gemfire-8.2");
  File serverGemfireModules = new File("/Users/danuta/Documents/gemfire-modules-8.2");

  File client0Gemfire = new File("/Users/danuta/Documents/gemfire-8.1.0.9");
  File client0GemfireModules = new File("/Users/danuta/Documents/gemfire-modules-8.1.0.2");

  File client1Gemfire = serverGemfire;
  File client1GemfireModules = serverGemfireModules;

  @Rule
  public transient TestName testName = new TestName();

  public transient Client client;
  public transient ContainerManager manager;

  private static String GFSH_LOCATION = "/Users/danuta/Documents/gemfire-8.2/bin/";

  protected void startServer(String name, String GFSHLocation, String classPath, int locatorPort)
      throws Exception {
    CommandStringBuilder command = new CommandStringBuilder(CliStrings.START_SERVER);

    command.addOption(CliStrings.START_SERVER__NAME, name);
    command.addOption(CliStrings.START_SERVER__SERVER_PORT, "0");
    command.addOption(CliStrings.START_SERVER__CLASSPATH, classPath);
    command.addOption(CliStrings.START_SERVER__LOCATORS, "localhost[" + locatorPort + "]");

    executeCommand(GFSHLocation + "gfsh " + command.toString());
  }

  protected void startLocator(String name, String GFSHLocation, String classPath, int port)
      throws Exception {
    CommandStringBuilder locStarter = new CommandStringBuilder(CliStrings.START_LOCATOR);

    locStarter.addOption(CliStrings.START_LOCATOR__MEMBER_NAME, "loc");
    locStarter.addOption(CliStrings.START_LOCATOR__CLASSPATH, classPath);
    locStarter.addOption(CliStrings.START_LOCATOR__PORT, Integer.toString(port));

    executeCommand(GFSHLocation + "gfsh " + locStarter.toString());
  }

  protected void executeCommand(String command) throws Exception {
    ProcessBuilder pb = new ProcessBuilder();
    Process pr = pb.command(command.split(" ")).start();
    pr.waitFor();
    InputStream is = pr.getInputStream();
    BufferedReader bis = new BufferedReader(new InputStreamReader(is));
    String line = bis.readLine();
    while (line != null) {
      System.out.println(line);
      line = bis.readLine();
    }
  }

  @Before
  public void setup() throws Exception {
    // Set gemfire property to stop GFSH error message
    System.setProperty("GEMFIRE", serverGemfire.getAbsolutePath());
    // Get available port for the locator
    int locatorPort = AvailablePortHelper.getRandomAvailableTCPPort();

    TomcatInstall install8 = new TomcatInstall(TomcatInstall.TomcatVersion.TOMCAT8,
        ContainerInstall.ConnectionType.CLIENT_SERVER,
        ContainerInstall.DEFAULT_INSTALL_DIR + "Tomcat8Server",
        "/Users/danuta/Documents/modded-gemfire-modules-8.2/lib",
        "/Users/danuta/Documents/gemfire-8.2/lib");

    String libDirJars = install8.getHome() + "/lib/*";
    String binDirJars = install8.getHome() + "/bin/*";



    CommandStringBuilder locStarter = new CommandStringBuilder(CliStrings.START_LOCATOR);
    System.out.println("TRYING TO START LOCATOR ON: " + locPort);
    locStarter.addOption(CliStrings.START_LOCATOR__MEMBER_NAME, "loc");
    locStarter.addOption(CliStrings.START_LOCATOR__CLASSPATH,
        binDirJars + File.pathSeparator + libDirJars);
    locStarter.addOption(CliStrings.START_LOCATOR__PORT, Integer.toString(locPort));

    executeCommand(GFSH_LOCATION + "gfsh " + locStarter.toString());

    install8.setDefaultLocator("localhost", locPort);



    client = new Client();
    manager = new ContainerManager();

    TomcatInstall install7 = new TomcatInstall(TomcatInstall.TomcatVersion.TOMCAT7,
        ContainerInstall.ConnectionType.CLIENT_SERVER,
        ContainerInstall.DEFAULT_INSTALL_DIR + "Tomcat7Server",
        "/Users/danuta/Documents/gemfire-modules-8.1.0.2/lib",
        "/Users/danuta/Documents/gemfire-8.1.0.9/lib");
    install7.setDefaultLocator("localhost", locPort);

    manager.setTestName(testName.getMethodName());
    manager.addContainer(install7);
  }

  /**
   * Stops all containers that were previously started and cleans up their configurations
   */
  @After
  public void stop() throws Exception {
    manager.stopAllActiveContainers();
    manager.cleanUp();

    CommandStringBuilder locStop = new CommandStringBuilder(CliStrings.STOP_LOCATOR);
    executeCommand(GFSH_LOCATION + "gfsh " + locStop.toString());

    CommandStringBuilder command = new CommandStringBuilder(CliStrings.STOP_SERVER);
    executeCommand(GFSH_LOCATION + "gfsh " + command.toString());
  }

  /**
   * Test that when multiple containers are using session replication, all of the containers will
   * use the same session cookie for the same client.
   */
  @Test
  public void checkContainer1GetsPutFromContainer0() throws IOException, URISyntaxException {
    // This has to happen at the start of every test
    manager.startAllInactiveContainers();

    String key = "value_testSessionPersists";
    String value = "Foo";

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    Client.Response resp = client.set(key, value);
    String cookie = resp.getSessionCookie();

    for (int i = 0; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals("Sessions are not replicating properly", cookie, resp.getSessionCookie());
      assertEquals("Session data is not replicating properly", value, resp.getResponse());
    }
  }

  @Test
  public void checkContainer0GetsPutFromContainer1() throws IOException, URISyntaxException {
    // This has to happen at the start of every test
    manager.startAllInactiveContainers();

    String key = "value_testSessionPersists";
    String value = "Foo";

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    Client.Response resp = client.set(key, value);
    String cookie = resp.getSessionCookie();

    for (int i = 0; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals("Sessions are not replicating properly", cookie, resp.getSessionCookie());
      assertEquals("Session data is not replicating properly", value, resp.getResponse());
    }
  }
}
