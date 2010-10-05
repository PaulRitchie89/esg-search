/*******************************************************************************
 * Copyright (c) 2010 Earth System Grid Federation
 * ALL RIGHTS RESERVED. 
 * U.S. Government sponsorship acknowledged.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of the <ORGANIZATION> nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package esg.search.query.impl.solr;

import java.io.IOException;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import esg.search.core.Record;
import esg.search.core.RecordSerializer;
import esg.search.core.RecordSerializerSolrImpl;
import esg.search.query.api.Facet;
import esg.search.query.api.SearchInput;
import esg.search.query.api.SearchOutput;
import esg.search.utils.XmlParser;

/**
 * Utility class to parse Solr XML files into information java objects.
 */
public class SolrXmlParser {
	
	/**
	 * The underlying XML parser.
	 */
	private final XmlParser xmlParser;
	
	/**
	 * The record-XML serializer/deserializer.
	 */
	private final RecordSerializer serializer = new RecordSerializerSolrImpl();
	
	/**
	 * Constructor instantiates the XML parser.
	 */
	public SolrXmlParser() {
		// no validation since Solr documents don't have a schema
		xmlParser = new XmlParser(false);
	}
	
	/**
	 * Method to parse a Solr XML output document into record and facet objects.
	 * Either the results or the facets section of the document can be processed, or both.
	 * 
	 * @param xml
	 * @param input
	 * @param parseResults
	 * @param parseFacets
	 * @return
	 */
	public SearchOutput parse(final String xml, final SearchInput input, final boolean parseResults, final boolean parseFacets) throws IOException, JDOMException {
		
		final SearchOutput output = new SearchOutputImpl();
		final Document doc = xmlParser.parseString(xml);
		final Element root = doc.getRootElement();

		// parse results
		if (parseResults) {
			parseResults(root, output);
		}
		
		// parse facets
		if (parseFacets) {
			parseFacets(root, input, output);
		}
		
		return output;
		
	}
	
	/**
	 * Private method to retrieve the list of facets and counts from the <lst name="facet_counts"> snippet.
	 * @param xml
	 * @return
	 * @throws IOException
	 * @throws JDOMException
	 */
	private void parseFacets(final Element root, final SearchInput input, final SearchOutput output) throws IOException, JDOMException {
				
		/* 
		<lst name="facet_counts">
		 	<lst name="facet_queries"/>
		 		<lst name="facet_fields">
		  			<lst name="project">
						<int name="AIRS">4</int>
						<int name="IPCC5">3</int>
						<int name="MLS">3</int>
		  		</lst>
		 	</lst>
		 	<lst name="facet_dates"/>
		</lst>
		*/
		for (final Object lstEl : root.getChildren(SolrXmlPars.ELEMENT_LST)) {
			final Element _lstEl = (Element)lstEl;
			if (_lstEl.getAttribute(SolrXmlPars.ATTRIBUTE_NAME).getValue().equals(SolrXmlPars.ELEMENT_FACET_COUNTS)) {
				for (final Object ffEl : _lstEl.getChildren("lst")) {
					final Element _ffEl = (Element)ffEl;
					if (_ffEl.getAttributeValue(SolrXmlPars.ATTRIBUTE_NAME).equals(SolrXmlPars.ELEMENT_FACET_FIELDS)) {
					
						for (final Object flstEl : _ffEl.getChildren(SolrXmlPars.ELEMENT_LST)) {
							final Element _flstEl = (Element)flstEl;
							final String facetName = _flstEl.getAttributeValue(SolrXmlPars.ATTRIBUTE_NAME);
							final Facet facet = new FacetImpl(facetName, facetName, "");
														
							// loop over facet options
							for (final Object intEl : _flstEl.getChildren(SolrXmlPars.ELEMENT_INT)) {
								final Element _intEl = (Element)intEl;
								final String subFacetName = _intEl.getAttributeValue(SolrXmlPars.ATTRIBUTE_NAME);
								final int subFacetCounts = Integer.parseInt(_intEl.getText());
								if (subFacetCounts>0) {
									
									// facet not constrained -> retrieve all options from XML response
									if (!input.getConstraints().containsKey(facetName)
										// constrained facet -> retrieve only selected option
										|| input.getConstraints().get(facetName).get(0).equals(subFacetName)) {
										final Facet subFacet = new FacetImpl(subFacetName, subFacetName, "");
										subFacet.setCounts(subFacetCounts);
										facet.addSubFacet( subFacet );
									}
								}
							}							
							
							output.addFacet(facetName, facet);
						}
					}	
				}
			}
		}

	}
	
	/**
	 * Private method to retrieve the search results from the <result name="response" numFound="..." start="0"> snippet.
	 * 
	 * @param xml
	 * @return
	 * @throws IOException
	 * @throws JDOMException
	 */
	private void parseResults(final Element root, final SearchOutput output) throws IOException, JDOMException {
		
		for (final Object resultEl : root.getChildren(SolrXmlPars.ELEMENT_RESULT)) {
			final Element _resultEl = (Element)resultEl;
			if (_resultEl.getAttributeValue(SolrXmlPars.ATTRIBUTE_NAME).equals(SolrXmlPars.ATTRIBUTE_VALUE_RESPONSE)) {
				
				final int numFound = Integer.parseInt(_resultEl.getAttributeValue(SolrXmlPars.ATTRIBUTE_NUM_FOUND));
				output.setCounts(numFound);
				final int start = Integer.parseInt(_resultEl.getAttributeValue(SolrXmlPars.ATTRIBUTE_START));
				output.setOffset(start);

				for (final Object docEl : _resultEl.getChildren(SolrXmlPars.ELEMENT_DOC)) {
					final Element _docEl = (Element)docEl;
					final Record record = serializer.deserialize(_docEl);
					output.addResult(record);
				}
				
			}
			
		}
		
	}
	
}
