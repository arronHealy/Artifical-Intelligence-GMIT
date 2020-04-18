package ie.gmit.sw.ai.search;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.encog.ml.MLRegression;
import org.encog.ml.data.MLData;
import org.encog.ml.data.versatile.NormalizationHelper;
import org.encog.ml.data.versatile.VersatileMLDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import ie.gmit.sw.ai.cloud.WordFrequency;
import net.sourceforge.jFuzzyLogic.FIS;

public class BestFirstNodeParser implements Callable<WordFrequency[]> {

	// visit 50 pages and stop
	private static final int MAX_PAGES = 50;

	// weight variables defined to score based on word frequency
	private static final int META_WEIGHT = 50;

	private static final int TITLE_WEIGHT = 20;

	private static final int HEADING1_WEIGHT = 10;

	private static final int PARAGRAPH_WEIGHT = 5;

	// other class variables
	private String url;

	private String[] searchTerm;

	private ExecutorService es;

	private Set<String> searchWords;

	private Map<Integer, WordFrequency> frequencyMap = new HashMap<>();

	private Set<String> closed = new TreeSet<>();

	private Set<String> ignoreWords;

	private File fuzzyFcl;

	private MLRegression bestMethod;

	private VersatileMLDataSet data;

	private int wordSize;

	private String aiMethodology;

	private Future<Set<String>> fileWords;

	private Queue<DocumentNode> queue = new PriorityQueue<>(Comparator.comparing(DocumentNode::getScore));

	// constructor for fuzzy logic search
	public BestFirstNodeParser(String term, File ignoreFile, File fuzzyFile, int size, String ai) throws IOException {
		this.es = Executors.newSingleThreadExecutor();
		this.searchTerm = term.toLowerCase().split(" ");
		this.url = "https://duckduckgo.com/html/?q=" + term;
		this.fuzzyFcl = fuzzyFile;
		this.wordSize = size;
		this.aiMethodology = ai;
		this.searchWords = new TreeSet<>();

		// parse ignore words file into set of string from threaded task
		this.fileWords = es.submit(new FileParser(ignoreFile));

		try {
			// get the set of ignore words
			this.ignoreWords = this.fileWords.get();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			// shutdown the executor service once ignore words have been parsed
			this.es.shutdown();
		}

		// add search term words to set to avoid returning search words as part of word
		// cloud
		for (String s : this.searchTerm) {
			this.searchWords.add(s);
		}
	}

	// constructor used for Neural Network search
	public BestFirstNodeParser(String term, File ignoreFile, VersatileMLDataSet data, MLRegression bestMethod, int size,
			String ai) throws IOException {
		this.es = Executors.newSingleThreadExecutor();
		this.searchTerm = term.toLowerCase().split(" ");
		this.url = "https://duckduckgo.com/html/?q=" + term;

		this.data = data;
		this.bestMethod = bestMethod;

		this.wordSize = size;
		this.aiMethodology = ai;
		this.searchWords = new TreeSet<>();

		// parse ignore words file into set of string from threaded task
		this.fileWords = es.submit(new FileParser(ignoreFile));

		try {
			// get the set of ignore words
			this.ignoreWords = this.fileWords.get();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			// shutdown the executor service once ignore words have been parsed
			this.es.shutdown();
		}

		// add search term words to set to avoid returning search words as part of word
		// cloud
		for (String s : this.searchTerm) {
			this.searchWords.add(s);
		}
	}

