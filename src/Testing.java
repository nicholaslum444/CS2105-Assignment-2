import java.io.File;
import java.io.IOException;


public class Testing {

	public static void main(String[] args) {
		File f = new File("asd.txt");
		try {
			f.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
