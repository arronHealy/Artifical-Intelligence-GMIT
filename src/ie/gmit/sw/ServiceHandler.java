package ie.gmit.sw;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.encog.ml.MLRegression;
import org.encog.ml.data.versatile.VersatileMLDataSet;
import org.encog.ml.data.versatile.columns.ColumnDefinition;
import org.encog.ml.data.versatile.columns.ColumnType;
import org.encog.ml.data.versatile.sources.CSVDataSource;
import org.encog.ml.data.versatile.sources.VersatileDataSource;
import org.encog.ml.factory.MLMethodFactory;
import org.encog.ml.model.EncogModel;
import org.encog.util.csv.CSVFormat;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import ie.gmit.sw.ai.cloud.LogarithmicSpiralPlacer;
import ie.gmit.sw.ai.cloud.WeightedFont;
import ie.gmit.sw.ai.cloud.WordFrequency;

import ie.gmit.sw.ai.search.*;

/*
 * -------------------------------------------------------------------------------------------------------------------
 * PLEASE READ THE FOLLOWING CAREFULLY. MOST OF THE "ISSUES" STUDENTS HAVE WITH DEPLOYMENT ARISE FROM NOT READING
 * AND FOLLOWING THE INSTRUCTIONS BELOW.
 * -------------------------------------------------------------------------------------------------------------------
 *
 * To compile this servlet, open a command prompt in the web application directory and execute the following commands:
 *
 * Linux/Mac													Windows
 * ---------													---------	
 * cd WEB-INF/classes/											cd WEB-INF\classes\
 * javac -cp .:$TOMCAT_HOME/lib/* ie/gmit/sw/*.java				javac -cp .:%TOMCAT_HOME%/lib/* ie/gmit/sw/*.java
 * cd ../../													cd ..\..\
 * jar -cf wcloud.war *											jar -cf wcloud.war *
 * 
 * Drag and drop the file ngrams.war into the webapps directory of Tomcat to deploy the application. It will then be 
 * accessible from http://localhost:8080. The ignore words file at res/ignorewords.txt will be located using the
 * IGNORE_WORDS_FILE_LOCATION mapping in web.xml. This works perfectly, so don't change it unless you know what
 * you are doing...
 * 
*/

public class ServiceHandler extends HttpServlet {

	private File ignoreWords = null;
	private File fuzzyFile = null;
	private File trainingFile = null;

	private VersatileDataSource source;

	private VersatileMLDataSet data;

	private MLRegression bestMethod;

	private ExecutorService es = Executors.newFixedThreadPool(50);

	public void init() throws ServletException {
		ServletContext ctx = getServletContext(); // Get a handle on the application context

		// Reads the value from the <context-param> in web.xml
		ignoreWords = new File(getServletContext().getRealPath(File.separator), ctx.getInitParameter("IGNORE_WORDS_FILE_LOCATION"));
		fuzzyFile = new File(getServletContext().getRealPath(File.separator), ctx.getInitParameter("FUZZY_FILE_LOCATION"));
		trainingFile = new File(getServletContext().getRealPath(File.separator), ctx.getInitParameter("TRAINING_FILE_LOCATION"));

		// on initialization get a handle on the csv training data file
		source = new CSVDataSource(trainingFile, false, CSVFormat.DECIMAL_POINT);

		// pass csv file to data set parameter
		data = new VersatileMLDataSet(source);

		// set up columns for neural network params to be processed from data set
		data.defineSourceColumn("meta-score", 0, ColumnType.continuous);
		data.defineSourceColumn("title-score", 1, ColumnType.continuous);
		data.defineSourceColumn("headings-score", 2, ColumnType.continuous);
		data.defineSourceColumn("body-score", 3, ColumnType.continuous);

		// define the output layer data as nominal
		ColumnDefinition out = data.defineSourceColumn("page-score", 4, ColumnType.nominal);
		data.analyze();
		data.defineSingleOutputOthersInput(out);

		// Step 2: Create a Machine Learning Model
		EncogModel model = new EncogModel(data);
		model.selectMethod(data, MLMethodFactory.TYPE_FEEDFORWARD);
		data.normalize();

		// Step 3: Train the Model
		model.holdBackValidation(0.3, true, 1001);
		model.selectTrainingType(data);

		bestMethod = (MLRegression) model.crossvalidate(5, true);
	}

	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("text/html"); // Output the MIME type
		PrintWriter out = resp.getWriter(); // Write out text. We can write out binary too and change the MIME type...

		// Initialise some request varuables with the submitted form info. These are
		// local to this method and thread safe...
		String search = req.getParameter("searchOptions");
		String option = req.getParameter("cmbOptions"); // Change options to whatever you think adds value to your
														// assignment...
		String ai = req.getParameter("aiOptions");
		String s = req.getParameter("query");

		out.print("<html><head><title>Artificial Intelligence Assignment</title>");
		out.print("<link rel=\"stylesheet\" href=\"includes/style.css\">");

		out.print("</head>");
		out.print("<body>");
		out.print(
				"<div style=\"font-size:48pt; font-family:arial; color:#990000; font-weight:bold\">Web Opinion Visualiser</div>");

