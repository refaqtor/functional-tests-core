package functional.tests.core.appium;

import functional.tests.core.enums.DeviceType;
import functional.tests.core.enums.PlatformType;
import functional.tests.core.settings.Settings;
import functional.tests.core.utils.FileSystem;
import io.appium.java_client.remote.AndroidMobileCapabilityType;
import io.appium.java_client.remote.IOSMobileCapabilityType;
import io.appium.java_client.remote.MobileCapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;

/**
 * Appium Desired Capabilities.
 */
public class Capabilities {

    /**
     * Load common capabilities.
     *
     * @param settings Current settings.
     * @return Common capabilities.
     */
    public DesiredCapabilities loadDesiredCapabilities(Settings settings) {
        DesiredCapabilities capabilities = new DesiredCapabilities();
        capabilities.setCapability(MobileCapabilityType.AUTOMATION_NAME, settings.automationName);
        capabilities.setCapability(MobileCapabilityType.PLATFORM_NAME, settings.platform);
        capabilities.setCapability(MobileCapabilityType.PLATFORM_VERSION, settings.platformVersion);
        capabilities.setCapability(MobileCapabilityType.DEVICE_NAME, settings.deviceName);

        // Increase the NEW_COMMAND_TIMEOUT capability
        // to avoid ending the session during debug.
        int newCommandTimeout = settings.deviceBootTimeout;
        if (settings.debug) {
            newCommandTimeout *= 3;
        }
        capabilities.setCapability(MobileCapabilityType.NEW_COMMAND_TIMEOUT, newCommandTimeout);

        // --no-reset
        // Do not reset app state between sessions
        // IOS: do not delete app plist files;
        // Android: do not uninstall app before new session)
        capabilities.setCapability(MobileCapabilityType.NO_RESET, true);

        // --full-reset
        // (iOS) Delete the entire simulator folder.
        // (Android) Reset app state by uninstalling app instead of clearing app data.
        // On Android, this will also remove the app after the session is complete.
        capabilities.setCapability(MobileCapabilityType.FULL_RESET, false);

        if (settings.orientation != null) {
            capabilities.setCapability(MobileCapabilityType.ORIENTATION, settings.orientation);
        }

        if (settings.isRealDevice == true) {
            capabilities.setCapability(MobileCapabilityType.UDID, settings.deviceId);
        }

        capabilities.setCapability(MobileCapabilityType.APP,
                settings.BASE_TEST_APP_DIR + File.separator + settings.testAppName);

        return capabilities;
    }

    /**
     * Load Android specific capabilities.
     *
     * @param settings Current settings.
     * @return Android specific capabilities.
     */
    public DesiredCapabilities loadAndroidCapabilities(Settings settings) {
        DesiredCapabilities capabilities = this.loadDesiredCapabilities(settings);

        // Android
        if (settings.platform == PlatformType.Andorid) {
            capabilities.setCapability(AndroidMobileCapabilityType.APP_PACKAGE, settings.packageId);
            capabilities.setCapability(AndroidMobileCapabilityType.APP_ACTIVITY, settings.android.defaultActivity);
            capabilities.setCapability(AndroidMobileCapabilityType.APP_WAIT_ACTIVITY, settings.android.appWaitActivity);
            capabilities.setCapability(AndroidMobileCapabilityType.APP_WAIT_PACKAGE, settings.android.appWaitPackage);
            capabilities.setCapability(AndroidMobileCapabilityType.NO_SIGN, true);
            if (settings.isRealDevice == false && !settings.debug) {
                // This line of code restart always the emulator
                // capabilities.setCapability(AndroidMobileCapabilityType.AVD, settings.deviceName);
                // capabilities.setCapability(AndroidMobileCapabilityType.AVD_ARGS, settings.android.emulatorOptions);
            }
        }

        return capabilities;
    }

    /**
     * Load iOS specific capabilities.
     *
     * @param settings Current settings.
     * @return iOS specific capabilities.
     */
    public DesiredCapabilities loadIOSCapabilities(Settings settings) {
        DesiredCapabilities capabilities = this.loadDesiredCapabilities(settings);

        capabilities.setCapability(IOSMobileCapabilityType.AUTO_ACCEPT_ALERTS, settings.ios.acceptAlerts);
        capabilities.setCapability(IOSMobileCapabilityType.LAUNCH_TIMEOUT, settings.deviceBootTimeout * 1000); // In ms.
        capabilities.setCapability(IOSMobileCapabilityType.SCREENSHOT_WAIT_TIMEOUT, settings.defaultTimeout);
        capabilities.setCapability(IOSMobileCapabilityType.SHOW_IOS_LOG, true);
        // capabilities.setCapability(IOSMobileCapabilityType.NATIVE_INSTRUMENTS_LIB, true);
        // capabilities.setCapability(IOSMobileCapabilityType.LOCATION_SERVICES_ENABLED, true);
        // capabilities.setCapability(IOSMobileCapabilityType.SEND_KEY_STRATEGY, "setValue");
        // capabilities.setCapability(IOSMobileCapabilityType.WAIT_FOR_APP_SCRIPT, true);
        // capabilities.setCapability(IOSMobileCapabilityType.APP_NAME, settings.testAppName);

        if (settings.deviceType == DeviceType.Simulator) {
            // This is required by the safe simulator restart.
            capabilities.setCapability(MobileCapabilityType.NO_RESET, true);
        }

        // It looks we need it for XCTest (iOS 10+ automation)
        if (settings.platformVersion >= 10) {
            capabilities.setCapability(MobileCapabilityType.UDID, settings.deviceId);
        }

        if (settings.isRealDevice) {
            if (FileSystem.exist(settings.ios.xCode8ConfigFile)) {
                capabilities.setCapability("xcodeConfigFile", settings.ios.xCode8ConfigFile);
            }

            capabilities.setCapability(MobileCapabilityType.FULL_RESET, true);
        }

        return capabilities;
    }
}
