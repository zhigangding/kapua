/*******************************************************************************
 * Copyright (c) 2017 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/
package org.eclipse.kapua.service.device.steps;

import java.math.BigInteger;
import java.util.List;

import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.commons.util.xml.JAXBContextProvider;
import org.eclipse.kapua.commons.util.xml.XmlUtil;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.service.DBHelper;
import org.eclipse.kapua.service.MQBrokerRunner;
import org.eclipse.kapua.service.StepData;
import org.eclipse.kapua.service.TestJAXBContextProvider;
import org.eclipse.kapua.service.device.management.bundle.DeviceBundle;
import org.eclipse.kapua.service.device.management.bundle.DeviceBundleManagementService;
import org.eclipse.kapua.service.device.management.bundle.DeviceBundles;
import org.eclipse.kapua.service.device.management.command.DeviceCommandFactory;
import org.eclipse.kapua.service.device.management.command.DeviceCommandInput;
import org.eclipse.kapua.service.device.management.command.DeviceCommandManagementService;
import org.eclipse.kapua.service.device.management.command.DeviceCommandOutput;
import org.eclipse.kapua.service.device.management.configuration.DeviceComponentConfiguration;
import org.eclipse.kapua.service.device.management.configuration.DeviceConfiguration;
import org.eclipse.kapua.service.device.management.configuration.DeviceConfigurationManagementService;
import org.eclipse.kapua.service.device.management.packages.DevicePackageManagementService;
import org.eclipse.kapua.service.device.management.packages.model.DevicePackage;
import org.eclipse.kapua.service.device.management.packages.model.DevicePackages;
import org.eclipse.kapua.service.device.management.snapshot.DeviceSnapshotManagementService;
import org.eclipse.kapua.service.device.registry.Device;
import org.eclipse.kapua.service.device.registry.DeviceRegistryService;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

/**
 * Steps used in integration scenarios with running MQTT broker and process of
 * registering mocked Kura device registering with Kapua and issuing basic administrative
 * commands on Mocked Kura.
 */
@ScenarioScoped
public class BrokerSteps extends Assert {

    /**
     * Embedded broker configuration file from classpath resources.
     */
    public static final String ACTIVEMQ_XML = "xbean:activemq.xml";

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(BrokerSteps.class);

    /**
     * Max timeout at embedded broker startup.
     */
    private static final long BROKER_STARTUP_WAIT_MILIS = 10_000;

    /**
     * Timeout for complete broker with channels and rules to start.
     * Might vary depending on system.
     */
    private static final long BROKER_RULES_WAIT_MILIS = 60_000;

    /**
     * Device birth topic.
     */
    private static final String MQTT_BIRTH = "$EDC/kapua-sys/rpione3/MQTT/BIRTH";

    /**
     * Device death topic.
     */
    private static final String MQTT_DC = "$EDC/kapua-sys/rpione3/MQTT/DC";

    /**
     * Access to device management service.
     */
    private DevicePackageManagementService devicePackageManagementService;

    /**
     * Device registry service for managing devices in DB.
     */
    private DeviceRegistryService deviceRegistryService;

    /**
     * Configuration service for kura devices.
     */
    private DeviceConfigurationManagementService deviceConfiguratiomManagementService;

    /**
     * Kura snapshot management service.
     */
    private DeviceSnapshotManagementService deviceSnapshotManagementService;

    /**
     * Kura bundle management service.
     */
    private DeviceBundleManagementService deviceBundleManagementService;

    /**
     * Service for issuing commnads on Kura device.
     */
    private DeviceCommandManagementService deviceCommandManagementService;

    /**
     * Factory for creating commands sent to Kura.
     */
    private DeviceCommandFactory deviceCommandFactory;

    /**
     * Client simulating Kura device
     */
    private KuraDevice kuraDevice;

    /**
     * Scenario scoped step data.
     */
    private StepData stepData;

    /**
     * Single point to database access.
     */
    private DBHelper dbHelper;

    private MQBrokerRunner broker;

    @Inject
    public BrokerSteps(StepData stepData, DBHelper dbHelper) {

        this.stepData = stepData;
        this.dbHelper = dbHelper;
    }

    @Before
    public void beforeScenario(Scenario scenario) throws Exception {

        KapuaLocator locator = KapuaLocator.getInstance();
        devicePackageManagementService = locator.getService(DevicePackageManagementService.class);
        deviceRegistryService = locator.getService(DeviceRegistryService.class);
        deviceConfiguratiomManagementService = locator.getService(DeviceConfigurationManagementService.class);
        deviceSnapshotManagementService = locator.getService(DeviceSnapshotManagementService.class);
        deviceBundleManagementService = locator.getService(DeviceBundleManagementService.class);
        deviceCommandManagementService = locator.getService(DeviceCommandManagementService.class);
        deviceCommandFactory = locator.getFactory(DeviceCommandFactory.class);

        /*
         * Initialize broker from MQ configuration file ad start it.
         * After issuing start wait a moment to start completely.
         */
        logger.info("******* Broker will start ********");
        broker = new MQBrokerRunner(BROKER_STARTUP_WAIT_MILIS, ACTIVEMQ_XML);
        new Thread(broker).start();
        // FIXME Issue with broker startup, not listening on topics and missing client connect messages.
        // This could be moved to Gherkin as first step.
        logger.info("******* Wait for Broker ********");
        Thread.sleep(BROKER_RULES_WAIT_MILIS);
        logger.info("******* Wait Broker Finished ********");

        kuraDevice = new KuraDevice();
        kuraDevice.mqttClientConnect();

        JAXBContextProvider consoleProvider = new TestJAXBContextProvider();
        XmlUtil.setContextProvider(consoleProvider);
    }

