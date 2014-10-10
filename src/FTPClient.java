import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;


public class FTPClient {
	
	private static final String LOCAL_FILE_NOT_FOUND = "FILE NOT FOUND";
	private static final String CRLF = "\r\n";
	private static final String CLIENT_BASE_DIR = "client-directory";
	private static final String DIRECTORY_LISTING_FILENAME = CLIENT_BASE_DIR + "/directory_listing";
	private static final String DIR = "DIR";
	private static final String GET = "GET";
	private static final String PUT = "PUT";
	private static final String NULL = "NULL___";
	private static final String PASV = "PASV" + CRLF;
	
	private String[] mArgs;
	private String mServerIpString;
	private int mServerControlPort;
	private String mCommand;
	private String mServerPathOfFileToGet;
	private String mClientPathOfFileToPut;
	private String mServerPathOfFileToPut;
	
	private InetAddress mServerIp;
	private Socket mControlSocket;
	private BufferedReader mControlInput;
	private BufferedOutputStream mControlOutput;
	private int mServerDataPort;
	private Socket mDataSocket;
	private BufferedInputStream mDataInput;
	private BufferedOutputStream mDataOutput;
	
	private boolean mAllSpoil = false;
	private boolean mGetSpoil = false;
	private boolean mPutSpoil = false;
	private boolean mPasvOK = false;
	

	public FTPClient() {
		
	}

