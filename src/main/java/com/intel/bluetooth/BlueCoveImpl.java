/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2004 Intel Corporation
 *  Copyright (C) 2006-2007 Vlad Skarzhevskyy
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *  @version $Id$
 */
package com.intel.bluetooth;

import java.util.Hashtable;
import java.util.Vector;


/**
 * 
 * Singleton class used as holder for BluetoothStack. 
 * 
 * All you need to do is initialize BlueCoveImpl inside Privileged context.
 * <p>
 * If automatic Bluetooth Stack detection is not enough Java System property
 * "bluecove.stack" can be used to force desired Stack Initialization. Values
 * "widcomm", "bluesoleil" or "winsock". By default winsock is selected if
 * available.
 * <p>
 * Another property "bluecove.stack.first" is used optimize stack detection. If
 * -Dbluecove.stack.first=widcomm then widcomm (bluecove.dll) stack is loaded
 * first and if not available then BlueCove will switch to winsock. By default
 * intelbth.dll is loaded first.
 * <p>
 * If multiple stacks are detected they are selected in following order:
 * "winsock", "widcomm", "bluesoleil". Since BlueCove v2.0.1
 * "bluecove.stack.first" will alter the order of stack selection.
 * <p>
 * If System property is not an option (e.g. when running in Webstart) create
 * text file "bluecove.stack" or "bluecove.stack.first" containing stack name
 * and add this file to BlueCove or Application jar. (Since v2.0.1)
 * <p>
 * Use `LocalDevice.getProperty("bluecove.stack")` to find out which stack is
 * used.
 * 
 * @author vlads
 * 
 */
public class BlueCoveImpl {

	public static final int versionMajor = 2;

	public static final int versionMinor = 0;

	public static final int versionBuild = 1;

	public static final String versionSufix = "-SNAPSHOT"; //SNAPSHOT

	public static final String version = String.valueOf(versionMajor) + "." + String.valueOf(versionMinor) + "." + String.valueOf(versionBuild) + versionSufix;

	public static final int nativeLibraryVersionExpected = versionMajor * 10000 + versionMinor * 100 + versionBuild;

	public static final String STACK_WINSOCK = "winsock";

	public static final String STACK_WIDCOMM = "widcomm";

	public static final String STACK_BLUESOLEIL = "bluesoleil";

	public static final String STACK_BLUEZ = "bluez";
	
	public static final String STACK_OSX = "mac";

	// We can't use the same DLL on windows for all implemenations.
	// Since WIDCOMM need to be compile /MD using VC6 and winsock /MT using VC2005
	// This variable can be used to simplify development/test builds
	private static final boolean oneDLLbuild = false;

	public static final String NATIVE_LIB_MS = "intelbth";

	public static final String NATIVE_LIB_WIDCOMM = oneDLLbuild?NATIVE_LIB_MS:"bluecove";

	public static final String NATIVE_LIB_BLUEZ = "bluecove";

	public static final String NATIVE_LIB_OSX = "bluecove";

	/**
	 * To work on BlueSoleil version 2.3 we need to compile C++ code /MT the same as winsock.
	 */
	public static final String NATIVE_LIB_BLUESOLEIL = NATIVE_LIB_MS;

	private BluetoothStack bluetoothStack;

	private static Hashtable configProperty = new Hashtable();
	
	private static final String FQCN = BlueCoveImpl.class.getName();
	
	private static final Vector fqcnSet = new Vector(); 
	
	static {
		fqcnSet.addElement(FQCN);
	}
	
    /**
     * Allow default initialization.
     * In Secure environment instance() should be called initialy from secure contex.
     */
    private static class SingletonHolder {

    	private static BlueCoveImpl instance;
		
    	private static void init() {
			if (instance != null) {
				return;
			}
			instance = new BlueCoveImpl();
		}

	}

