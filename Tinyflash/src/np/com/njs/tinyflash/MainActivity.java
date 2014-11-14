/**
 * MainActivity.java
 * @author Nj Subedi (http://njs.com.np)
 * 2014 Nov 10
 */
package np.com.njs.tinyflash;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings.SettingNotFoundException;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

public class MainActivity extends Activity {

	private boolean flashIsOn = false;
	private boolean screenIsOn = false;
	private boolean hasFlash = false;
	private PackageManager pm;
	private Camera cam;
	private Parameters param;
	WakeLock wakeLock;
	PowerManager powerManager;
	int currentBrightness = -100;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// my way of putting everything in a method.
		init();

		// turn both screen and flash on (both are off/'false' at first)
		toggleScreen(getCurrentFocus());
		toggleFlash(getCurrentFocus());

		// keep screen on, obviously.
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	/**
	 * Initialize all variables appropriately.
	 */
	private void init() {
		if (pm == null)
			pm = getPackageManager();
		if (cam == null)
			cam = Camera.open();
		if (param == null)
			param = cam.getParameters();
		if (powerManager == null)
			powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		if (wakeLock == null)
			wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					"tinyflash");
	}

	/**
	 * Fired from the button [id: btn_g]'s "onClick" attribute.
	 * 
	 * Toggles the screen lighting. If screen is lit, turns it off by setting
	 * the background color to dark gray and changes the button text
	 * appropiately. If screen is off, lits it up by setting the background
	 * color white.
	 * 
	 * @param v
	 *            Any dummy view
	 */
	public void toggleScreen(View v) {
		if (!screenIsOn) {
			makeFullBrightness();
			((RelativeLayout) findViewById(R.id.bg))
					.setBackgroundColor(Color.WHITE);
			((Button) findViewById(R.id.btn_g)).setText("SCREEN ON");
			screenIsOn = true;
		} else {
			restoreBrightness();
			((RelativeLayout) findViewById(R.id.bg))
					.setBackgroundColor(Color.BLACK);
			((Button) findViewById(R.id.btn_g)).setText("SCREEN OFF");
			screenIsOn = false;
		}
	}

	/**
	 * Fired from the button [id: btn_flash]'s "onClick" attribute.
	 * 
	 * Toggles the flash light of the camera. If flash is turned on, turns it
	 * off by setting the FLASH_MODE_OFF to camera's parameters and changes the
	 * button text appropiately. If flash is off, turns it on by setting the
	 * camera's parameter FLASH_MODE_TORCH
	 * 
	 * Also hides/shows the "Flash On / Flash Off" button based on whether the
	 * flash light is supported.
	 * 
	 * @param v
	 *            Any dummy view
	 */
	public void toggleFlash(View v) {
		if (hasFlash()) {
			try {
				((Button) findViewById(R.id.btn_flash))
						.setVisibility(View.VISIBLE);
				if (!flashIsOn) {
					/*
					 * We need several device-specific hacks here to work on as
					 * many devices as possible.
					 */

					param.setFlashMode(Parameters.FLASH_MODE_TORCH);
					cam.setParameters(param);

					// 1. Some devices don't support torch mode
					if (param.getFlashMode() != Parameters.FLASH_MODE_TORCH) {
						param.setFlashMode(Parameters.FLASH_MODE_ON);
					}

					// 2. Some devices need camera preview
					cam.startPreview();

					// 3. And some needed autofocus mode!
					cam.autoFocus(new AutoFocusCallback() {
						public void onAutoFocus(boolean success, Camera camera) {
						}
					});

					/*
					 * Finally we are here.
					 */

					((Button) findViewById(R.id.btn_flash)).setText("FLASH ON");
					flashIsOn = true;
				} else {
					param.setFlashMode(Parameters.FLASH_MODE_OFF);
					cam.setParameters(param);
					cam.stopPreview();
					((Button) findViewById(R.id.btn_flash))
							.setText("FLASH OFF");
					flashIsOn = false;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			((Button) findViewById(R.id.btn_flash)).setVisibility(View.GONE);
		}
	}

	/**
	 * Check whether flash light is supported. First of all checks whether there
	 * is camera built in on the device. If yes, tries to get the flash mode
	 * from camera parameters. If flash mode is not found, we suppose there is
	 * no flash light support.
	 * 
	 * @return Boolean true if flash is supported, false otherwise
	 */
	private boolean hasFlash() {
		if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			try {
				if (param.getFlashMode() != null) {
					hasFlash = true;
				}
			} catch (Exception e) {
			}
		}
		return hasFlash;
	}

	/**
	 * Restores the device's original brightness (the value before flashlight
	 * was turned on). The value is saved in `currentBrightness` variable.
	 */
	private void restoreBrightness() {
		WindowManager.LayoutParams layout = getWindow().getAttributes();
		layout.screenBrightness = currentBrightness;
		getWindow().setAttributes(layout);
	}

	/**
	 * Saves the current brightness (if not already saved) in a variable to
	 * restore later. Then sets the full screen brightness.
	 */
	private void makeFullBrightness() {
		/*
		 * Only save the device's original brightness once. -100 is the value we
		 * initialized the variable with. It would be only between 0 - 1 if set
		 * already. -100 means it has never been saved.
		 */
		if (currentBrightness == -100) {
			try {
				currentBrightness = android.provider.Settings.System.getInt(
						getContentResolver(),
						android.provider.Settings.System.SCREEN_BRIGHTNESS);
			} catch (SettingNotFoundException e) {
				e.printStackTrace();
			}
		}
		/*
		 * Set brightness to full ( 1L ). Acceptable values: 0 - 1.
		 */
		WindowManager.LayoutParams layout = getWindow().getAttributes();
		layout.screenBrightness = 1F;
		getWindow().setAttributes(layout);
	}

	@Override
	protected void onPause() {
		/*
		 * When flash light is turned off, we don't need to keep device from
		 * sleeping.
		 */
		if (wakeLock.isHeld()) {
			wakeLock.release();
		}

		/*
		 * After the flash is closed, release the camera's resource.
		 */
		if (cam != null)
			cam.release();
		super.onPause();
	}

	@Override
	protected void onResume() {
		/*
		 * In case resumed after home-button press, let's initialize.
		 */
		init();
		/*
		 * When flash is turned on, we prevent the device from dimming/sleeping.
		 */
		if (!wakeLock.isHeld()) {
			wakeLock.acquire();
		}
		super.onResume();
	}

}
