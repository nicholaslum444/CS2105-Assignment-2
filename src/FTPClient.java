import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;


public class FTPClient {
	
	private static final String CLIENT_BASE_DIR = "client-directory/";
	private static final String SERVER_BASE_DIR = "server-directory/";
	private static final String DIR = "DIR";
	private static final String GET = "GET";
	private static final String PUT = "PUT";
	
	private String[] mArgs;
	private String mServerIpString;
	private int mServerPort;
	private String mCommand;
	private String mServerPathOfFileToGet;
	private String mClientPathOfFileToPut;
	private String mServerPathOfFileToPut;
	
	private InetAddress mServerIp;
	private Socket mSocket;
	private BufferedReader mInput;
	private DataOutputStream mOutput;

	public FTPClient() {

	}

	public void run(String[] args) {
		mArgs = args;
		try {
			settleArgs();
			setUpSocket();
			executeCommand();

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void executeCommand() throws Exception {
		if (mCommand.equals(DIR)) {
			
		} else if (mCommand.equals(GET)) {
			
		} else if (mCommand.equals(PUT)) {
			
		} else {
			throw new Exception();
		}
	}

	private void setUpSocket() throws UnknownHostException, IOException {
		InetAddress ip = InetAddress.getByName(mServerIpString);
		mSocket = new Socket(ip, mServerPort);
		mOutput = new DataOutputStream(mSocket.getOutputStream());
		mInput = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
	}

	private boolean settleArgs() throws Exception {
		if (mArgs.length < 3) {
			println("not enough args");
			throw new Exception();
		}
		
		mServerIpString = mArgs[0];
		mServerPort = Integer.parseInt(mArgs[1]);
		mCommand = mArgs[2];
		
		if (mCommand.equals(GET)) {
			if (mArgs.length < 4) {
				println("not enough args");
				throw new Exception();
			}
			mServerPathOfFileToGet = SERVER_BASE_DIR + mArgs[3];
			
		} else if (mCommand.equals(PUT)) {
			if (mArgs.length < 4) {
				println("not enough args");
				throw new Exception();
			}
			mClientPathOfFileToPut = CLIENT_BASE_DIR + mArgs[3];
			
			if (mArgs.length == 4) {
				mServerPathOfFileToPut = SERVER_BASE_DIR;
			}
			
			if (mArgs.length == 5) {
				mServerPathOfFileToPut = SERVER_BASE_DIR + mArgs[4];
			}
		} else if (mCommand.equals(DIR)) {
			
		} else {
			println("invalid command");
			throw new Exception();
		}
		
		return true;
	}
	
	
	
	
	
	
	
	public void println(String s) {
		System.out.println(s);
	}
	
	public static void main(String[] args) {
		new FTPClient().run(args);
	}

}