	private BlueCoveImpl() {

		BluetoothStack detectorStack = null;
		String stackFirstDetector = getConfigProperty("bluecove.stack.first");
		String stackSelected = getConfigProperty("bluecove.stack");
		if ( stackFirstDetector == null) {
			 stackFirstDetector = stackSelected;
		}

		switch (NativeLibLoader.getOS()) {
			case NativeLibLoader.OS_LINUX:
				if (!NativeLibLoader.isAvailable(NATIVE_LIB_BLUEZ)) {
					throw new Error("BlueCove not available");
				}
				detectorStack = new BluetoothStackBlueZ();
				break;
			case NativeLibLoader.OS_MAC_OS_X:
				if (!NativeLibLoader.isAvailable(NATIVE_LIB_OSX)) {
					throw new Error("BlueCove not available");
				}
				detectorStack = new BluetoothStackOSX();
				break;
			case NativeLibLoader.OS_WINDOWS:
			case NativeLibLoader.OS_WINDOWS_CE:
				detectorStack = createDetectorOnWindows(stackFirstDetector);
				if (DebugLog.isDebugEnabled()) {
					detectorStack.enableNativeDebug(DebugLog.class, true);
				}
				break;
			default:
				throw new Error("BlueCove not available");

		}

		int libraryVersion = detectorStack.getLibraryVersion();
		if (nativeLibraryVersionExpected != libraryVersion) {
			DebugLog.fatal("BlueCove native library version mismatch " + libraryVersion + " expected " + nativeLibraryVersionExpected);
			return;
		}

		if (stackSelected == null) {
			//auto detect
			int aval = detectorStack.detectBluetoothStack();
			DebugLog.debug("BluetoothStack detected", aval);
			if ((aval & 1) != 0) {
				stackSelected = STACK_WINSOCK;
			} else if ((aval & 2) != 0) {
				stackSelected = STACK_WIDCOMM;
			} else if ((aval & 4) != 0) {
				stackSelected = STACK_BLUESOLEIL;
			} else {
				DebugLog.fatal("BluetoothStack not detected");
				throw new RuntimeException("BluetoothStack not detected");
			}
		} else {
			DebugLog.debug("BluetoothStack selected", stackSelected);
		}

		stackSelected = setBluetoothStack(stackSelected, detectorStack);

		// bluetoothStack.destroy(); May stuck in WIDCOMM forever. Exit JVM anyway.
		final ShutdownHookThread shutdownHookThread = new ShutdownHookThread();
		shutdownHookThread.start();

		Runnable r = new Runnable() {
			public void run() {
				synchronized (shutdownHookThread) {
					shutdownHookThread.notifyAll();
					try {
						shutdownHookThread.wait(7000);
					} catch (InterruptedException e) {
					}
				}
			}
		};

		try {
			// since Java 1.3
			UtilsJavaSE.runtimeAddShutdownHook(new Thread(r));
		} catch (Throwable java12) {
		}

		copySystemProperty();
		System.out.println("BlueCove version " + version + " on " + stackSelected);
	}
	
	/**
	 * API that can be used to configure BlueCove properties instead of System properties.
	 * Should be used before stack intitialized. If <code>null</code> is passed as the
	 * <code>value</code> then the property will be removed.
	 * 
	 * @param key
	 * @param value
	 * 
	 * @exception IllegalArgumentException
	 *                if the stack alredy intitialized.
	 */
	public static void setConfigProperty(String key, String value) {
		if (SingletonHolder.instance != null) {
			throw new IllegalArgumentException("BlueCove Stack alredy intitialized");
		}
		if (value == null) {
			configProperty.remove(key);
		} else {
			configProperty.put(key, value);
		}
	}
	
	String getConfigProperty(String key) {
		String value = (String)configProperty.get(key);
		if (value == null) {
			value = System.getProperty(key);
		}
		if (value == null) {
			value = Utils.getResourceProperty(this, key);
		}
		return value;
	}
	
	void copySystemProperty() {
		if (bluetoothStack != null) {
			UtilsJavaSE.setSystemProperty("bluetooth.api.version", "1.1");
			String[] property = { 
					"bluetooth.master.switch", 
					"bluetooth.sd.attr.retrievable.max",
					"bluetooth.connected.devices.max",
					"bluetooth.l2cap.receiveMTU.max", 
					"bluetooth.sd.trans.max",
					"bluetooth.connected.inquiry.scan",
					"bluetooth.connected.page.scan", 
					"bluetooth.connected.inquiry",
					"bluetooth.connected.page" };
			for (int i = 0; i < property.length; i++) {
				UtilsJavaSE.setSystemProperty(property[i], bluetoothStack.getLocalDeviceProperty(property[i]));
			}
		}
	}
	
