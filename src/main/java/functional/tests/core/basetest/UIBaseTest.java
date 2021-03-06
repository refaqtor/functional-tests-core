package functional.tests.core.basetest;

import functional.tests.core.app.App;
import functional.tests.core.appium.Client;
import functional.tests.core.appium.Server;
import functional.tests.core.device.Device;
import functional.tests.core.element.UIElement;
import functional.tests.core.enums.PlatformType;
import functional.tests.core.find.Find;
import functional.tests.core.find.Wait;
import functional.tests.core.gestures.Gestures;
import functional.tests.core.image.ImageUtils;
import functional.tests.core.image.ImageVerification;
import functional.tests.core.image.Sikuli;
import functional.tests.core.log.Log;
import functional.tests.core.log.LoggerBase;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO(svetli): Add docs.
 */
public abstract class UIBaseTest extends BaseTest {

    private static final LoggerBase LOGGER = LoggerBase.getLogger("UIBaseTest");
    private static int maxPixelTolerance = Integer.MAX_VALUE;

    private int imageCounter = 1;
    private int defaultWaitTime = 1000;
    private double minPercentTolerant = 0.001;
    private Map<String, Boolean> imagesResults;

    private boolean firstTest;
    private Sikuli sikuliImageProcessing;
    private Server server;

    protected Log log;
    protected ImageVerification imageVerification;

    public Client client;
    public Device device;
    public Find find;
    public Gestures gestures;
    public App app;
    public Wait wait;
    public ImageUtils imageUtils;

    /**
     *
     * Init UI Tests setup.
     */
    public UIBaseTest() {
        this.initUITestHelpers();
        this.imageVerification = new ImageVerification();
    }

    /**
     * TODO(): Add docs.
     *
     * @return
     */
    protected Sikuli getSikuliImagePorcessing() {
        return this.sikuliImageProcessing;
    }

    /**
     * Executed before suite with UI Tests.
     * Actions:
     * 1. Start Appium server (retry once on failure).
     * 2. Start emulator/simulator or ensure device is available.
     * 3. Start appium client (and deploy app under test).
     * 4. Verify app under test is running and track loading time.     *
     *
     * @throws Exception
     */
    @BeforeSuite(alwaysRun = true)
    public void beforeSuiteUIBaseTest() throws Exception {

        // TODO(dtopuzov): For iOS it will be nice to retry starting session if this.testSetupManager.startDevice(); fails.
        // And on second call of this.testSetupManager.initServer() we should force debug log level.
        // It will be very usefull to get logs on failure.

        // Start server (and retry on failure
        if (!this.testSetupManager.initServer()) {
            this.testSetupManager.restartServer();
        }

        // Start device and init client (Init Appium Client is done on Device.start())
        this.testSetupManager.startDevice();

        // Mark this test as first in suite
        this.firstTest = true;
    }

    /**
     * Executed before each UI Test method.
     * Actions:
     * 1. [If restartApp=true] Restart app under test.
     * 2. [Only if previous test failed] Restart appium session and app under test.
     *
     * @param method
     * @throws Exception
     */
    @BeforeMethod(alwaysRun = true)
    public void beforeMethodUIBaseTest(Method method) throws Exception {

        // Perform actions when previous test passed.
        if (this.context.lastTestResult == ITestResult.SUCCESS && this.settings.restartApp && !this.firstTest) {
            this.context.app.restart();
        }

        // Perform set of actions on test fail.
        if (this.context.lastTestResult == ITestResult.FAILURE && this.context.shouldRestartAppOnFailure) {
            try {
                // Restart app under test
                this.context.app.restart();
            } catch (Exception e) {
                // Restart might fail if server or client are dead
                this.testSetupManager.restartSession();
            }

            // Reset navigation state manager
            if (this.context.navigationManager != null && this.context.shouldRestartAppOnFailure) {
                this.context.navigationManager.resetNavigationToLastOpenedPage();
            } else {
                LOGGER.error("TestStateManager is: " + this.context.navigationManager + " in beforeMethodUIBaseTest!");
            }
        }

        // First test is already started, so set this.firstTest = false;
        this.firstTest = false;
        // Set value to true in case of valid failures
        this.context.shouldRestartAppOnFailure = true;

        this.imagesResults = new HashMap<String, Boolean>();
        this.imageCounter = 1;
    }

