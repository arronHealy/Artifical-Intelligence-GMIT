<%@ include file="includes/header.jsp" %>

<div class="animated bounceInDown" style="font-size:48pt; font-family:arial; color:#990000; font-weight:bold">Web Opinion Visualiser</div>

</p>&nbsp;</p>&nbsp;</p>

<table width="600" cellspacing="0" cellpadding="7" border="0">
	<tr>
		<td valign="top">

			<form bgcolor="white" method="POST" action="doProcess">
				<fieldset>
					<legend><h3>Specify Details</h3></legend>
				
					<b>Select Option:</b>
					<p>
					You should make the most of the ability to configure a search with different algorithms and heuristics. You can employ fuzzy logic or machine 
					learning if you like. 
					
					<br>
					
					The following drop down list allows you to select the number of words you would like to return from the search
					</p>
					
					<p>
					<select name="cmbOptions">
						<option selected>10</option>
						<option>20</option>
						<option>30</option>
					</select>
					</p>
					
					<p>
					<br>
					
					The following drop down list allows you to choose the type of search you would like to run. You can choose between a Best First Search or Depth limited Depth first search.
					</p>
					
					<p>
					<select name="searchOptions">
						<option selected>Best First</option>
						<option>DDFS</option>
					</select>
					</p>
					
					<p>
					<br>
					
					Please select from the list below the AI methodology you wish to carry out on the search.
					</p>
					
					<p>
					<select name="aiOptions">
						<option selected>Fuzzy Logic</option>
						<option>Neural Network</option>
					</select>
					</p>
					
					Only use the following JARs with your application. You can assume that they have already been added to the Tomcat CLASSPATH:
					<p>
					<ol>
						<li><a href="https://jsoup.org">JSoup</a>
						<li><a href="http://jfuzzylogic.sourceforge.net/html/index.html">JFuzzyLogic</a>
						<li><a href="https://github.com/jeffheaton/encog-java-core">Encog</a>
					</ol>	
							
			
					<p/>

					<b>Enter Text :</b><br>
					<input name="query" size="100">	
					<p/>

					<center><input type="submit" value="Search & Visualise!"></center>
				</fieldset>							
			</form>	

		</td>
	</tr>
</table>
<%@ include file="includes/footer.jsp" %>

