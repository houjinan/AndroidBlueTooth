package com.bluetooth.phone.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("NewApi")
public class MainActivity extends Activity implements OnClickListener {
	private Button mStartScanBtn = null;
	private Button mStopScanBtn = null;
	private Button mConnectedBtn = null;
	private Button mCreateServerSocketBtn = null;
	private Button mCreateClientSocketBtn = null;
	private TextView mConnectedsTxt = null;
	private Button mDiscover = null;

	private BroadcastReceiver mReceiver;
	private BluetoothAdapter adapter;

	private UUID MY_UUID = UUID
			.fromString("08590F7E-DB05-467E-8757-72F6FAEB13D4");//E20A39F4-73F5-4BC4-A12F-17D1AD07A961
	private BluetoothDevice server_device = null;

	ListView lvBTDevices;
	ArrayAdapter<String> adtDevices;
	List<String> lstDevices = new ArrayList<String>();

	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private String mBluetoothDeviceAddress;
	private BluetoothGatt mBluetoothGatt;
	private int mConnectionState = STATE_DISCONNECTED;
	private static final int STATE_DISCONNECTED = 0;
	private static final int STATE_CONNECTING = 1;
	private static final int STATE_CONNECTED = 2;
	public final static String ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
	public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
	public final static String ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
	public final static String EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA";
	private final static String TAG = MainActivity.class.getSimpleName();
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mStartScanBtn = (Button) findViewById(R.id.btn_start_scan);
		mStopScanBtn = (Button) findViewById(R.id.btn_stop_scan);
		mConnectedBtn = (Button) findViewById(R.id.btn_connected);
		mCreateServerSocketBtn = (Button) findViewById(R.id.btn_create_server_socket);
		mCreateClientSocketBtn = (Button) findViewById(R.id.btn_create_client_socket);
		mDiscover = (Button) findViewById(R.id.btn_discover);
		mConnectedsTxt = (TextView) findViewById(R.id.txt_connecteds);
		mConnectedBtn.setOnClickListener(this);
		mStartScanBtn.setOnClickListener(this);
		mStopScanBtn.setOnClickListener(this);
		mCreateServerSocketBtn.setOnClickListener(this);
		mCreateClientSocketBtn.setOnClickListener(this);
		mDiscover.setOnClickListener(this);