    /**
     * Executed after each UI Test.
     * Actions:
     * 1. Get and log performance info for app under test (and check if specified in settings).
     * 2. Log test results (on failure include logs and screenshots, see this.testSetupManager.logTestResult).
     *
     * @param result
     * @throws IOException
     */
    @AfterMethod(alwaysRun = true)
    public void afterMethodUIBaseTest(ITestResult result) throws IOException {

        // Log memory usage and assert it is less than memoryMaxUsageLimit (if memoryMaxUsageLimit is set)
        this.checkMemoryPerformance(result);

        this.context.lastTestResult = result.getStatus();
        this.testSetupManager.logTestResult(this.context.lastTestResult, this.context.getTestName());
    }

    /**
     * Navigate to appropriate page.
     */
    @AfterClass(alwaysRun = true)
    public void afterClassUIBaseTest() {
        if (this.context.navigationManager != null) {
            this.context.navigationManager.navigateToHomePage();
            this.context.navigationManager = null;
        }
    }

    /**
     * Execited after all tests complete.
     * Actions:
     * 1. Write perf info to file.
     * 2. Stop Appium Client and Server
     * 3. [Only if reuseDevices==false] Stop emulator/simulator.
     *
     * @throws Exception
     */
    @AfterSuite(alwaysRun = true)
    public void afterSuiteUIBaseTest() throws Exception {
        this.testContextSetupManager.device.logPerfInfo();
        this.testSetupManager.fullStop();
    }

    /**
     * Get memory usage and log it.
     * Assert memory usage is less than memoryMaxUsageLimit (if memoryMaxUsageLimit is specified).
     *
     * @param result
     */
    protected void checkMemoryPerformance(ITestResult result) {
        if (this.settings.platform == PlatformType.Andorid) {
            int usedMemory = this.device.getMemUsage(this.settings.packageId);
            if (usedMemory > -1) {
                LOGGER.info("Used memory: " + usedMemory);

                int currentMaxMem = this.context.device.getIDevice().android().getMaxUsedMemory();
                if (currentMaxMem < usedMemory) {
                    currentMaxMem = usedMemory;
                    this.context.device.getIDevice().android().setMaxUsedMemory(currentMaxMem);
                    LOGGER.debug("Maximum used memory: " + currentMaxMem);
                }

                if (this.settings.android.memoryMaxUsageLimit > 0) {
                    LOGGER.info("Expected max memory usage: " + this.settings.android.memoryMaxUsageLimit);
                    if (this.settings.android.memoryMaxUsageLimit < usedMemory) {
                        LOGGER.error("=== Memory leak appears after test " + result.getName() + " ====");
                        Assert.assertTrue(false, "Used memory of " + usedMemory + " is more than expected " + this.settings.android.memoryMaxUsageLimit + " !!!");
                        result.setStatus(ITestResult.FAILURE);
                    }
                }
            } else {
                LOGGER.error("Failed to get memory usage stats.");
            }
        } else {
            this.log.debug("Check performance not implemented for iOS.");
        }
    }

    /**
     * TODO(): Add docs.
     *
     * @param element
     * @param timeOut
     * @throws Exception
     */
    public void compareElements(UIElement element, int timeOut) throws Exception {
        this.compareElements(element, timeOut, this.defaultWaitTime, Integer.MAX_VALUE, this.minPercentTolerant);
    }

    /**
     * TODO(): Add docs.
     *
     * @param element
     * @param timeOut
     * @param percentTolerance
     * @throws Exception
     */
    public void compareElements(UIElement element, int timeOut, double percentTolerance) throws Exception {
        this.compareElements(element, timeOut, this.defaultWaitTime, Integer.MAX_VALUE, percentTolerance);
    }

    /**
     * TODO(): Add docs.
     *
     * @param imageName
     * @param element
     * @param timeOut
     * @param percentTolerance
     * @throws Exception
     */
    public void compareElements(String imageName, UIElement element, int timeOut, double percentTolerance) throws Exception {
        this.compareElements(imageName, element, timeOut, this.defaultWaitTime, Integer.MAX_VALUE, percentTolerance);
    }