	private double getHeuristicScore(Document doc) {

		double score = 0;

		double metaScore = 0;
		double titleScore = 0;
		double headingsScore = 0;
		double bodyScore = 0;

		String title = "";

		// get meta tags for a given web page document
		Elements metaTags = doc.getElementsByTag("meta");
		
		// if neural network then score the meta tags content
		if (metaTags != null) {
			/*
			 * loop for all elements in meta tag
			 * extract the text content from meta tag
			 * score the meta content 
			 */
			for (Element e : metaTags) {
				String content = e.attr("content");

				metaScore += getFrequency(content.toLowerCase()) * META_WEIGHT;
			} // for
			
			// cap the meta score at 400
			if (metaScore > 400) {
				metaScore = 400;
			}
		}

		// score title for fuzzy scoring
		if (doc.title() != null) {
			title = doc.title();
			titleScore = TITLE_WEIGHT * getFrequency(doc.title().toLowerCase());

			// cap score value at 100
			if (titleScore > 100) {
				titleScore = 100;
			}
		}

		// get heading tags for a given web page document
		List<String> headings = doc.select("h1, h2, h3, h4, h5, h6").eachText();

		// score
		if (headings != null) {
			for (String heading : headings) {
				String h = heading;
				
				headingsScore += getFrequency(h.toLowerCase()) * HEADING1_WEIGHT;
			}

			if (headingsScore > 100) {
				headingsScore = 100;
			}
		}

		// switch value based on ai methodology chosen
		switch (aiMethodology) {
		case "Neural Network":
			// if web page body tag has text, get score
			if (doc.body().text() != null) {
				bodyScore = getFrequency(doc.body().text().toLowerCase()) * PARAGRAPH_WEIGHT;
			}

			// get neural network heuristic score
			score = getNeuralNetHeuristic(metaScore, titleScore, headingsScore, bodyScore);
			break;

		case "Fuzzy Logic":
			// if fuzzy logic score selected get fuzzy logic heuristic
			score = getFuzzyHeuristic(metaScore, titleScore, headingsScore);
			break;
		
		} // switch

		// if score greater than or equal to 40 then index the words for that web page
		if (score >= 40) {
			// check for body text and index words for a web page
			if (doc.body().hasText()) {
				indexWords(metaTags, title.toLowerCase(), headings, doc.body().text());
			} else {
				indexWords(metaTags, title.toLowerCase(), headings, null);
			}
			
		} // if

		return score;
	
	} // getHeuristicScore
	

	private void indexWords(Elements meta, String title, List<String> headings, String body) {

		String[] titleWords;

		if (meta != null) {

			/*
			 * Loop for all elements in the meta tags for a given page extract the content
			 * text in meta tags related to a given page into a string array and replace non
			 * alpha characters
			 */
			for (Element e : meta) {
				String[] content = e.attr("content").toLowerCase().replaceAll("[^a-zA-Z0-9\\s]+", "").split(" ");

				/*
				 * Loop for each string in content and check for new occurrence or update
				 * existing value, continue in the loop if string deemed irrelevant
				 */
				for (String s : content) {
					if (ignoreWords.contains(s) || searchWords.contains(s) || s.length() < 4 || s.matches("[0-9]+")) {
						continue;
					}

					if (frequencyMap.containsKey(s.hashCode())) {
						frequencyMap.put(s.hashCode(),
								new WordFrequency(s, 1 + frequencyMap.get(s.hashCode()).getFrequency()));
					}

					if (!frequencyMap.containsKey(s.hashCode()) && !ignoreWords.contains(s)) {
						frequencyMap.put(s.hashCode(), new WordFrequency(s, 1));
					}

				} // for

			} // for

		} // if

		/*
		 * check if title of web page has content
		 */
		if (title != null && title.length() > 0) {
			// convert string to array and replace non alpha characters
			titleWords = title.replaceAll("[^a-zA-Z0-9\\s]+", "").split(" ");

			/*
			 * Loop for each string in title and check for new occurrence or update existing
			 * value, continue in the loop if string deemed irrelevant
			 */
			for (String s : titleWords) {
				if (ignoreWords.contains(s) || searchWords.contains(s) || s.length() < 4 || s.matches("[0-9]+")) {
					continue;
				}

				if (frequencyMap.containsKey(s.hashCode())) {
					frequencyMap.put(s.hashCode(),
							new WordFrequency(s, 1 + frequencyMap.get(s.hashCode()).getFrequency()));
				}

				if (!frequencyMap.containsKey(s.hashCode()) && !ignoreWords.contains(s)) {
					frequencyMap.put(s.hashCode(), new WordFrequency(s, 1));
				}

			} // for

		} // if

		// check list of headings values has String data 
		if (headings != null && headings.size() > 0) {

			/*
			 * Loop for each string in headings list and check for new occurrence or update
			 * existing value, continue in the loop if string deemed irrelevant
			 */
			for (String s : headings) {

				/*
				 * convert string to string array and loop for each word in a heading tag 
				 * check for new occurrence or update existing value
				 * continue in the loop if string deemed irrelevant
				 */
				for (String word : s.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]+", "").split(" ")) {
					if (ignoreWords.contains(word) || searchWords.contains(word) || word.length() < 3
							|| word.matches("[0-9]+")) {
						continue;
					}

					if (frequencyMap.containsKey(word.hashCode())) {
						frequencyMap.put(word.hashCode(),
								new WordFrequency(word, 1 + frequencyMap.get(word.hashCode()).getFrequency()));
					}

					if (!frequencyMap.containsKey(word.hashCode()) && !ignoreWords.contains(word)) {
						frequencyMap.put(word.hashCode(), new WordFrequency(word, 1));
					}
					
				} // for
				
			} // for
			
		} // if