    @After
    public void afterScenario() throws Exception {

        kuraDevice.mqttClientDisconnect();
        broker.stopBroker();

        dbHelper.deleteAll();
        KapuaSecurityUtils.clearSession();

        this.stepData = null;
    }

    @When("^Device birth message is sent$")
    public void deviceBirthMessage() throws Exception {

        kuraDevice.sendMessageFromFile(MQTT_BIRTH, 0, false, "src/test/resources/mqtt/rpione3_MQTT_BIRTH.mqtt");
    }

    @When("^Device is connected$")
    public void deviceConnected() throws Exception {

        deviceBirthMessage();
    }

    @When("^Device death message is sent$")
    public void deviceDeathMessage() throws Exception {

        kuraDevice.sendMessageFromFile(MQTT_DC, 0, false, "src/test/resources/mqtt/rpione3_MQTT_DC.mqtt");
    }

    @When("^Device is disconnected$")
    public void deviceDisconnected() throws Exception {

        deviceDeathMessage();
    }

    @When("^Packages are requested$")
    public void requestPackages() throws Exception {

        Device device = deviceRegistryService.findByClientId(new KapuaEid(BigInteger.valueOf(1l)), "rpione3");
        if (device != null) {
            DevicePackages deploymentPackages = devicePackageManagementService.getInstalled(device.getScopeId(),
                    device.getId(), null);
            List<DevicePackage> packages = deploymentPackages.getPackages();
            stepData.put("packages", packages);
        }
    }

    @Then("^Packages are received$")
    public void packagesReceived() {

        List<DevicePackage> packages = (List<DevicePackage>) stepData.get("packages");
        if (packages != null) {
            assertEquals(1, packages.size());
        }
    }

    @When("^Bundles are requested$")
    public void requestBundles() throws Exception {

        Device device = deviceRegistryService.findByClientId(new KapuaEid(BigInteger.valueOf(1l)), "rpione3");
        DeviceBundles deviceBundles = deviceBundleManagementService.get(device.getScopeId(), device.getId(), null);
        List<DeviceBundle> bundles = deviceBundles.getBundles();
        stepData.put("bundles", bundles);
    }

    @Then("^Bundles are received$")
    public void bundlesReceived() {

        List<DeviceBundle> bundles = (List<DeviceBundle>) stepData.get("bundles");
        assertEquals(80, bundles.size());
    }

    @When("^Configuration is requested$")
    public void requestConfiguration() throws Exception {

        Device device = deviceRegistryService.findByClientId(new KapuaEid(BigInteger.valueOf(1l)), "rpione3");
        DeviceConfiguration deviceConfiguration = deviceConfiguratiomManagementService.get(device.getScopeId(),
                device.getId(), null, null, null);
        List<DeviceComponentConfiguration> configurations = deviceConfiguration.getComponentConfigurations();
        stepData.put("configurations", configurations);
    }

    @Then("^Configuration is received$")
    public void configurationReceived() {

        List<DeviceComponentConfiguration> configurations = (List<DeviceComponentConfiguration>) stepData.get("configurations");
        assertEquals(11, configurations.size());
    }

    @When("^Command (.*) is executed$")
    public void executeCommand(String command) throws Exception {

        Device device = deviceRegistryService.findByClientId(new KapuaEid(BigInteger.valueOf(1l)), "rpione3");
        DeviceCommandInput commandInput = deviceCommandFactory.newCommandInput();
        commandInput.setCommand(command);
        commandInput.setRunAsynch(false);
        commandInput.setTimeout(0);
        DeviceCommandOutput deviceCommandOutput = deviceCommandManagementService.exec(device.getScopeId(),
                device.getId(), commandInput, null);
        Integer commandExitCode = deviceCommandOutput.getExitCode();
        stepData.put("commandExitCode", commandExitCode);
    }

    @Then("^Exit code (\\d+) is received$")
    public void configurationReceived(int expectedExitCode) {

        Integer commandExitCode = (Integer) stepData.get("commandExitCode");
        assertEquals(expectedExitCode, commandExitCode.intValue());
    }

    @When("^I wait (\\d+) second")
    public void iWait(int waitInSeconds) throws Exception {
        logger.info("***** Start wait step ******");
        Thread.sleep(1000L * waitInSeconds);
        logger.info("***** End wait step ******");
    }

}