		// ListView及其数据源 适配器
		lvBTDevices = (ListView) this.findViewById(R.id.lvDevices);
		adtDevices = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, lstDevices);
		lvBTDevices.setAdapter(adtDevices);
		lvBTDevices.setOnItemClickListener(new ItemClickEvent());

		// 获得BluetoothAdapter对象，该API是android 2.0开始支持的
		adapter = BluetoothAdapter.getDefaultAdapter();

		mReceiver = new SingBroadcastReceiver();
		// 注册Receiver来获取蓝牙设备相关的结果
		String ACTION_PAIRING_REQUEST = "android.bluetooth.device.action.PAIRING_REQUEST";
		IntentFilter intent = new IntentFilter();
		intent.addAction(BluetoothDevice.ACTION_FOUND);// 用BroadcastReceiver来取得搜索结果
		intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		intent.addAction(ACTION_PAIRING_REQUEST);
		intent.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
		intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		this.registerReceiver(mReceiver, intent);
	}

	class ItemClickEvent implements AdapterView.OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			if (adapter.isDiscovering())
				adapter.cancelDiscovery();
			String str = lstDevices.get(arg2);
			String[] values = str.split("\\|");
			String address = values[2];
			Log.e("address", values[2]);
			BluetoothDevice btDev = adapter.getRemoteDevice(address);
			try {
				Boolean returnValue = false;
				if (btDev.getBondState() == BluetoothDevice.BOND_NONE) {
					// 利用反射方法调用BluetoothDevice.createBond(BluetoothDevice
					// remoteDevice);
					// Method createBondMethod =
					// BluetoothDevice.class.getMethod("createBond");
					// Log.d("BlueToothTestActivity", "开始配对");
					// returnValue = (Boolean) createBondMethod.invoke(btDev);
					ClsUtils.pair(address, "000000");
				} else if (btDev.getBondState() == BluetoothDevice.BOND_BONDED) {
					connect(btDev);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void connect(BluetoothDevice btDev) {
		new ConnectThread(btDev).start();
	}

	UUID[] uuids = new UUID[] { MY_UUID };
	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, int rssi,
				byte[] scanRecord) {

			System.out.println("find________" + device.getName() + "________"
					+ device.getUuids() + "________" + device.getAddress());
			mBluetoothGatt =device.connectGatt(getApplicationContext(), true, mGattCallback);
			//mBluetoothGatt.connect();
			//new ConnectThread(device).start();
			
			runOnUiThread(new Runnable() {
				@Override
				public void run() {

				}
			});
		}
	};

	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
				int newState) {
			String intentAction;
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				adapter.stopLeScan(mLeScanCallback);
				
				intentAction = ACTION_GATT_CONNECTED;
				mConnectionState = STATE_CONNECTED;
				//broadcastUpdate(intentAction);
				// Log.i(TAG, "Connected to GATT server.");
				// Log.i(TAG, "Attempting to start service discovery:"
				// + mBluetoothGatt.discoverServices());
				
				BluetoothGattCharacteristic characteristic =new BluetoothGattCharacteristic(MY_UUID, 1, 1);
				boolean enabled = true;
				gatt.setCharacteristicNotification(characteristic, enabled);
				BluetoothGattDescriptor descriptor = characteristic.getDescriptor(MY_UUID);
				descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				mBluetoothGatt.writeDescriptor(descriptor);

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = ACTION_GATT_DISCONNECTED;
				mConnectionState = STATE_DISCONNECTED;
				// Log.i(TAG, "Disconnected from GATT server.");
				//broadcastUpdate(intentAction);
			}
		}

		@Override
		// New services discovered
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
			} else {
				// Log.w(TAG, "onServicesDiscovered received: " + status);
			}
		}

		@Override
		// Result of a characteristic read operation
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
			}
		}
	};

	private void broadcastUpdate(final String action) {
		final Intent intent = new Intent(action);
		sendBroadcast(intent);
	}

	private void broadcastUpdate(final String action,
			final BluetoothGattCharacteristic characteristic) {
		final Intent intent = new Intent(action);

		// This is special handling for the Heart Rate Measurement profile. Data
		// parsing is carried out as per profile specifications.
		if (MY_UUID.equals(characteristic.getUuid())) {
			int flag = characteristic.getProperties();
			int format = -1;
			if ((flag & 0x01) != 0) {
				format = BluetoothGattCharacteristic.FORMAT_UINT16;
				Log.d(TAG, "Heart rate format UINT16.");
			} else {
				format = BluetoothGattCharacteristic.FORMAT_UINT8;
				Log.d(TAG, "Heart rate format UINT8.");
			}
			final int heartRate = characteristic.getIntValue(format, 1);
			Log.d(TAG, String.format("Received heart rate: %d", heartRate));
			intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
		} else {
			// For all other profiles, writes the data formatted in HEX.
			final byte[] data = characteristic.getValue();
			if (data != null && data.length > 0) {
				final StringBuilder stringBuilder = new StringBuilder(
						data.length);
				for (byte byteChar : data)
					stringBuilder.append(String.format("%02X ", byteChar));
				intent.putExtra(EXTRA_DATA, new String(data) + "\n"
						+ stringBuilder.toString());
			}
		}
		sendBroadcast(intent);
	}

	@Override
	public void onClick(View v) {

		// adapter不等于null，说明本机有蓝牙设备
		if (adapter != null) {
			System.out.println("本机有蓝牙设备！");
			// 如果蓝牙设备未开启
			if (!adapter.isEnabled()) {
				Intent intent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				// 请求开启蓝牙设备
				startActivity(intent);
				return;
			}
		} else {
			System.out.println("本机没有蓝牙设备！");
			return;
		}
		switch (v.getId()) {
		case R.id.btn_start_scan:
			lstDevices.clear();
			if (adapter.isDiscovering())
				adapter.cancelDiscovery();
			setTitle("本机蓝牙地址：" + adapter.getAddress());

			boolean result = adapter.startLeScan(mLeScanCallback);
			System.out.println("start le scan : " + result);
			// adapter.startDiscovery();
			// List<ScanFilter> filters=new ArrayList<ScanFilter>();
			// ScanFilter scanFilterM=ScanFilter.Builder;
			// scanFilterM.
			//
			// BluetoothLeScanner.startScan(filters, ScanSettings, ScanCallback)
			break;
		case R.id.btn_stop_scan:
			if (adapter != null && adapter.isDiscovering()) {
				adapter.cancelDiscovery();
			}
			break;
		case R.id.btn_connected:
			mConnectedsTxt.setText("");
			Set<BluetoothDevice> devices = adapter.getBondedDevices();
			if (devices.size() > 0) {
				for (Iterator<BluetoothDevice> it = devices.iterator(); it
						.hasNext();) {
					BluetoothDevice device = (BluetoothDevice) it.next();
					if (device.getName().equals("小米手机")) {
						server_device = device;
					}
					mConnectedsTxt.setText(mConnectedsTxt.getText() + "\n"
							+ device.getAddress());
					// 打印出远程蓝牙设备的物理地址
					System.out.println(device.getAddress());
				}
			}
			break;
		case R.id.btn_create_server_socket:

			// BluetoothServerSocket bluetoothServerSocket = adapter
			// .listenUsingRfcommWithServiceRecord("", MY_UUID);
			new AcceptThread().start();
			// BluetoothServerSocket mmServerSocket = null;
			// try {
			// mmServerSocket = adapter.listenUsingRfcommWithServiceRecord(
			// adapter.getName(), MY_UUID);
			// while (true) {
			// System.out.println("socket_start");
			// BluetoothSocket socket = mmServerSocket.accept();
			// System.out.println("socket_end");
			// }
			// } catch (IOException e) {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// }
			break;
		case R.id.btn_create_client_socket:
			if (server_device == null) {
				Toast.makeText(this, "no search server ,please search!",
						Toast.LENGTH_SHORT).show();
				return;
			}
			new ConnectThread(server_device).start();
			break;
		case R.id.btn_discover:
			Intent discoverableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(
					BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent);
			break;
		default:
			break;
		}

	}

	private class ScanFilterM implements Parcelable {

		@Override
		public int describeContents() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			// TODO Auto-generated method stub

		}

	}

	private class SingBroadcastReceiver extends BroadcastReceiver {

		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction(); // may need to chain this to a
												// recognizing function
			BluetoothDevice device = null;
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device.getName().equals("小米手机")) {
					server_device = device;
				}
				// Add the name and address to an array adapter to show in a
				// Toast
				String derp = device.getName() + " - " + device.getAddress();

				if (device.getBondState() == BluetoothDevice.BOND_NONE) {
					String str = "未配对|" + device.getName() + "|"
							+ device.getAddress();
					if (lstDevices.indexOf(str) == -1)// 防止重复添加
						lstDevices.add(str); // 获取设备名称和mac地址
					adtDevices.notifyDataSetChanged();

					try {
						ClsUtils.setPin(device.getClass(), device, "000000");
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					try {
						ClsUtils.cancelPairingUserInput(device.getClass(),
								device);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				} else {
					String str = "已配对|" + device.getName() + "|"
							+ device.getAddress();
					lstDevices.add(str); // 获取设备名称和mac地址
					adtDevices.notifyDataSetChanged();
				}
			} else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
				device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				switch (device.getBondState()) {
				case BluetoothDevice.BOND_BONDING:
					Log.d("BlueToothTestActivity", "正在配对......");
					break;
				case BluetoothDevice.BOND_BONDED:
					Log.d("BlueToothTestActivity", "完成配对");
					connect(device);// 连接设备
					break;
				case BluetoothDevice.BOND_NONE:
					Log.d("BlueToothTestActivity", "取消配对");
				default:
					break;
				}
			}

			// if (intent.getAction().equals(
			// "android.bluetooth.device.action.PAIRING_REQUEST")) {
			// Log.e("tag11111111111111111111111", "ddd");
			// BluetoothDevice btDevice = intent
			// .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			// // byte[] pinBytes = BluetoothDevice.convertPinToBytes("1234");
			// // device.setPin(pinBytes);
			// try {
			// ClsUtils.setPin(btDevice.getClass(), btDevice, "000000"); //
			// 手机和蓝牙采集器配对
			// ClsUtils.createBond(btDevice.getClass(), btDevice);
			// // ClsUtils.cancelPairingUserInput(btDevice.getClass(),
			// // btDevice);
			// } catch (Exception e) {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// }
			// }
		}
	}

	private class AcceptThread extends Thread {
		private final BluetoothServerSocket mmServerSocket;

		public AcceptThread() {
			// Use a temporary object that is later assigned to mmServerSocket,
			// because mmServerSocket is final
			BluetoothServerSocket tmp = null;
			try {
				// MY_UUID is the app's UUID string, also used by the client
				// code
				tmp = adapter.listenUsingRfcommWithServiceRecord(
						adapter.getName(), MY_UUID);
			} catch (IOException e) {
			}
			mmServerSocket = tmp;
		}

		public void run() {
			BluetoothSocket socket = null;
			// Keep listening until exception occurs or a socket is returned
			while (true) {
				try {
					System.out
							.println("Run socket..................................");
					socket = mmServerSocket.accept();

					// If a connection was accepted
					if (socket != null) {
						// Do work to manage the connection (in a separate
						// thread)
						System.out
								.println("Run find..................................");
						manageConnectedSocket(socket);
						mmServerSocket.close();
						break;
					}
				} catch (Exception e) {
					System.out.println("" + e.toString());
					e.printStackTrace();
					break;
				}
			}
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancel() {
			try {
				mmServerSocket.close();
			} catch (IOException e) {
			}
		}
	}

	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device) {
			// Use a temporary object that is later assigned to mmSocket,
			// because mmSocket is final
			BluetoothSocket tmp = null;
			mmDevice = device;

			// Get a BluetoothSocket to connect with the given BluetoothDevice
			try {
				// MY_UUID is the app's UUID string, also used by the server
				// code
				tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
			} catch (IOException e) {
			}
			mmSocket = tmp;
		}

		public void run() {
			// Cancel discovery because it will slow down the connection
			adapter.cancelDiscovery();

			try {
				// Connect the device through the socket. This will block
				// until it succeeds or throws an exception
				mmSocket.connect();
			} catch (IOException connectException) {
				// Unable to connect; close the socket and get out
				try {
					mmSocket.close();
				} catch (IOException closeException) {
				}
				return;
			}

			// Do work to manage the connection (in a separate thread)
			manageConnectedSocket(mmSocket);
		}

		/** Will cancel an in-progress connection, and close the socket */
		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
			}
		}
	}

	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;
			String deviceName = android.os.Build.MODEL;
			byte[] name = deviceName.getBytes(); // new byte[] { '3' };

			// Get the input and output streams, using temp objects because
			// member streams are final
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;

			write(name);
		}

		public void run() {
			byte[] buffer = new byte[1024]; // buffer store for the stream
			int bytes; // bytes returned from read()

			// Keep listening to the InputStream until an exception occurs
			while (true) {
				try {
					// Read from the InputStream
					bytes = mmInStream.read(buffer);
					String result = Util.converts(mmInStream);
					String sendString = new String(buffer, "UTF-8");
					System.out.println("" + result);
					// Send the obtained bytes to the UI Activity
					// mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
					// .sendToTarget();
				} catch (IOException e) {
					break;
				}
			}
		}

		/* Call this from the main Activity to send data to the remote device */
		public void write(byte[] bytes) {
			try {
				mmOutStream.write(bytes);
			} catch (IOException e) {
			}
		}

		/* Call this from the main Activity to shutdown the connection */
		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
			}
		}
	}

	private void manageConnectedSocket(BluetoothSocket mmSocket) {
		new ConnectedThread(mmSocket).start();
	}
	
	
	
	
	// Demonstrates how to iterate through the supported GATT
    // Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the
    // ExpandableListView on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString ="unknown_service";
        String unknownCharaString = "unknown_characteristic";
        ArrayList<HashMap<String, String>> gattServiceData =
                new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
       List<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData =
                    new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    "aaa", "aaa");
            currentServiceData.put("", uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();
           // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic :
                    gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData =
                        new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentServiceData.put(
                        "aaa", "aaa");
                currentServiceData.put("", uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
         }
    }
}