	private class ShutdownHookThread extends Thread {

		ShutdownHookThread() {
			super("BluecoveShutdownHookThread");
			UtilsJavaSE.threadSetDaemon(this);
		}

		public void run() {
			synchronized (this) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					return;
				}
			}
			if (bluetoothStack != null) {
				bluetoothStack.destroy();
				bluetoothStack = null;
			}
			System.out.println("BlueCove stack shutdown completed");
			synchronized (this) {
				this.notifyAll();
			}
		}

	}

	/**
	 * Applications should not used this function.
	 * 
	 * @return Instance of the class with initializaed stack variable. getBluetoothStack() can be called.
	 * @throws RuntimeException when BluetoothStack not detected. If one connected the hardware later, 
     * BlueCove would be able to recover and start correctly
	 */
    public static BlueCoveImpl instance() throws RuntimeException {
    	SingletonHolder.init();  
		return SingletonHolder.instance;
    }

    private BluetoothStack createDetectorOnWindows(String stackFirst) {
		if (stackFirst != null) {
			DebugLog.debug("detector stack", stackFirst);
			if (STACK_WIDCOMM.equalsIgnoreCase(stackFirst)) { 
				if ((NativeLibLoader.isAvailable(NATIVE_LIB_WIDCOMM))) {
					return new BluetoothStackWIDCOMM();
				}
			} else if (STACK_BLUESOLEIL.equalsIgnoreCase(stackFirst)) {
				if (NativeLibLoader.isAvailable(NATIVE_LIB_BLUESOLEIL)) {
					return new BluetoothStackBlueSoleil();
				}
			} else if (STACK_WINSOCK.equalsIgnoreCase(stackFirst)) {
				if (NativeLibLoader.isAvailable(NATIVE_LIB_MS)) {
					return new BluetoothStackMicrosoft();
				}
			} else {
				throw new Error("Invalid BlueCove detector stack ["+ stackFirst+ "]");
			}
		}
		if (NativeLibLoader.isAvailable(NATIVE_LIB_MS)) {
			return new BluetoothStackMicrosoft();
		} else if (NativeLibLoader.isAvailable(NATIVE_LIB_WIDCOMM)) {
			return new BluetoothStackWIDCOMM();
		} else {
			throw new Error("BlueCove not avalable");
		}
	}

    public String setBluetoothStack(String stack) {
    	return setBluetoothStack(stack, null);
    }

    private String setBluetoothStack(String stack, BluetoothStack detectorStack) {
    	if (bluetoothStack != null) {
    		bluetoothStack.destroy();
    		bluetoothStack = null;
    	}
    	BluetoothStack newStack;
    	if ((detectorStack != null) && (detectorStack.getStackID()).equalsIgnoreCase(stack)) {
    		newStack = detectorStack;
    	} else if (STACK_WIDCOMM.equalsIgnoreCase(stack)) {
    		newStack = new BluetoothStackWIDCOMM();
		} else if (STACK_BLUESOLEIL.equalsIgnoreCase(stack)) {
			newStack = new BluetoothStackBlueSoleil();
		} else {
			newStack = new BluetoothStackMicrosoft();
		}
    	int libraryVersion = newStack.getLibraryVersion();
		if (nativeLibraryVersionExpected != libraryVersion) {
			DebugLog.fatal("BlueCove native library version mismatch " + libraryVersion + " expected " + nativeLibraryVersionExpected);
			return null;
		}

    	if (DebugLog.isDebugEnabled()) {
    		newStack.enableNativeDebug(DebugLog.class, true);
		}
    	newStack.initialize();
    	bluetoothStack = newStack;
    	return bluetoothStack.getStackID();
    }

    public void enableNativeDebug(boolean on) {
    	if (bluetoothStack != null) {
    		bluetoothStack.enableNativeDebug(DebugLog.class, on);
    	}
    }

    /**
     * Applications should not used this function.
     * 
     * @return curent BluetoothStack implementation
     */
	public BluetoothStack getBluetoothStack() {
		Utils.isLegalAPICall(fqcnSet);
		if (bluetoothStack == null) {
			throw new Error("BlueCove not avalable");
		}
		return bluetoothStack;
	}

}
