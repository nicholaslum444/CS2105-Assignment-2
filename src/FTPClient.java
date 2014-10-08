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
	
	private static final String NEWLINE = "\r\n";
	private static final String CLIENT_BASE_DIR = "client-directory/";
	private static final String SERVER_BASE_DIR = "server-directory/";
	private static final String DIRECTORY_LISTING_FILENAME = CLIENT_BASE_DIR + "directory_listing";
	private static final String DIR = "DIR";
	private static final String GET = "GET";
	private static final String PUT = "PUT";
	private static final String PASV = "PASV" + NEWLINE;
	
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

	public FTPClient() {

	}

	public void run(String[] args) {
		mArgs = args;
		try {
			settleArgs();
			println("args done");
			setUpControlSocket();
			println("control socket done");
			executeCommand();
			println("executed");
			closeControlSocket();

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void settleArgs() {
		if (mArgs.length < 3) {
			println("not enough args at all");
			exit();
		}
		
		mServerIpString = mArgs[0];
		mServerControlPort = Integer.parseInt(mArgs[1]);
		mCommand = mArgs[2].toUpperCase();
		
		if (mCommand.equals(GET)) {
			if (mArgs.length < 4) {
				println("get not enough args");
				exit();
			}
			mServerPathOfFileToGet = mArgs[3];
			
		} else if (mCommand.equals(PUT)) {
			if (mArgs.length < 4) {
				println("put not enough args");
				exit();
			}
			
			mClientPathOfFileToPut = mArgs[3];
			
			if (mArgs.length == 4) {
				mServerPathOfFileToPut = null;
			}
			
			if (mArgs.length == 5) {
				mServerPathOfFileToPut = mArgs[4];
			}
			
		} else if (mCommand.equals(DIR)) {
			// do nothing else
		} else {
			println("invalid command");
			println(mCommand);
			exit();
		}
	}

	private void setUpControlSocket() throws IOException {
		// open control socket
		mServerIp = InetAddress.getByName(mServerIpString);
		openControlSocket();
		mControlOutput.write(PASV.getBytes());
		mControlOutput.flush();
		println("sent pasv");
		String responseString = mControlInput.readLine();
		println("receive response");
		println(responseString);
		if (!responseOK(responseString)) {
			exit();
		}
		println("response ok");
		String[] responseArray = responseString.split(" ");
		//mServerIp = InetAddress.getByName(responseArray[2]);
		mServerDataPort = Integer.parseInt(responseArray[3]);
		println("server data port = " + responseArray[3]);
	}

	private void executeCommand() throws IOException {
		println("executing");
		if (mCommand.equals(DIR)) {
			println("dir");
			executeDIR();
		} else if (mCommand.equals(GET)) {
			println("get");
			executeGET();
		} else if (mCommand.equals(PUT)) {
			println("put");
			executePUT();
		} else {
			println("invalid command");
			exit();
		}
	}

	private void executeDIR() throws IOException {
		println("running dir");
		// send cmd, open data socket if 200.
		String fullCmd = mCommand + NEWLINE;
		mControlOutput.write(fullCmd.getBytes());
		mControlOutput.flush();
		println(fullCmd);
		println("cmd sent");
		String responseString = mControlInput.readLine();
		println("response received");
		if (!responseOK(responseString)) {
			exit();
		}
		println("response ok");
		openDataSocket();
		println("data socket opened");
		
		// get input data and write to file.
		BufferedWriter bw = new BufferedWriter(new FileWriter(DIRECTORY_LISTING_FILENAME));
		int i = mDataInput.read();
		println("got input from data socket");
		while (i != -1) {
			bw.write(i);
			i = mDataInput.read();
		}
		bw.close();
		println("all data read");
	}

	private void executeGET() throws IOException {
		println("running get");
		// send cmd, open data socket if 200.
		String fullCmd = mCommand + " " + mServerPathOfFileToGet + NEWLINE;
		mControlOutput.write(fullCmd.getBytes());
		mControlOutput.flush();
		println(fullCmd);
		println("cmd sent");
		String responseString = mControlInput.readLine();
		println("response received");
		if (!responseOK(responseString)) {
			exit();
		}
		println("response ok");
		openDataSocket();
		println("data socket opened");
		
		// if get from server/s-d/asd then put in client/c-d/asd
		String localFilename = new File(mServerPathOfFileToGet).getName();
		String localFilepath = CLIENT_BASE_DIR + localFilename;
		BufferedWriter bw = new BufferedWriter(new FileWriter(localFilepath));
		int i = mDataInput.read();
		while (i != -1) {
			bw.write(i);
			i = mDataInput.read();
		}
		bw.close();
	}

	private void executePUT() throws IOException {
		// send cmd, open data socket if 200.
		String fullCmd = mCommand + " " + mClientPathOfFileToPut;
		if (mServerPathOfFileToPut != null) {
			fullCmd += " " + mServerPathOfFileToPut;
		}
		fullCmd += NEWLINE;
		mControlOutput.write(fullCmd.getBytes());
		mControlOutput.flush();
		println(fullCmd);
		println("cmd sent");
		String responseString = mControlInput.readLine();
		println("response received");
		println(responseString);
		if (!responseOK(responseString)) {
			exit();
		}
		println("response ok");
		openDataSocket();
		println("data socket opened");
		
		try {
			BufferedInputStream br = new BufferedInputStream(new FileInputStream(CLIENT_BASE_DIR + mClientPathOfFileToPut));
			int i = br.read();
			while (i != -1) {
				mDataOutput.write(i);
				i = br.read();
			}
			br.close();
			mDataOutput.write(NEWLINE.getBytes());
			mDataOutput.flush();
			closeDataSocket();
			println("file sent");
			println("waiting for reponse");
			
			String putResponseString = mControlInput.readLine();
			println("got response");
			println(putResponseString);
			if (!responseOK(putResponseString)) {
				exit();
			}
			
			
		} catch (FileNotFoundException e) {
			log("FILE NOT FOUND");
		}
	}

	

	
	
	
	
	
	// ------- HELPER METHODS ------------- //
	
	private boolean responseOK(String responseString) throws IOException {
		String pasvResponse = responseString;
		if (pasvResponse == null || pasvResponse.length() == 0) {
			println("empty response");
		} else {
			String[] responseCodes = pasvResponse.split(" ");
			if (!responseCodes[0].equals("200")) {
				println("not 200 response");
				println(pasvResponse);
			}
		}
		log(responseString+"\n"); // the original diff file needs the \n
		return true;
	}

	private void openControlSocket() throws IOException {
		println("control port "+ mServerControlPort);
		mControlSocket = new Socket(mServerIp, mServerControlPort);
		mControlOutput = new BufferedOutputStream (new DataOutputStream(mControlSocket.getOutputStream()));
		mControlInput = new BufferedReader(new InputStreamReader(mControlSocket.getInputStream()));
	}

	private void openDataSocket() throws IOException {
		println("data port "+ mServerDataPort);
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

	private void exit() {
		System.exit(1);
	}
	
	private void println(String s) {
		System.out.println(s);
	}
	
	
	
	
	
	
	
	// ------------ MAIN METHOD ------------------ //
	
	public static void main(String[] args) {
		new FTPClient().run(args);
	}

}