	public void run(String[] args) {
		mArgs = args;
		try {
			settleArgs();
			setUpControlSocket();
			executeCommand(mCommand);
			closeControlSocket();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void settleArgs() throws IOException {
		if (mArgs.length < 3) {
			mAllSpoil = true;
		} else {
			mServerIpString = mArgs[0];
			mServerControlPort = Integer.parseInt(mArgs[1]);
			mCommand = mArgs[2].toUpperCase();
			if (mCommand.equals(GET)) {
				if (mArgs.length < 4) {
					mGetSpoil = true;
				} else {
					mServerPathOfFileToGet = mArgs[3];
				}
			} else if (mCommand.equals(PUT)) {
				if (mArgs.length < 4) {
					mPutSpoil = true;
				} else {
					mClientPathOfFileToPut = mArgs[3];
					if (mArgs.length == 4) {
						mServerPathOfFileToPut = null;
					} else if (mArgs.length == 5) {
						mServerPathOfFileToPut = mArgs[4];
					}
				}
			} else if (mCommand.equals(DIR)) {
				// do nothing else
			} else {
				mAllSpoil = true;
			}
		}
	}

	private void setUpControlSocket() throws IOException {
		// open control socket
		mServerIp = InetAddress.getByName(mServerIpString);
		openControlSocket();
		mControlOutput.write(PASV.getBytes());
		mControlOutput.flush();
		String responseString = mControlInput.readLine();
		if (!isResponseOK(responseString)) {
			mPasvOK = false;
		} else {
			mPasvOK = true;
		}
		String[] responseArray = responseString.split(" ");
		mServerDataPort = Integer.parseInt(responseArray[3]);
	}

	private void executeCommand(String cmd) throws IOException {
		if (mPasvOK) {
			//send the cmd to server and get response
			String fullCmd = getFullCmd(cmd);
			String cmdResponse = getResponseForCmd(fullCmd);
			
			if (isResponseOK(cmdResponse)) {
				processResponse(cmd, cmdResponse);
			}
		}
	}

	private void processResponse(String cmd, String cmdResponse) throws IOException {
		if (cmd.equals(DIR)) {
			processResponseDIR(cmdResponse);
		} else if (cmd.equals(GET)) {
			processResponseGET(cmdResponse);
		} else if (cmd.equals(PUT)) {
			processResponsePUT(cmdResponse);
		}
	}

	private void processResponseDIR(String response) throws IOException {
		openDataSocket();
		// get input data and write to file.
		BufferedWriter bw = new BufferedWriter(new FileWriter(DIRECTORY_LISTING_FILENAME));
		int i = mDataInput.read();
		while (i != -1) {
			bw.write(i);
			i = mDataInput.read();
		}
		bw.close();
		closeDataSocket();
		logFinalOK();
	}

	private void processResponseGET(String response) throws IOException {
		openDataSocket();
		// put in client-dir root
		String localFilename = new File(mServerPathOfFileToGet).getName();
		String localFilepath = CLIENT_BASE_DIR + "/" + localFilename;
		BufferedWriter bw = new BufferedWriter(new FileWriter(localFilepath));
		int i = mDataInput.read();
		while (i != -1) {
			bw.write(i);
			i = mDataInput.read();
		}
		bw.close();
		closeDataSocket();
		logFinalOK();
	}

	private void processResponsePUT(String response) throws IOException {
		File f = new File(CLIENT_BASE_DIR + "/" + mClientPathOfFileToPut);
		if (!f.exists() || f.isDirectory()) {
			log(LOCAL_FILE_NOT_FOUND);
		} else {
			openDataSocket();
			BufferedInputStream br = new BufferedInputStream(new FileInputStream(f));
			int i = br.read();
			while (i != -1) {
				mDataOutput.write(i);
				i = br.read();
			}
			br.close();
			mDataOutput.flush();
			closeDataSocket();
			logFinalOK();
		}
	
	}

	// ------- HELPER METHODS ------------- //
	
	private String getFullCmd(String cmd) {
		String fullCmd;
		if (mAllSpoil) {
			fullCmd = NULL;
		} else if (cmd.equals(DIR)) {
			fullCmd = cmd;
		} else if (cmd.equals(GET)) {
			if (mGetSpoil) {
				fullCmd = cmd;
			} else {
				fullCmd = cmd + " " + mServerPathOfFileToGet;
			}
		} else if (cmd.equals(PUT)) {
			if (mPutSpoil) {
				fullCmd = cmd;
			} else {
				fullCmd = cmd + " " + mClientPathOfFileToPut;
				if (mServerPathOfFileToPut != null) {
					fullCmd += " " + mServerPathOfFileToPut;
				}
			}

		} else {
			fullCmd = cmd;
		}
		fullCmd += CRLF;
		return fullCmd;
	}
	
	private String getResponseForCmd(String fullCmd) throws IOException {
		mControlOutput.write(fullCmd.getBytes());
		mControlOutput.flush();
		String cmdResponse = mControlInput.readLine();
		log(cmdResponse);
		return cmdResponse;
	}

	private boolean isResponseOK(String responseString) throws IOException {
		boolean result = true;
		if (responseString == null || responseString.length() == 0) {
			result = false;
		} else {
			String[] responseCodes = responseString.split(" ");
			if (!responseCodes[0].equals("200")) {
				result = false;
			}
		}
		return result;
	}

	private void logFinalOK() throws IOException {
		String cmdResponse = mControlInput.readLine();
		log(cmdResponse);
	}

	private void openControlSocket() throws IOException {
		mControlSocket = new Socket(mServerIp, mServerControlPort);
		mControlOutput = new BufferedOutputStream (new DataOutputStream(mControlSocket.getOutputStream()));
		mControlInput = new BufferedReader(new InputStreamReader(mControlSocket.getInputStream()));
	}

	private void openDataSocket() throws IOException {
		mDataSocket = new Socket(mServerIp, mServerDataPort);
		mDataOutput = new BufferedOutputStream (new DataOutputStream(mDataSocket.getOutputStream()));
		mDataInput = new BufferedInputStream(mDataSocket.getInputStream());
	}
	
	private void log(String msg) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter("log"));
		bw.write(msg);
		bw.close();
	}
	
	private void closeDataSocket() throws IOException {
		this.mDataSocket.close();
		this.mDataInput.close();
		this.mDataOutput.close();
	}

	private void closeControlSocket() throws IOException {
		this.mControlSocket.close();
		this.mControlInput.close();
		this.mControlOutput.close();
	}

	private void println(String s) {
		System.out.println(s);
	}
	
	
	
	
	
	
	
	// ------------ MAIN METHOD ------------------ //
	
	public static void main(String[] args) {
		new FTPClient().run(args);
	}

}