		out.print("<p><h2>Please read the following carefully</h2>");
		out.print("<p>The &quot;ignore words&quot; file is located at <font color=red><b>" + ignoreWords.getAbsolutePath()
				+ "</b></font> and is <b><u>" + ignoreWords.length() + "</u></b> bytes in size.");
		out.print(
				"You must place any additional files in the <b>res</b> directory and access them in the same way as the set of ignore words.");
		out.print(
				"<p>Place any additional JAR archives in the WEB-INF/lib directory. This will result in Tomcat adding the library of classes ");
		out.print(
				"to the CLASSPATH for the web application context. Please note that the JAR archives <b>jFuzzyLogic.jar</b>, <b>encog-core-3.4.jar</b> and ");
		out.print("<b>jsoup-1.12.1.jar</b> have already been added to the project.");

		out.print("<p><fieldset><legend><h3>Result</h3></legend>");

		WordFrequency[] words = new WeightedFont().getFontSizes(getWordFrequencyKeyValue(search, s, option, ai));
		Arrays.sort(words, Comparator.comparing(WordFrequency::getFrequency, Comparator.reverseOrder()));
		// Arrays.stream(words).forEach(System.out::println);

		// Spira Mirabilis
		LogarithmicSpiralPlacer placer = new LogarithmicSpiralPlacer(800, 600);
		for (WordFrequency word : words) {
			placer.place(word); // Place each word on the canvas starting with the largest
		}

		BufferedImage cloud = placer.getImage(); // Get a handle on the word cloud graphic
		out.print("<img src=\"data:image/png;base64," + encodeToString(cloud) + "\" alt=\"Word Cloud\">");

		out.print("</fieldset>");
		out.print("<P>Maybe output some search stats here, e.g. max search depth, effective branching factor.....<p>");
		out.print("<a href=\"./\">Return to Start Page</a>");
		out.print("</body>");
		out.print("</html>");
	}

	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}

	// A sample array of WordFrequency for demonstration purposes
	private WordFrequency[] getWordFrequencyKeyValue(String search, String query, String option, String aiOption) {

		Future<WordFrequency[]> wf;

		int size = 0;

		WordFrequency[] searchWords = null;

		// switch array size option passed to method
		switch (option) {
		case "10":
			size = 10;
			break;

		case "20":
			size = 20;
			break;

		case "30":
			size = 30;
			break;

		default:
			size = 20;
		}

		if (search.equals("Best First")) {
			// check if Fuzzy logic option selected
			if (aiOption.equals("Fuzzy Logic")) {
				// run best first search in thread with selected constructor for fuzzy logic scoring
				try {
					wf = es.submit(new BestFirstNodeParser(query, ignoreWords, fuzzyFile, size, aiOption));

					searchWords = wf.get();
					
					if(searchWords.length == 0 || searchWords[0] == null) {
						return getError();
					}
					
					return searchWords;
					
				} catch (IOException e1) {

				} catch (InterruptedException e) {

				} catch (ExecutionException e) {

				} // try/catch
			} else {
				// run best first search in thread with selected constructor for Neural network
				try {
					wf = es.submit(new BestFirstNodeParser(query, ignoreWords, data, bestMethod, size, aiOption));

					searchWords = wf.get();
					
					if(searchWords.length == 0 || searchWords[0] == null) {
						return getError();
					}

					return searchWords;
					
				} catch (IOException e1) {

				} catch (InterruptedException e) {

				} catch (ExecutionException e) {

				} // try/catch

			} // if/else
			
		} else {
			// check if Fuzzy logic option selected
			if (aiOption.equals("Fuzzy Logic")) {
				// run best first search in thread with selected constructor for fuzzy logic scoring
				try {
					wf = es.submit(new DepthLimitedDFSNodeParser(query, ignoreWords, fuzzyFile, size, aiOption));

					searchWords = wf.get();
					
					if(searchWords.length == 0 || searchWords[0] == null) {
						return getError();
					}

					return searchWords;
					
				} catch (IOException e1) {

				} catch (InterruptedException e) {

				} catch (ExecutionException e) {

				} // try/catch
			} else {
				// run best first search in thread with selected constructor for Neural network
				try {
					wf = es.submit(new DepthLimitedDFSNodeParser(query, ignoreWords, data, bestMethod, size, aiOption));

					searchWords = wf.get();
					
					if(searchWords.length == 0 || searchWords[0] == null) {
						return getError();
					}
					
					return searchWords;
					
				} catch (IOException e1) {

				} catch (InterruptedException e) {

				} catch (ExecutionException e) {

				} // try/catch

			} // if/else
		} // if

		// return error if method somehow gets down to this return statement
		return getError();
	}

	// error method added incase anything were to go wrong with threaded search and
	// save application from crashing
	private WordFrequency[] getError() {
		WordFrequency[] errorWords = new WordFrequency[5];

		errorWords[0] = new WordFrequency("An Error Occurred! Please try again...", 50);
		errorWords[1] = new WordFrequency("An Error Occurred! Please try again...", 30);
		errorWords[2] = new WordFrequency("An Error Occurred! Please try again...", 10);
		errorWords[3] = new WordFrequency("An Error Occurred! Please try again...", 5);
		errorWords[4] = new WordFrequency("An Error Occurred! Please try again...", 1);

		return errorWords;
	}

	private String encodeToString(BufferedImage image) {
		String s = null;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		try {
			ImageIO.write(image, "png", bos);
			byte[] bytes = bos.toByteArray();

			Base64.Encoder encoder = Base64.getEncoder();
			s = encoder.encodeToString(bytes);
			bos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return s;
	}

	private BufferedImage decodeToImage(String imageString) {
		BufferedImage image = null;
		byte[] bytes;
		try {
			Base64.Decoder decoder = Base64.getDecoder();
			bytes = decoder.decode(imageString);
			ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
			image = ImageIO.read(bis);
			bis.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return image;
	}
}