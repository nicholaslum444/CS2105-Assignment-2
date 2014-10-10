import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;


public class FTPServer {

	private static final String CRLF = "\r\n";
	private static final String SERVER_BASE_DIR = "server-directory";
	private static final String DIR = "DIR";
	private static final String GET = "GET";
	private static final String PUT = "PUT";
	private static final String PASV = "PASV";
	
	private static final String OK = "200 OK";
	private static final String UC = "500 UNKNOWN COMMAND";
	private static final String IA = "501 INVALID ARGUMENTS";
	private static final String FNF = "401 FILE NOT FOUND";
	
	private static final String DIR_OK = "200 DIR COMMAND OK";
	private static final String SERVER_EMPTY = "---the server directory is empty---";
	
	private String[] mArgs;
	private int mServerControlPort;
	private InetAddress mServerIp;
	private ServerSocket mControlServerSocket;
	private Socket mControlSocket;
	private BufferedReader mControlInput;
	private BufferedOutputStream mControlOutput;
	private int mPreviousDataPort = -1;
	private int mServerDataPort;
	private ServerSocket mDataServerSocket;
	private Socket mDataSocket;
	private BufferedInputStream mDataInput;
	private BufferedOutputStream mDataOutput;
	
	private String mCommandString;
	public boolean mPasvOK = false;