    /**
     * TODO(): Add docs.
     *
     * @param element
     * @param timeOut
     * @param waitTime
     * @throws Exception
     */
    public void compareElements(UIElement element, int timeOut, int waitTime) throws Exception {
        this.compareElements(element, timeOut, waitTime, Integer.MAX_VALUE, this.minPercentTolerant);
    }

    /**
     * Compare the current screen.
     *
     * @throws Exception
     */
    public void compareScreens() throws Exception {
        this.compareScreens(1, this.defaultWaitTime, maxPixelTolerance, 0);
    }

    /**
     * Assert the current screen.
     *
     * @throws Exception
     */
    public void assertScreen() throws Exception {
        this.compareScreens(1, this.defaultWaitTime, maxPixelTolerance, 0);
        this.assertImagesResults();
    }

    /**
     * Compare the current screen.
     *
     * @param name of the image
     * @throws Exception
     */
    public void compareScreens(String name) throws Exception {
        this.compareScreens(name, 1, this.defaultWaitTime, maxPixelTolerance, 0);
    }

    /**
     * Assert the current screen.
     *
     * @param name of the image
     * @throws Exception
     */
    public void assertScreen(String name) throws Exception {
        this.compareScreens(name, 1, this.defaultWaitTime, maxPixelTolerance, 0);
        this.assertImagesResults();
    }

    /**
     * Compare the current screen.
     *
     * @param timeOut to wait for the image
     * @throws Exception
     */
    public void compareScreens(int timeOut) throws Exception {
        this.compareScreens(timeOut, this.defaultWaitTime, maxPixelTolerance, 0);
    }

    /**
     * Assert the current screen.
     *
     * @param timeOut to wait for the image
     * @throws Exception
     */
    public void assertScreen(int timeOut) throws Exception {
        this.compareScreens(timeOut, this.defaultWaitTime, maxPixelTolerance, 0);
        this.assertImagesResults();
    }

    /**
     * Compare the current screen.
     *
     * @param name    of the image
     * @param timeOut to wait for the image
     * @throws Exception
     */
    public void compareScreens(String name, int timeOut) throws Exception {
        this.compareScreens(name, timeOut, this.defaultWaitTime, maxPixelTolerance, 0);
    }

    /**
     * Assert the current screen.
     *
     * @param name    of the image
     * @param timeOut to wait for the image
     * @throws Exception
     */
    public void assertScreen(String name, int timeOut) throws Exception {
        this.compareScreens(name, timeOut, this.defaultWaitTime, maxPixelTolerance, 0);
        this.assertImagesResults();
    }

    /**
     * Compare the current screen.
     *
     * @param timeOut          to wait for the image
     * @param percentTolerance of the image
     * @return boolean
     * @throws Exception
     */
    public boolean compareScreens(int timeOut, double percentTolerance) throws Exception {
        return this.compareScreens(timeOut, this.defaultWaitTime, maxPixelTolerance, percentTolerance);
    }

    /**
     * Assert the current screen.
     *
     * @param timeOut          to wait for the image
     * @param percentTolerance of the image
     * @throws Exception
     */
    public void assertScreen(int timeOut, double percentTolerance) throws Exception {
        this.compareScreens(timeOut, this.defaultWaitTime, maxPixelTolerance, percentTolerance);
        this.assertImagesResults();
    }

    /**
     * Compare the current screen.
     *
     * @param timeOut          to wait for the image
     * @param wait             to sleep before asserting the image
     * @param percentTolerance of the image
     * @return boolean
     * @throws Exception
     */
    public boolean compareScreens(int timeOut, int wait, double percentTolerance) throws Exception {
        return this.compareScreens(timeOut, wait, maxPixelTolerance, percentTolerance);
    }

    /**
     * Assert the current screen.
     *
     * @param timeOut          to wait for the image
     * @param wait             to sleep before asserting the image
     * @param percentTolerance of the image
     * @throws Exception
     */
    public void assertScreen(int timeOut, int wait, double percentTolerance) throws Exception {
        this.compareScreens(timeOut, wait, maxPixelTolerance, percentTolerance);
        this.assertImagesResults();
    }

