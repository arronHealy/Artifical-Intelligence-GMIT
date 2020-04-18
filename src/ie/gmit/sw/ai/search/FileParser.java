package ie.gmit.sw.ai.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;

public class FileParser implements Callable<Set<String>> {
	
	private File filePath;
	
	private Set<String> words;
	
	public FileParser(File path) {
		// TODO Auto-generated constructor stub
		this.filePath = path;
		this.words = new TreeSet<>();
	}
	
	@Override
	public Set<String> call() throws Exception {
		// TODO Auto-generated method stub
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)));
			String line = null;

			while ((line = br.readLine()) != null) {
				words.add(line.trim());
			}
			//System.out.println("set size -> " + ignoreWords.size());
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
		return words;
	}

}