	public FTPServer() {

	}
	
	
	public void run(String[] args) {
		mArgs = args;
		try {
			settleArgs();
			createControlServerSocket();
			while (true) {
				setupControlSocket();
				executeCommand();
				closeControlSocket();
			}

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void settleArgs() {
		if (mArgs.length < 1) {
			throw new IllegalArgumentException("insufficient args");
		} else {
			mServerControlPort = Integer.parseInt(mArgs[0]);
			if (mArgs.length > 1) {
				mServerDataPort = Integer.parseInt(mArgs[1]);
				if (mServerDataPort == mServerControlPort) {
					mServerDataPort = mServerControlPort + 1;
				}
			} else {
				mServerDataPort = mServerControlPort + 1;
			}
		}
	}


	private void createControlServerSocket() throws UnknownHostException, IOException {
		mServerIp = InetAddress.getLocalHost();
		mControlServerSocket = new ServerSocket(mServerControlPort);
	}


	private void setupControlSocket() throws IOException {
		mControlSocket = mControlServerSocket.accept(); // wait for client to join
		mControlInput = new BufferedReader(new InputStreamReader(mControlSocket.getInputStream()));
		mControlOutput = new BufferedOutputStream(mControlSocket.getOutputStream());
		pasvSetup();
	}
	
	private void pasvSetup() throws IOException {
		String pasvInput = mControlInput.readLine();
		if (pasvInput.toUpperCase().equals(PASV)) { // mServerIp.getHostAddress()"127.0.0.1"
			String pasvResponse = "200 PORT " +  mServerIp.getHostAddress() + " " + getNextDataPort() + CRLF;
			mControlOutput.write(pasvResponse.getBytes());
			mControlOutput.flush();
			mPasvOK = true;
		} else {
			mPasvOK = false;
		}
	}
	
	private void executeCommand() throws IOException {
		if (mPasvOK) {
			mCommandString = mControlInput.readLine();
			if (mCommandString == null || mCommandString.length() <= 0) {
				return;
			}
			
			String[] cmdArray = mCommandString.split(" ");
			if (cmdArray[0].equals(DIR)) {
				executeDIR();
			} else if (cmdArray[0].equals(GET)) {
				executeGET();
			} else if (cmdArray[0].equals(PUT)) {
				executePUT();
			} else {
				mControlOutput.write((UC + CRLF).getBytes());
				mControlOutput.flush();
			}
		}
		
	}


	private void executeDIR() throws IOException {
		String[] cmdArray = mCommandString.split(" ");
		if (cmdArray.length == 1) {
			mControlOutput.write((DIR_OK + CRLF).getBytes());
			mControlOutput.flush();
		} else {
			mControlOutput.write((IA + CRLF).getBytes());
			mControlOutput.flush();
			return;
		}

		makeDataSocket();
		ArrayList<String> dirList = getDirList("", new File(SERVER_BASE_DIR));
		String dirListString = makeString(dirList);
		mDataOutput.write(dirListString.getBytes());
		mDataOutput.flush();
		
		closeDataSocket();
		mControlOutput.write((OK + CRLF).getBytes());
		mControlOutput.flush();
	}

	private int getNextDataPort() {
		if (mPreviousDataPort == -1) {
			mPreviousDataPort = mServerDataPort;
			return mServerDataPort;
		} else {
			int i = mPreviousDataPort + 1;
			if (i >= 65535) {
				i = 20000;
			}
			mPreviousDataPort = i;
			mServerDataPort = i;
			return mServerDataPort;
		}
	}


	private void executeGET() throws IOException {
		File fileToSend;
		String[] cmdArray = mCommandString.split(" ");
		if (cmdArray.length == 2) {
			fileToSend = new File(SERVER_BASE_DIR + "/" + cmdArray[1]);
			if (fileToSend.exists() && !fileToSend.isDirectory()) {
				mControlOutput.write((OK + CRLF).getBytes());
				mControlOutput.flush();
			} else {
				mControlOutput.write((FNF + CRLF).getBytes());
				mControlOutput.flush();
				return;
			}
		} else {
			mControlOutput.write((IA + CRLF).getBytes());
			mControlOutput.flush();
			return;
		}

		makeDataSocket();
		BufferedInputStream br = new BufferedInputStream(new FileInputStream(fileToSend));
		int i = br.read();
		while (i != -1) {
			mDataOutput.write(i);
			i = br.read();
		}
		br.close();
		mDataOutput.flush();
		closeDataSocket();
		mControlOutput.write((OK + CRLF).getBytes());
		mControlOutput.flush();
	}
	
	private void executePUT() throws IOException {
		File receivedFile;
		String[] cmdArray = mCommandString.split(" ");
		String filename;
		String filepath;
		if (cmdArray.length == 2) {
			filename = new File(cmdArray[1]).getName();
			filepath = SERVER_BASE_DIR + "/" + filename;
			receivedFile = new File(filepath);
			
			mControlOutput.write((OK + CRLF).getBytes());
			mControlOutput.flush();
		} else if (cmdArray.length == 3) {
			filename = new File(cmdArray[1]).getName();
			File dir = new File(SERVER_BASE_DIR + File.separator + cmdArray[2]);
			if (!dir.exists()) {
				dir.mkdir();
			}
			filepath = SERVER_BASE_DIR + "/" + cmdArray[2] + "/" + filename;
			receivedFile = new File(filepath);
			
			mControlOutput.write((OK + CRLF).getBytes());
			mControlOutput.flush();
		} else {
			mControlOutput.write((IA + CRLF).getBytes());
			mControlOutput.flush();
			return;
		}
		
		makeDataSocket();
		BufferedOutputStream bw = new BufferedOutputStream(new FileOutputStream(receivedFile));

		int i = mDataInput.read();
		while (i != -1) {
			bw.write(i);
			i = mDataInput.read();
		}
		bw.flush();
		bw.close();
		
		mControlOutput.write((OK + CRLF).getBytes());
		mControlOutput.flush();
		closeDataSocket();
	}


	private void makeDataSocket() throws IOException {
		mDataServerSocket = new ServerSocket(mServerDataPort);
		mDataSocket = mDataServerSocket.accept();
		mDataOutput = new BufferedOutputStream(mDataSocket.getOutputStream());
		mDataInput = new BufferedInputStream(mDataSocket.getInputStream());
	}
	
	
	
	
	
	
	
	
	
	
	
	// ------- HELPER METHODS ------------- //
	
	
	private String makeString(ArrayList<String> array) {
		if (array.size() <= 0) {
			return SERVER_EMPTY;
		}
		String result = "";
		for (int i = 0; i < array.size(); i++) {
			if (i == array.size() - 1) {
				result += array.get(i);
			} else {
				result += array.get(i) + "\n";
			}
		}
		
		return result;
	}

	private ArrayList<String> getDirList(String parentPath, File file) {
		ArrayList<String> filepathList = new ArrayList<String>();
		if (file.isDirectory()) {
			File[] contents = file.listFiles();
			for (File f : contents) {
				if (file.getName().equals("server-directory")) {
					filepathList.addAll(getDirList("", f));
				} else {
					filepathList.addAll(getDirList(parentPath + file.getName() + "/", f));
				}
			}
		} else {
			filepathList.add(parentPath + file.getName());
		}
		Collections.sort(filepathList);
		return filepathList;
	}
	
	private void closeControlSocket() throws IOException {
		mControlInput.close();
		mControlOutput.close();
		mControlSocket.close();
	}
	
	private void closeDataSocket() throws IOException {
		mDataInput.close();
		mDataOutput.close();
		mDataSocket.close();
		mDataServerSocket.close();
	}


	private void exit() {
		System.exit(1);
	}
	
	private void println(String s) {
		System.out.println(s);
	}
	
	
	
	// ------------ MAIN METHOD ------------------ //
	
	public static void main(String[] args) {
		new FTPServer().run(args);
	}

}