		// check that body text is not empty
		if (body != null) {
			
			/*
			 * convert string to string array and loop for each word in a heading tag 
			 * check for new occurrence or update existing value
			 * continue in the loop if string deemed irrelevant
			 */
			for (String s : body.replaceAll("[^a-zA-Z0-9\\s]+", "").toLowerCase().split(" ")) {
				
				if (ignoreWords.contains(s) || searchWords.contains(s) || s.length() < 4 || s.matches("[0-9]+")) {
					continue;
				}

				if (frequencyMap.containsKey(s.hashCode())) {
					frequencyMap.put(s.hashCode(),
							new WordFrequency(s, 1 + frequencyMap.get(s.hashCode()).getFrequency()));
				}

				if (!frequencyMap.containsKey(s.hashCode()) && !ignoreWords.contains(s)) {
					frequencyMap.put(s.hashCode(), new WordFrequency(s, 1));
				}
				
			} // for
			
		} // if
		
	} // indexWords

	private int getFrequency(String s) {
		int frequency = 0;

		// convert string to array and replace all non alpha characters
		String[] words = s.replaceAll("[^a-zA-Z0-9\\s]+", "").split(" ");

		// loop for each word and check for search term, increase frequency for each occurrence 
		for (String word : words) {
			if (searchWords.contains(word)) {
				frequency++;
			}
			
		}// for

		return frequency;
		
	} // getFrequency

	
	private double getNeuralNetHeuristic(double meta, double title, double headings, double body) {
		double score = 0;

		NormalizationHelper helper = data.getNormHelper();
		String[] line = new String[4];
		MLData input = helper.allocateInputVector();

		// assign scores as string to array
		line[0] = Double.toString(meta);
		line[1] = Double.toString(title);
		line[2] = Double.toString(headings);
		line[3] = Double.toString(body);

		helper.normalizeInputVector(line, input.getData(), false);
		MLData output = bestMethod.compute(input); // Ask the network to classify the input
		String actual = helper.denormalizeOutputVectorToString(output)[0]; // The actual result…

		// assign score to web page based on nominal value predicted
		switch (actual) {
		case "irrelevant":
			score = 0;
			break;

		case "low":
			score = 20;
			break;

		case "average":
			score = 40;
			break;

		case "high":
			score = 80;
			break;

		default:
			score = 0;
		}

		return score;
		
	} // getNeuralNetHeuristicScore
	

	private double getFuzzyHeuristic(double meta, double title, double headings) {
		// load the fuzzy heuristic file
		FIS fis = FIS.load(fuzzyFcl.getAbsolutePath(), true);

		// Set inputs
		fis.setVariable("meta", meta);
		fis.setVariable("title", title);
		fis.setVariable("headings", headings);

		// Evaluate
		fis.evaluate();

		return fis.getVariable("score").getValue();
	
	} // getFuzzyHeuristic 

	
	@Override
	public WordFrequency[] call() throws Exception {
		// get web page from initial search 
		Document doc = Jsoup.connect(url).get();
		double score = getHeuristicScore(doc);

		// add url to visited list 
		closed.add(url);
		
		queue.offer(new DocumentNode(doc, score));

		while (!queue.isEmpty() && closed.size() <= MAX_PAGES) {
			DocumentNode node = queue.poll();
			doc = node.getDoc();

			// get the hyper links for the web page you are on
			Elements edges = doc.select("a[href]");

			// loop for all links and score for each page
			for (Element e : edges) {
				String link = e.absUrl("href");

				if (link != null && closed.size() <= MAX_PAGES && !closed.contains(link)) {
					try {
						closed.add(link);
						Document child = Jsoup.connect(link).get();
						score = getHeuristicScore(child);
						queue.offer(new DocumentNode(child, score));
					} catch (IOException e1) {
						continue;
					}
				}

			}
		}

		int count = 0;
		WordFrequency[] words = new WordFrequency[frequencyMap.size()];

		// loop for all keys in frequency map
		for (Integer i : frequencyMap.keySet()) {
			// add to word frequency array
			words[count] = frequencyMap.get(i);
			count++;
		}

		// sort Word frequency array in descending order
		Collections.sort(Arrays.asList(words), new Comparator<WordFrequency>() {

			@Override
			public int compare(WordFrequency o1, WordFrequency o2) {
				// TODO Auto-generated method stub
				return -Integer.compare(o1.getFrequency(), o2.getFrequency());
			}

		});

		// return word frequency array of based on size passed into class  
		return Arrays.copyOfRange(words, 0, wordSize);
		
	} // call

}
