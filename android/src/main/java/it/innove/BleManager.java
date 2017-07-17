package it.innove;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.LinkedHashMap;
import java.util.Map;

import static android.app.Activity.RESULT_OK;


class BleManager extends ReactContextBaseJavaModule implements ActivityEventListener {

	public static final String LOG_TAG = "logs";
	private static final int ENABLE_REQUEST = 539;


	//private BluetoothAdapter bluetoothAdapter;
	private Context context;
	private ReactApplicationContext reactContext;
	private Callback enableBluetoothCallback;
	private ScanManager scanManager;

	// key is the MAC Address
	public Map<String, Peripheral> peripherals = new LinkedHashMap<>();
	// scan session id
	private BLEEvent bleEvent;
	private BleBinder bleBinder;
	private ServiceConnection conn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
			bleBinder = (BleBinder) iBinder;
			bleBinder.setReactContext(reactContext);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			bleBinder = null;
		}
	};
	public BleManager(ReactApplicationContext reactContext) {
		super(reactContext);
		context = reactContext;
		this.reactContext = reactContext;
		bleEvent = new BLEEvent(reactContext);
		reactContext.addActivityEventListener(this);
		Log.d(LOG_TAG, "BleManager created");
		Intent intent = new Intent(context,BackgroundService.class);
		context.startService(intent);
		context.bindService(intent,conn, Service.BIND_AUTO_CREATE);
	}

	@Override
	public String getName() {
		return "BleManager";
	}

	private BluetoothAdapter getBluetoothAdapter() {
		return bleBinder.getBluetoothAdapter();
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(LOG_TAG, "onReceive");
			final String action = intent.getAction();

			String stringState = "";
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				switch (state) {
					case BluetoothAdapter.STATE_OFF:
						stringState = "off";
						break;
					case BluetoothAdapter.STATE_TURNING_OFF:
						stringState = "turning_off";
						break;
					case BluetoothAdapter.STATE_ON:
						stringState = "on";
						break;
					case BluetoothAdapter.STATE_TURNING_ON:
						stringState = "turning_on";
						break;
				}
			}

			WritableMap map = Arguments.createMap();
			map.putString("state", stringState);
			Log.d(LOG_TAG, "state: " + stringState);
			bleEvent.sendEvent(BLEEvent.EVENT_BLEMANAGER_DID_UPDATE_STATE, map);
		}
	};

	@Override
	public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
		Log.d(LOG_TAG, "onActivityResult");
		if (requestCode == ENABLE_REQUEST && enableBluetoothCallback != null) {
			if (resultCode == RESULT_OK) {
				enableBluetoothCallback.invoke();
			} else {
				enableBluetoothCallback.invoke("User refused to enable");
			}
			enableBluetoothCallback = null;
		}
	}

	@Override
	public void onNewIntent(Intent intent) {

	}

	@ReactMethod
	public void checkState(){
		WritableMap map = Arguments.createMap();
		map.putString("state", bleBinder.checkState());
		bleEvent.sendEvent(BLEEvent.EVENT_BLEMANAGER_DID_UPDATE_STATE, map);
	}

	@ReactMethod
	public void start(ReadableMap options, Callback callback) {
		Log.d(LOG_TAG, "start");
		if (getBluetoothAdapter() == null) {
			Log.d(LOG_TAG, "No bluetooth support");
			callback.invoke("No bluetooth support");
			return;
		}
		boolean forceLegacy = false;
		if (options.hasKey("forceLegacy")) {
			forceLegacy = options.getBoolean("forceLegacy");
		}

		IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		context.registerReceiver(mReceiver, filter);
		callback.invoke();
		Log.d(LOG_TAG, "BleManager initialized");

	}

	@ReactMethod
	public void enableBluetooth(Callback callback) {
		if (getBluetoothAdapter() == null) {
			Log.d(LOG_TAG, "No bluetooth support");
			callback.invoke("No bluetooth support");
			return;
		}
		if (!getBluetoothAdapter().isEnabled()) {
			enableBluetoothCallback = callback;
			if (getCurrentActivity() == null){
				callback.invoke("Current activity not available");
			} else {
				Intent intentEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				getCurrentActivity().startActivityForResult(intentEnable, ENABLE_REQUEST);
			}
		} else
			callback.invoke();
	}

	// 未完成，需要完善peripheral中的Writable相关功能
	@ReactMethod
	public void scan(ReadableArray serviceUUIDs, final int scanSeconds, boolean allowDuplicates, ReadableMap options, final Callback callback) {
		Log.d(LOG_TAG, "scan");

		bleBinder.startScan(serviceUUIDs,scanSeconds,options,new CallBackManager.Scaner(){
			@Override
			public void onFinded(Peripheral peripheral) {
				WritableMap map = peripheral.asWritableMap();
				bleEvent.sendEvent(BLEEvent.EVENT_BLEMANAGER_DISCOVER_PERIPHERAL,map);
			}

			@Override
			public void onStop() {
				WritableMap map = Arguments.createMap();
				bleEvent.sendEvent(BLEEvent.EVENT_BLEMANAGER_STOP_SCAN,map);
			}

			@Override
			public void onResult(String text) {
				if(text == null){
					callback.invoke();
				}else {
					callback.invoke(text);
				}
			}
		});
		callback.invoke();
	}

	// 未完成，需要完善peripheral中的Writable相关功能
	@ReactMethod
	public void stopScan(final Callback callback) {
		Log.d(LOG_TAG, "Stop scan");

		//scanManager.stopScan(callback);
		bleBinder.stopScan(new CallBackManager.Scaner() {
			@Override
			public void onFinded(Peripheral peripheral) {

			}

			@Override
			public void onStop() {
				WritableMap map = Arguments.createMap();
				bleEvent.sendEvent(BLEEvent.EVENT_BLEMANAGER_STOP_SCAN,map);
			}

			@Override
			public void onResult(String text) {
				if(text == null){
					callback.invoke();
				}else {
					callback.invoke(text);
				}
			}
		});
		callback.invoke();
	}

	@ReactMethod
	public void connect(String peripheralUUID, final Callback callback) {
		Log.d(LOG_TAG, "Connect to: " + peripheralUUID );
		bleBinder.connect(getCurrentActivity(),peripheralUUID, new CallBackManager.PeripheralConnect() {
			@Override
			public void onConnect(BluetoothDevice device) {
				WritableMap map = Arguments.createMap();
				map.putString("peripheral", device.getAddress());
				Log.d(LOG_TAG, "Peripheral event ("+ BLEEvent.EVENT_BLEMANAGER_CONNECT_PERIPHERAL +"):" + device.getAddress());
				bleEvent.sendEvent(BLEEvent.EVENT_BLEMANAGER_DISCONNECT_PERIPHERAL,map);
			}

			@Override
			public void onDisconnect(BluetoothDevice device) {
				WritableMap map = Arguments.createMap();
				map.putString("peripheral", device.getAddress());
				Log.d(LOG_TAG, "Peripheral event ("+ BLEEvent.EVENT_BLEMANAGER_DISCONNECT_PERIPHERAL +"):" + device.getAddress());
				bleEvent.sendEvent(BLEEvent.EVENT_BLEMANAGER_DISCONNECT_PERIPHERAL,map);
			}

			@Override
			public void onResult(String text) {
				if(text == null){
					callback.invoke();
				}else {
					callback.invoke(text);
				}
			}
		});

	}

	@ReactMethod
	public void disconnect(String peripheralUUID, final Callback callback) {
		Log.d(LOG_TAG, "Disconnect from: " + peripheralUUID);
		bleBinder.disconnect(peripheralUUID,new CallBackManager.PeripheralConnect(){
			@Override
			public void onConnect(BluetoothDevice device) {

			}

			@Override
			public void onDisconnect(BluetoothDevice device) {

			}

			@Override
			public void onResult(String text) {
				if(text == null){
					callback.invoke();
				}else {
					callback.invoke(text);
				}
			}
		});

	}
	// 未完成，需要完善peripheral中的Writable相关功能
	@ReactMethod
	public void retrieveServices(String deviceUUID, final Callback callback) {
		Log.d(LOG_TAG, "Retrieve services from: " + deviceUUID);

		bleBinder.retrieveServices(deviceUUID,new CallBackManager.RetrieveServices(){
			@Override
			public void onSuccessed(WritableMap map) {
//				WritableMap map = .asWritableMap(gatt);
				callback.invoke(null,map);
			}

			@Override
			public void onFailed(String text) {
				callback.invoke(text,null);
			}
		});
	}

	@ReactMethod
	public void removePeripheral(String deviceUUID, Callback callback) {
		String result = bleBinder.removePeripheral(deviceUUID);
		callback.invoke(result);
	}

	@ReactMethod
	public void getDiscoveredPeripherals(Callback callback) {
		Log.d(LOG_TAG, "Get discovered peripherals");
		WritableArray map = Arguments.createArray();
		Map<String, Peripheral> peripherals = bleBinder.getPeripherals();
		Map <String, Peripheral> peripheralsCopy = new LinkedHashMap<>(peripherals);
		for (Map.Entry<String, Peripheral> entry : peripheralsCopy.entrySet()) {
			Peripheral peripheral = entry.getValue();
			WritableMap jsonBundle = peripheral.asWritableMap();
			map.pushMap(jsonBundle);
		}
		callback.invoke(null, map);
	}

	@ReactMethod
	public void getConnectedPeripherals(ReadableArray serviceUUIDs, Callback callback) {
		Log.d(LOG_TAG, "Get connected peripherals");
		WritableArray map = Arguments.createArray();
		Map<String, Peripheral> peripherals = bleBinder.getPeripherals();
		Map <String, Peripheral> peripheralsCopy = new LinkedHashMap<>(peripherals);
		for (Map.Entry<String, Peripheral> entry : peripheralsCopy.entrySet()) {
			Peripheral peripheral = entry.getValue();
			Boolean accept = false;

			if (serviceUUIDs != null && serviceUUIDs.size() > 0) {
				for (int i = 0; i < serviceUUIDs.size(); i++) {
					accept = peripheral.hasService(UUIDHelper.uuidFromString(serviceUUIDs.getString(i)));
				}
			} else {
				accept = true;
			}

			if (peripheral.isConnected() && accept) {
				WritableMap jsonBundle = peripheral.asWritableMap();
				map.pushMap(jsonBundle);
			}
		}
		callback.invoke(null, map);
	}

	@ReactMethod
	public void write(String deviceUUID, String serviceUUID, String characteristicUUID, ReadableArray message, Integer maxByteSize, final Callback callback) {
		Log.d(LOG_TAG, "Write to: " + deviceUUID);
		bleBinder.write(deviceUUID, serviceUUID, characteristicUUID, message, maxByteSize, new CallBackManager.PeripheralWrite() {
			@Override
			public void onResult(String text) {
				callback.invoke(text);
			}
		});
	}

	@ReactMethod
	public void writeWithoutResponse(String deviceUUID, String serviceUUID, String characteristicUUID, ReadableArray message, Integer maxByteSize, Integer queueSleepTime, final Callback callback) {
		Log.d(LOG_TAG, "Write without response to: " + deviceUUID);

		bleBinder.writeWithoutResponse(deviceUUID, serviceUUID, characteristicUUID, message, maxByteSize, queueSleepTime, new CallBackManager.PeripheralWrite() {
			@Override
			public void onResult(String text) {
				callback.invoke(text);
			}
		});
	}


	@ReactMethod
	public void read(String deviceUUID, String serviceUUID, String characteristicUUID, final Callback callback) {
		Log.d(LOG_TAG, "Read from: " + deviceUUID);
		bleBinder.read(deviceUUID, serviceUUID, characteristicUUID, new CallBackManager.PeripheralRead() {
			@Override
			public void onSuccessed(byte[] value) {
				callback.invoke(null,bytesToWritableArray(value));
			}

			@Override
			public void onFailed(String text) {
				callback.invoke(text,null);
			}
		});
	}

	@ReactMethod
	public void readRSSI(String deviceUUID, final Callback callback) {
		Log.d(LOG_TAG, "Read RSSI from: " + deviceUUID);

		bleBinder.readRssi(deviceUUID, new CallBackManager.PeripheralRssiRead() {
			@Override
			public void onSuccessed(int rssi) {
				callback.invoke(null,rssi);
			}

			@Override
			public void onFailed(String text) {
				callback.invoke(text,null);
			}
		});
	}
//////////////////////////////////////////////////////////////

	@ReactMethod
	public void startNotification(String deviceUUID, String serviceUUID, String characteristicUUID, final Callback callback) {
		Log.d(LOG_TAG, "startNotification");
		bleBinder.startNotification(deviceUUID, serviceUUID, characteristicUUID, new CallBackManager.PeripheralNotification() {
			@Override
			public void onResult(String text) {
				callback.invoke(text);
			}
		});
	}

	@ReactMethod
	public void stopNotification(String deviceUUID, String serviceUUID, String characteristicUUID, final Callback callback) {
		Log.d(LOG_TAG, "stopNotification");

		bleBinder.stopNotification(deviceUUID, serviceUUID, characteristicUUID, new CallBackManager.PeripheralNotification() {
			@Override
			public void onResult(String text) {
				callback.invoke(text);
			}
		});
	}

	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static WritableArray bytesToWritableArray(byte[] bytes) {
		WritableArray value = Arguments.createArray();
		for(int i = 0; i < bytes.length; i++)
			value.pushInt((bytes[i] & 0xFF));
		return value;
	}


}