    /**
     * Compare the current screen.
     *
     * @param name             of the image
     * @param timeOut          to wait for the image
     * @param percentTolerance of the image
     * @return boolean
     * @throws Exception
     */
    public boolean compareScreens(String name, int timeOut, double percentTolerance) throws Exception {
        boolean result = this.compareScreens(name, timeOut, 0, maxPixelTolerance, percentTolerance);
        return result;
    }

    /**
     * Assert the current screen.
     *
     * @param name             of the image
     * @param timeOut          to wait for the image
     * @param percentTolerance of the image
     * @throws Exception
     */
    public void assertScreen(String name, int timeOut, double percentTolerance) throws Exception {
        this.compareScreens(name, timeOut, 0, maxPixelTolerance, percentTolerance);
        this.assertImagesResults();
    }

    /**
     * TODO(): Add docs.
     */
    public void clearImagesResults() {
        this.imagesResults.clear();
        this.imageCounter = 1;
    }

    /**
     * TODO(): Add docs.
     */
    public void assertImagesResults() {
        for (String imageName : this.imagesResults.keySet()) {
            Assert.assertTrue(this.imagesResults.get(imageName), String.format("The test failed - %s does not match the actual image.", imageName));
        }
    }

    /**
     * TODO(): Add docs.
     *
     * @param timeOut
     * @param waitTime
     * @param pixelTolerance
     * @param percentTolerance
     * @return
     * @throws Exception
     */
    private boolean compareScreens(int timeOut, int waitTime, int pixelTolerance, double percentTolerance) throws Exception {
        String testName = this.createImageName();
        boolean result = this.compareScreens(testName, timeOut, waitTime, pixelTolerance, percentTolerance);

        return result;
    }

    /**
     * TODO(): Add docs.
     *
     * @param name
     * @param timeOut
     * @param waitTime
     * @param pixelTolerance
     * @param percentTolerance
     * @return
     * @throws Exception
     */
    private boolean compareScreens(String name, int timeOut, int waitTime, int pixelTolerance, double percentTolerance) throws Exception {
        boolean result = this.imageVerification.compareScreens(name, timeOut, waitTime, pixelTolerance, percentTolerance);
        this.imagesResults.put(name, result);
        this.imageCounter++;

        return result;
    }

    /**
     * TODO(): Add docs.
     *
     * @param element
     * @param timeOut
     * @param waitTime
     * @param pixelTolerance
     * @param percentTolerance
     * @throws Exception
     */
    private void compareElements(UIElement element, int timeOut, int waitTime, int pixelTolerance, double percentTolerance) throws Exception {
        String testName = this.createImageName();
        this.compareElements(testName, element, timeOut, waitTime, pixelTolerance, percentTolerance);
    }

    /**
     * TODO(): Add docs.
     *
     * @param imageName
     * @param element
     * @param timeOut
     * @param waitTime
     * @param pixelTolerance
     * @param percentTolerance
     * @throws Exception
     */
    private void compareElements(String imageName, UIElement element, int timeOut, int waitTime, int pixelTolerance, double percentTolerance) throws Exception {
        boolean result = this.imageVerification.compareElements(element, imageName, timeOut, waitTime, pixelTolerance, percentTolerance);
        this.imagesResults.put(imageName, result);
        this.imageCounter++;
    }

    /**
     * TODO(): Add docs.
     *
     * @return
     */
    private String createImageName() {
        return this.imageCounter <= 1 ? this.context.getTestName() : this.context.getTestName() + "_" + this.imageCounter;
    }

    /**
     * TODO(): Add docs.
     */
    private void initUITestHelpers() {
        this.context = this.testContextSetupManager.initUITestSetup();
        this.server = this.context.server;
        this.log = this.context.log;
        this.locators = this.context.locators;
        this.app = this.context.app;
        this.find = this.context.find;
        this.gestures = this.context.gestures;
        this.wait = this.context.wait;
        this.sikuliImageProcessing = this.context.sikuliImageProcessing;
        this.device = this.context.getDevice();
        this.client = this.context.client;
        this.settings = this.context.settings;
        this.imageUtils = this.context.imageUtils;
    }
}
