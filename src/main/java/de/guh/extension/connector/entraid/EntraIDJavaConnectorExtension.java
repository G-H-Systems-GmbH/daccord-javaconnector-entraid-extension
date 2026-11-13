package de.guh.extension.connector.entraid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;
import org.dom4j.Element;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.guh.connector.core.ConnectorRequest;
import de.guh.connector.core.ConnectorResponse;
import de.guh.connector.core.JavaConnectorExtension;
import de.guh.connector.core.NodeCollection;
import de.guh.connector.core.RelationCollection;
import de.guh.connector.core.connection.ConnectionManager;
import de.guh.connector.core.connection.ConnectionTestResult;
import de.guh.plugin.msgraph.MSGraphRequest;
import de.guh.plugin.msgraph.MSGraphResponse;
import de.guh.plugin.msgraph.MSGraphSession;
import de.guh.plugin.rules.RuleEvaluableString;
import de.guh.plugin.xml.XML;

public class EntraIDJavaConnectorExtension implements JavaConnectorExtension {

	/**
	 * Check connection to Entra ID
	 * Tests authentication and connection using ConnectionManager
	 * 
	 * @param thislog    Logger instance
	 * @param coRequest  Request with connection parameters
	 * @param coResponse Response object (not used for connection test)
	 * @return XML with connection test result
	 * @throws Exception if connection test fails
	 */
	@Override
	public XML checkConnection(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) {
		thislog.info("checkConnection()...[running]");

		// Extract parameters from ConnectorRequest
		String authority = coRequest.getParameter("gp_param1");
		String tenant = coRequest.getParameter("gp_param2");
		String tenantname = coRequest.getParameter("gp_param3");
		String clientid = coRequest.getParameter("gp_param4");
		String scope = coRequest.getParameter("gp_param5");
		String connecteddomain = coRequest.getParameter("gp_param6");
		String proxyhost = coRequest.getParameter("gp_param7");
		String proxyport = coRequest.getParameter("gp_param8");
		String sslproxystring = coRequest.getParameter("gp_param9");
		String proxyusername = coRequest.getParameter("gp_param10");
		String secret = coRequest.getParameter("gp_pwparam1"); // Already decrypted by ConnectorRequest!
		String proxypassword = coRequest.getParameter("gp_pwparam2"); // Already decrypted!

		// Parse boolean
		boolean sslproxy = false;
		if (sslproxystring != null && sslproxystring.equalsIgnoreCase("true")) {
			sslproxy = true;
		}

		// Log parameters
		thislog.info("checkConnection()...[authority]: " + authority);
		thislog.info("checkConnection()...[tenant]: " + tenant);
		thislog.info("checkConnection()...[tenantname]: " + tenantname);
		thislog.info("checkConnection()...[clientid]: " + clientid);
		thislog.info("checkConnection()...[scope]: " + scope);
		thislog.info("checkConnection()...[connecteddomain]: " + connecteddomain);
		thislog.info("checkConnection()...[proxyhost]: " + proxyhost);
		thislog.info("checkConnection()...[proxyport]: " + proxyport);
		thislog.info("checkConnection()...[sslproxy]: " + sslproxy);
		thislog.info("checkConnection()...[proxyusername]: " + proxyusername);
		thislog.info("checkConnection()...[secret]: successfully read");
		thislog.info("checkConnection()...[proxypassword]: successfully read");

		// Build connection parameters for ConnectionManager
		Map<String, String> connectionParams = extractConnectionParams(coRequest);

		// Test via ConnectionManager
		thislog.info("checkConnection()...testing connection via ConnectionManager");
		ConnectionTestResult result =
				ConnectionManager.getInstance().testConnection(connectionParams, MSGraphManagedConnection.class, new MSGraphConnectionFactory());

		// Build XML response
		XML xmlConnection = new XML("connection");

		if (result.isSuccess()) {
			thislog.info("checkConnection()...[SUCCESS] Successfully connected to Entra ID");
			xmlConnection.appendNode("result", "SUCCESS");
			xmlConnection.appendNode("resultcode", "success");
			xmlConnection.appendNode("connected", "true");
			xmlConnection.appendNode("duration", String.valueOf(result.getDuration().toMillis()));
		} else {
			thislog.error("checkConnection()...[ERROR] Unable to connect to Entra ID: " + result.getMessage());
			xmlConnection.appendNode("result", "ERROR");
			xmlConnection.appendNode("resultcode", result.getMessage());
			xmlConnection.appendNode("connected", "false");
			xmlConnection.appendNode("duration", String.valueOf(result.getDuration().toMillis()));

			// Add exception details if available
			if (result.getException() != null) {
				xmlConnection.appendNode("error", result.getException().getMessage());
			}
		}

		return xmlConnection;
	}

	/**
	 * Get preview data from Entra ID
	 * Returns XML with limited data set for frontend preview
	 * NOW WITH SUBQUERY SUPPORT!
	 * 
	 * @param thislog    Logger instance
	 * @param coRequest  Request with connection and preview parameters
	 * @param coResponse Response object (not used for preview)
	 * @return XML with preview data (max 10 entries)
	 * @throws Exception if preview fails
	 */
	@Override
	public XML getNodePreview(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {

		thislog.info("getNodePreview()...[running]");

		// Extract connection parameters (same as getNodeData)
		String authority = coRequest.getParameter("gp_param1");
		String tenant = coRequest.getParameter("gp_param2");
		String tenantname = coRequest.getParameter("gp_param3");
		String clientid = coRequest.getParameter("gp_param4");
		String scope = coRequest.getParameter("gp_param5");
		String connecteddomain = coRequest.getParameter("gp_param6");
		String proxyhost = coRequest.getParameter("gp_param7");
		String proxyport = coRequest.getParameter("gp_param8");
		String sslproxystring = coRequest.getParameter("gp_param9");
		boolean sslproxy = false;
		if (sslproxystring != null && sslproxystring.equalsIgnoreCase("true"))
			sslproxy = true;
		String proxyusername = coRequest.getParameter("gp_param10");
		String secret = coRequest.getParameter("gp_pwparam1");
		String proxypassword = coRequest.getParameter("gp_pwparam2");

		// Log connection parameters
		thislog.info("getNodePreview()...[authority]: " + authority);
		thislog.info("getNodePreview()...[tenant]: " + tenant);
		thislog.info("getNodePreview()...[tenantname]: " + tenantname);
		thislog.info("getNodePreview()...[clientid]: " + clientid);
		thislog.info("getNodePreview()...[scope]: " + scope);
		thislog.info("getNodePreview()...[connecteddomain]: " + connecteddomain);
		thislog.info("getNodePreview()...[proxyhost]: " + proxyhost);
		thislog.info("getNodePreview()...[proxyport]: " + proxyport);
		thislog.info("getNodePreview()...[sslproxy]: " + sslproxy);
		thislog.info("getNodePreview()...[proxyusername]: " + proxyusername);
		thislog.info("getNodePreview()...[secret]: successfully read");
		thislog.info("getNodePreview()...[proxypassword]: successfully read");

		// Extract node collection parameters (same as getNodeData)
		String objecttype = coRequest.getParameter("cp_param1");
		String resourcepath = coRequest.getParameter("cp_param2");
		String uniqueattribute = coRequest.getParameter("cp_param3");
		String select = coRequest.getParameter("cp_param4");
		String filter = coRequest.getParameter("cp_param5");
		String expand = coRequest.getParameter("cp_param6");
		String resourcepathsubquery = coRequest.getParameter("cp_param7");
		String resourcepathsubqueryselect = coRequest.getParameter("cp_param8");
		String resourcepathsubqueryfilter = coRequest.getParameter("cp_param9");
		String subqueryattributeprefix = coRequest.getParameter("cp_param10");

		// Log collection parameters
		thislog.info("getNodePreview()...[objecttype]: " + objecttype);
		thislog.info("getNodePreview()...[resourcepath]: " + resourcepath);
		thislog.info("getNodePreview()...[uniqueattribute]: " + uniqueattribute);
		thislog.info("getNodePreview()...[select]: " + select);
		thislog.info("getNodePreview()...[filter]: " + filter);
		thislog.info("getNodePreview()...[expand]: " + expand);
		thislog.info("getNodePreview()...[resourcepathsubquery]: " + resourcepathsubquery);
		thislog.info("getNodePreview()...[resourcepathsubqueryselect]: " + resourcepathsubqueryselect);
		thislog.info("getNodePreview()...[resourcepathsubqueryfilter]: " + resourcepathsubqueryfilter);
		thislog.info("getNodePreview()...[subqueryattributeprefix]: " + subqueryattributeprefix);

		// Get session from pool (same as getNodeData)
		MSGraphSession session = getOrCreateSession(coRequest);

		if (!session.isReady()) {
			thislog.error("getNodePreview()...[ERROR] Unable to connect to Entra ID");

			// Return error XML
			XML xmlError = new XML("entries");
			xmlError.appendNode("error", "Unable to connect to Entra ID: " + session.getStatusmessage());
			return xmlError;
		}

		// Build request (same as getNodeData)
		MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_GET, resourcepath);

		if (filter != null && !filter.equals("")) {
			request.addParameter("$filter", filter);
		}

		if (expand != null && !expand.equals("")) {
			request.addParameter("$expand", expand);
		}

		String previewsendselect = coRequest.getParameter("cp_param11");
		if (select != null && !select.equals("")
				&& (previewsendselect == null || previewsendselect.isBlank() || !"false".equalsIgnoreCase(previewsendselect.trim()))) {
			request.addParameter("$select", buildGraphSelect(select));
		}

		// PREVIEW SPECIFIC: Limit to 10 results
		request.addParameter("$top", "10");

		thislog.info("getNodePreview()...Sending request (limited to 10 results)");
		MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

		// CHANGED: Pass session and coRequest for subquery support
		XML xmlPreview = renderNodePreviewResponse(thislog, session, response, coRequest, objecttype, uniqueattribute, select);

		thislog.info("getNodePreview()...[SUCCESS] Preview data retrieved");

		return xmlPreview;
	}

	/**
	 * Renders MSGraph response data as XML for preview
	 * Similar to renderNodeResponse but creates XML instead of CSV
	 * NOW WITH SUBQUERY SUPPORT!
	 * 
	 * @param thislog         Logger instance
	 * @param session         MSGraph session (needed for subquery)
	 * @param response        MSGraph API response
	 * @param coRequest       Connector request (needed for subquery parameters)
	 * @param objecttype      Type of object (user, group, etc.)
	 * @param uniqueattribute Unique identifier attribute name
	 * @param select          Selected attributes (comma-separated)
	 * @return XML with preview entries (max 10)
	 */
	private XML renderNodePreviewResponse(Logger thislog, MSGraphSession session, MSGraphResponse response, ConnectorRequest coRequest, String objecttype,
			String uniqueattribute, String select) {

		thislog.debug("renderNodePreviewResponse()...objecttype: " + objecttype);

		XML xmlEntries = new XML("entries");

		// Get XML data from response (same as in renderNodeResponse!)
		XML responsexmlfull = response.getDataAsXML();

		if (responsexmlfull == null || !responsexmlfull.hasNodes()) {
			thislog.info("renderNodePreviewResponse()...no data returned");
			return xmlEntries;
		}

		// Count elements
		long elementCount = responsexmlfull.getElementStream().count();
		thislog.info("renderNodePreviewResponse()...processing " + elementCount +
			" entries");

		// Reset stream (count() consumes it)
		responsexmlfull = response.getDataAsXML();

		// Get subquery parameters (same as in renderNodeResponse)
		String resourcepathsubquery = coRequest.getParameter("cp_param7");
		String resourcepathsubqueryselect = coRequest.getParameter("cp_param8");
		String resourcepathsubqueryfilter = coRequest.getParameter("cp_param9");
		String subqueryattributeprefix = coRequest.getParameter("cp_param10");

		// Split select string to array
		String[] selectarr = select.split(",");

		// Counter for limit (max 10)
		AtomicInteger counter = new AtomicInteger(0);

		// Process each element (limit to 10)
		responsexmlfull.getElementStream().forEach((Element p) -> {

			// Stop after 10 entries
			if (counter.get() >= 10) {
				return;
			}

			XML xmlElement = new XML(p);
			XML xmlEntry = new XML("entry");

			// Get unique attribute value
			String uniqueattributevalue = xmlElement.findNode(uniqueattribute).getText();

			if (uniqueattributevalue != null && !uniqueattributevalue.equals("")) {

				thislog.debug("renderNodePreviewResponse()...processing preview entry: " + uniqueattribute +
					"=" +
					uniqueattributevalue);

				// Add unique attribute first
				xmlEntry.appendNode(uniqueattribute, uniqueattributevalue);

				// Add all selected attributes
				for (int i = 0; i < selectarr.length; i++) {

					if (!selectarr[i].equalsIgnoreCase(uniqueattribute)) {
						String value = readSelectedValue(xmlElement, selectarr[i]);

						if (value == null) {
							value = "";
						}

						// Clean value (same as in renderNodeResponse)
						value = value.replace("\n", "").replace("\r", "");

						// Add to entry
						xmlEntry.appendNode(selectarr[i], value);
					}
				}

				if (resourcepathsubquery != null && !resourcepathsubquery.equals("")) {

					String finalresourcepathsubquery = resourcepathsubquery.replace("$id", uniqueattributevalue);

					thislog.debug("renderNodePreviewResponse()...  Executing subquery for preview: " + finalresourcepathsubquery);

					MSGraphRequest subqueryrequest = new MSGraphRequest(MSGraphRequest.type_GET, finalresourcepathsubquery);

					if (resourcepathsubqueryselect != null && !resourcepathsubqueryselect.equals("")) {
						subqueryrequest.addParameter("$select", resourcepathsubqueryselect);
					}

					if (resourcepathsubqueryfilter != null && !resourcepathsubqueryfilter.equals("")) {
						subqueryrequest.addParameter("$filter", resourcepathsubqueryfilter);
					}

					try {
						MSGraphResponse subqueryresponse = sendRequestWithRetry(session, subqueryrequest, coRequest, thislog);

						// Add subquery data to xmlEntry
						addSubqueryDataToPreviewEntry(thislog, subqueryresponse, xmlEntry, subqueryattributeprefix);

						thislog.debug("renderNodePreviewResponse()...  Subquery data added to preview entry");

					} catch (Exception e) {
						thislog.error("renderNodePreviewResponse()...subquery failed for preview entry, skipping subquery data: " + e.getMessage());
						// Continue without subquery data (entry still valid)
					}
				}

				// Add entry to entries
				xmlEntries.appendXML(xmlEntry);

				// Increment counter
				counter.incrementAndGet();
			}
		});

		thislog.info("renderNodePreviewResponse()...created XML with " + counter.get() +
			" entries");

		return xmlEntries;
	}

//	/**
//	 * Adds subquery response data to a preview entry XML
//	 * Helper method for renderPreviewResponse
//	 * 
//	 * @param thislog                 Logger instance
//	 * @param subqueryresponse        Subquery response from MSGraph
//	 * @param xmlEntry                The entry XML to add data to
//	 * @param subqueryattributeprefix Prefix for subquery attributes
//	 */
//	private void addSubqueryDataToPreviewEntry(Logger thislog, MSGraphResponse subqueryresponse, XML xmlEntry, String subqueryattributeprefix) {
//
//		XML subqueryresponsexml = subqueryresponse.getDataAsXML();
//
//		// Count subquery elements
//		long subqueryElementCount = subqueryresponsexml.getElementStream().count();
//		thislog.debug("  Subquery returned " + subqueryElementCount +
//			" elements");
//
//		// Reset stream
//		subqueryresponsexml = subqueryresponse.getDataAsXML();
//
//		subqueryresponsexml.getElementStream().forEach((Element p) -> {
//
//			XML xml = new XML(p);
//
//			// Check if this is a metadata element (like odata.context)
//			if (!xml.getElement().getName().equalsIgnoreCase("value")) {
//				thislog.debug("    Skipping metadata element: " + xml.getElement().getName());
//				return; // Skip metadata
//			}
//
//			if (xml.hasNodes()) {
//				String key = xml.getFirstChild().getElement().getName();
//				String value = xml.getFirstChild().getText();
//
//				if (value == null) {
//					value = "";
//				}
//
//				// Clean value
//				value = value.replace("\n", "").replace("\r", "");
//
//				thislog.debug("    Adding subquery attribute to preview: " + subqueryattributeprefix +
//					key +
//					"=" +
//					value);
//
//				// Add to XML entry
//				xmlEntry.appendNode(subqueryattributeprefix + key, value);
//			}
//		});
//	}

	/**
	 * Get relation preview data from Entra ID
	 * Returns XML with limited data set for frontend preview
	 * Shows source-target relationships with multi-value collapse
	 * 
	 * @param thislog    Logger instance
	 * @param coRequest  Request with connection and preview parameters
	 * @param coResponse Response object (not used for preview)
	 * @return XML with preview data (max 10 entries)
	 * @throws Exception if preview fails
	 */
	@Override
	public XML getRelationPreview(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {

		thislog.info("getRelationPreview()...[running]");

		// Extract connection parameters (same as getNodePreview)
		String authority = coRequest.getParameter("gp_param1");
		String tenant = coRequest.getParameter("gp_param2");
		String tenantname = coRequest.getParameter("gp_param3");
		String clientid = coRequest.getParameter("gp_param4");
		String scope = coRequest.getParameter("gp_param5");
		String connecteddomain = coRequest.getParameter("gp_param6");
		String proxyhost = coRequest.getParameter("gp_param7");
		String proxyport = coRequest.getParameter("gp_param8");
		String sslproxystring = coRequest.getParameter("gp_param9");
		boolean sslproxy = false;
		if (sslproxystring != null && sslproxystring.equalsIgnoreCase("true"))
			sslproxy = true;
		String proxyusername = coRequest.getParameter("gp_param10");
		String secret = coRequest.getParameter("gp_pwparam1");
		String proxypassword = coRequest.getParameter("gp_pwparam2");

		// Log connection parameters
		thislog.info("getRelationPreview()...[authority]: " + authority);
		thislog.info("getRelationPreview()...[tenant]: " + tenant);
		thislog.info("getRelationPreview()...[tenantname]: " + tenantname);
		thislog.info("getRelationPreview()...[clientid]: " + clientid);
		thislog.info("getRelationPreview()...[scope]: " + scope);
		thislog.info("getRelationPreview()...[connecteddomain]: " + connecteddomain);
		thislog.info("getRelationPreview()...[proxyhost]: " + proxyhost);
		thislog.info("getRelationPreview()...[proxyport]: " + proxyport);
		thislog.info("getRelationPreview()...[sslproxy]: " + sslproxy);
		thislog.info("getRelationPreview()...[proxyusername]: " + proxyusername);
		thislog.info("getRelationPreview()...[secret]: successfully read");
		thislog.info("getRelationPreview()...[proxypassword]: successfully read");

		String objecttype = coRequest.getParameter("cp_param1");
		String resourcepath = coRequest.getParameter("cp_param2");
		String select = coRequest.getParameter("cp_param3");
		String sourceuniqueattribute = coRequest.getParameter("cp_param4");
		String filter = coRequest.getParameter("cp_param5");
		String resourcepathsubquery = coRequest.getParameter("cp_param6");
		String resourcepathsubqueryselect = coRequest.getParameter("cp_param7");
		String resourcepathsubqueryfilter = coRequest.getParameter("cp_param8");
		String subqueryattributeprefix = coRequest.getParameter("cp_param9");

		// Log collection parameters
		thislog.info("getRelationPreview()...[objecttype]: " + objecttype);
		thislog.info("getRelationPreview()...[resourcepath]: " + resourcepath);
		thislog.info("getRelationPreview()...[select]: " + select);
		thislog.info("getRelationPreview()...[sourceuniqueattribute]: " + sourceuniqueattribute);
		thislog.info("getRelationPreview()...[filter]: " + filter);
		thislog.info("getRelationPreview()...[resourcepathsubquery]: " + resourcepathsubquery);
		thislog.info("getRelationPreview()...[resourcepathsubqueryselect]: " + resourcepathsubqueryselect);
		thislog.info("getRelationPreview()...[resourcepathsubqueryfilter]: " + resourcepathsubqueryfilter);
		thislog.info("getRelationPreview()...[subqueryattributeprefix]: " + subqueryattributeprefix);

		// Get session from pool (same as getNodePreview)
		MSGraphSession session = getOrCreateSession(coRequest);

		if (!session.isReady()) {
			thislog.error("getRelationPreview()...[ERROR] Unable to connect to Entra ID");

			// Return error XML
			XML xmlError = new XML("entries");
			xmlError.appendNode("error", "Unable to connect to Entra ID: " + session.getStatusmessage());
			return xmlError;
		}

		// Build request (same as getNodePreview)
		MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_GET, resourcepath);

		if (filter != null && !filter.equals("")) {
			request.addParameter("$filter", filter);
		}

		if (select != null && !select.equals("")) {
			request.addParameter("$select", buildGraphSelect(select));
		}

		// PREVIEW SPECIFIC: Limit to 10 results
		request.addParameter("$top", "10");

		thislog.info("getRelationPreview()...Sending request (limited to 10 results)");
		MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

		// Render relation preview response (with multi-value collapse)
		XML xmlPreview = renderRelationPreviewResponse(thislog, session, response, coRequest, objecttype, sourceuniqueattribute, select, resourcepathsubquery,
				resourcepathsubqueryselect, resourcepathsubqueryfilter, subqueryattributeprefix);

		thislog.info("getRelationPreview()...[SUCCESS] Preview data retrieved");

		return xmlPreview;
	}

	/**
	 * Renders MSGraph response data as XML for relation preview
	 * Shows source-target relationships with multi-value collapse
	 * One entry per source object (with collapsed target information)
	 * 
	 * @param thislog                    Logger instance
	 * @param session                    MSGraph session (needed for subquery)
	 * @param response                   MSGraph API response (source objects)
	 * @param coRequest                  Connector request (needed for subquery
	 *                                       parameters)
	 * @param objecttype                 Type of object (user, group, etc.)
	 * @param sourceuniqueattribute      Source identifier attribute name (e.g.
	 *                                       "id")
	 * @param select                     Selected source attributes
	 *                                       (comma-separated)
	 * @param resourcepathsubquery       Subquery path (e.g. "/users/$id/memberOf")
	 * @param resourcepathsubqueryselect Subquery select (e.g. "id,displayName")
	 * @param resourcepathsubqueryfilter Subquery filter
	 * @param subqueryattributeprefix    Prefix for target attributes (e.g.
	 *                                       "target-")
	 * @return XML with relation preview entries (max 10)
	 */
	private XML renderRelationPreviewResponse(Logger thislog, MSGraphSession session, MSGraphResponse response, ConnectorRequest coRequest, String objecttype,
			String sourceuniqueattribute, String select, String resourcepathsubquery, String resourcepathsubqueryselect, String resourcepathsubqueryfilter,
			String subqueryattributeprefix) {

		thislog.debug("renderRelationPreviewResponse()...objecttype: " + objecttype);

		XML xmlEntries = new XML("entries");

		// Get XML data from response (same as in renderNodePreviewResponse)
		XML responsexmlfull = response.getDataAsXML();

		if (responsexmlfull == null || !responsexmlfull.hasNodes()) {
			thislog.info("renderRelationPreviewResponse()...no data returned");
			return xmlEntries;
		}

		// Count elements
		long elementCount = responsexmlfull.getElementStream().count();
		thislog.info("renderRelationPreviewResponse()...processing " + elementCount +
			" source elements");

		// Reset stream (count() consumes it)
		responsexmlfull = response.getDataAsXML();

		// Split select string to array (for source attributes)
		String[] selectarr = select.split(",");

		// Counter for limit (max 10)
		AtomicInteger counter = new AtomicInteger(0);

		// Process each source element (limit to 10)
		responsexmlfull.getElementStream().forEach((Element p) -> {

			// Stop after 10 entries
			if (counter.get() >= 10) {
				return;
			}

			XML xmlElement = new XML(p);
			XML xmlEntry = new XML("entry");

			// Get source unique attribute value
			String sourceuniqueattributevalue = xmlElement.findNode(sourceuniqueattribute).getText();

			if (sourceuniqueattributevalue != null && !sourceuniqueattributevalue.equals("")) {

				thislog.debug("renderRelationPreviewResponse()...processing source: " + sourceuniqueattribute +
					"=" +
					sourceuniqueattributevalue);

				// Add source unique attribute first
				xmlEntry.appendNode(sourceuniqueattribute, sourceuniqueattributevalue);

				// Add all selected source attributes
				for (int i = 0; i < selectarr.length; i++) {

					if (!selectarr[i].equalsIgnoreCase(sourceuniqueattribute)) {
						String value = readSelectedValue(xmlElement, selectarr[i]);

						if (value == null) {
							value = "";
						}

						// Clean value
						value = value.replace("\n", "").replace("\r", "");

						// Add to entry
						xmlEntry.appendNode(selectarr[i], value);
					}
				}

				// Execute subquery to get target data (if configured)
				if (resourcepathsubquery != null && !resourcepathsubquery.equals("")) {

					String finalresourcepathsubquery = resourcepathsubquery.replace("$id", sourceuniqueattributevalue);

					thislog.debug("renderRelationPreviewResponse()...  Executing subquery: " + finalresourcepathsubquery);

					MSGraphRequest subqueryrequest = new MSGraphRequest(MSGraphRequest.type_GET, finalresourcepathsubquery);

					if (resourcepathsubqueryselect != null && !resourcepathsubqueryselect.equals("")) {
						subqueryrequest.addParameter("$select", resourcepathsubqueryselect);
					}

					if (resourcepathsubqueryfilter != null && !resourcepathsubqueryfilter.equals("")) {
						subqueryrequest.addParameter("$filter", resourcepathsubqueryfilter);
					}

					try {
						MSGraphResponse subqueryresponse = sendRequestWithRetry(session, subqueryrequest, coRequest, thislog);

						// Add collapsed target data to xmlEntry
						addCollapsedTargetDataToPreviewEntry(thislog, subqueryresponse, xmlEntry, resourcepathsubqueryselect, subqueryattributeprefix);

						thislog.debug("renderRelationPreviewResponse()...  Target data added to preview entry");

					} catch (Exception e) {
						thislog.error("renderRelationPreviewResponse()...subquery failed for source, skipping target data: " + e.getMessage());
						// Continue without target data (entry still valid with source data only)
					}
				}

				// Add entry to entries
				xmlEntries.appendXML(xmlEntry);

				// Increment counter
				counter.incrementAndGet();
			}
		});

		thislog.info("renderRelationPreviewResponse()...created XML with " + counter.get() +
			" entries");

		return xmlEntries;
	}

	/**
	 * Adds collapsed target data to a relation preview entry
	 * Shows count for multi-value relationships: "(N) - FirstValue"
	 * Helper method for renderRelationPreviewResponse
	 * 
	 * @param thislog                    Logger instance
	 * @param subqueryresponse           Subquery response from MSGraph (target
	 *                                       objects)
	 * @param xmlEntry                   The entry XML to add target data to
	 * @param resourcepathsubqueryselect Selected target attributes
	 *                                       (comma-separated)
	 * @param subqueryattributeprefix    Prefix for target attributes
	 */
	private void addCollapsedTargetDataToPreviewEntry(Logger thislog, MSGraphResponse subqueryresponse, XML xmlEntry, String resourcepathsubqueryselect,
			String subqueryattributeprefix) {

		XML subqueryresponsexml = subqueryresponse.getDataAsXML();

		// Count target elements
		long targetElementCount = subqueryresponsexml.getElementStream().count();
		thislog.debug("  Subquery returned " + targetElementCount +
			" target elements");

		// Reset stream
		subqueryresponsexml = subqueryresponse.getDataAsXML();

		// Collect all values per attribute (for multi-value detection)
		Map<String, List<String>> attributeValues = new HashMap<>();

		// Split select to know which attributes to extract
		String[] selectarr = resourcepathsubqueryselect.split(",");

		// Process all target elements
		subqueryresponsexml.getElementStream().forEach((Element p) -> {

			XML xml = new XML(p);

			// Skip metadata elements (like odata.context)
			if (!xml.getElement().getName().equalsIgnoreCase("value")) {
				thislog.debug("    Skipping metadata element: " + xml.getElement().getName());
				return;
			}

			// Extract all selected attributes from this target
			for (String attributeName : selectarr) {
				String value = readSelectedValue(xml, attributeName);

				if (value == null) {
					value = "";
				}

				// Clean value
				value = value.replace("\n", "").replace("\r", "");

				// Add to collection with prefix
				String prefixedAttributeName = subqueryattributeprefix + attributeName;
				attributeValues.computeIfAbsent(prefixedAttributeName, k -> new ArrayList<>()).add(value);

				thislog.debug("      Collected: " + prefixedAttributeName +
					" = " +
					value);
			}
		});

		// Add to XML with multi-value indicator
		for (Map.Entry<String, List<String>> entry : attributeValues.entrySet()) {
			String attributeName = entry.getKey();
			List<String> values = entry.getValue();

			if (values != null && values.size() > 1) {
				// MULTI-VALUE: Show count and first value
				String displayValue = "(" + values.size() +
					") - " +
					values.get(0);
				thislog.debug("    Adding multi-value target attribute: " + attributeName +
					"=" +
					displayValue);
				xmlEntry.appendNode(attributeName, displayValue);

			} else if (values != null && values.size() == 1) {
				// SINGLE VALUE: Show value only
				thislog.debug("    Adding single-value target attribute: " + attributeName +
					"=" +
					values.get(0));
				xmlEntry.appendNode(attributeName, values.get(0));

			} else {
				// EMPTY: Add empty node
				thislog.debug("    Adding empty target attribute: " + attributeName);
				xmlEntry.appendNode(attributeName, "");
			}
		}

		thislog.debug("  Added " + attributeValues.size() +
			" target attributes to preview entry");
	}

	/**
	 * Adds subquery response data to a preview entry XML
	 * Handles multi-value attributes (shows count if multiple values exist)
	 * Processes ALL attributes from $select (not just first child)
	 * Helper method for renderPreviewResponse
	 * 
	 * @param thislog                 Logger instance
	 * @param subqueryresponse        Subquery response from MSGraph
	 * @param xmlEntry                The entry XML to add data to
	 * @param subqueryattributeprefix Prefix for subquery attributes
	 */
	private void addSubqueryDataToPreviewEntry(Logger thislog, MSGraphResponse subqueryresponse, XML xmlEntry, String subqueryattributeprefix) {

		XML subqueryresponsexml = subqueryresponse.getDataAsXML();

		// Count subquery elements
		long subqueryElementCount = subqueryresponsexml.getElementStream().count();
		thislog.debug("  Subquery returned " + subqueryElementCount +
			" elements");

		// Reset stream
		subqueryresponsexml = subqueryresponse.getDataAsXML();

		// Collect all values per attribute (for multi-value detection)
		Map<String, List<String>> attributeValues = new HashMap<>();

		// First pass: Collect all values from ALL child elements
		subqueryresponsexml.getElementStream().forEach((Element p) -> {

			XML xml = new XML(p);

			// Skip metadata elements (like odata.context)
			if (!xml.getElement().getName().equalsIgnoreCase("value")) {
				thislog.debug("    Skipping metadata element: " + xml.getElement().getName());
				return;
			}

			// Process ALL child nodes (not just first!)
			if (xml.hasNodes()) {
				@SuppressWarnings("unchecked")
				List<Element> children = xml.getElement().elements();

				thislog.debug("    Processing " + children.size() +
					" child elements in this value node");

				for (Element child : children) {
					String key = child.getName();
					String value = child.getText();

					if (value == null) {
						value = "";
					}

					// Clean value
					value = value.replace("\n", "").replace("\r", "");

					// Add to collection with prefix
					String attributeName = subqueryattributeprefix + key;
					attributeValues.computeIfAbsent(attributeName, k -> new ArrayList<>()).add(value);

					thislog.debug("      Collected: " + attributeName +
						" = " +
						value);
				}
			}
		});

		// Second pass: Add to XML with multi-value indicator
		for (Map.Entry<String, List<String>> entry : attributeValues.entrySet()) {
			String attributeName = entry.getKey();
			List<String> values = entry.getValue();

			if (values != null && values.size() > 1) {
				// MULTI-VALUE: Show count and first value
				String displayValue = "(" + values.size() +
					") - " +
					values.get(0);
				thislog.debug("    Adding multi-value subquery attribute: " + attributeName +
					"=" +
					displayValue);
				xmlEntry.appendNode(attributeName, displayValue);

			} else if (values != null && values.size() == 1) {
				// SINGLE VALUE: Show value only
				thislog.debug("    Adding single-value subquery attribute: " + attributeName +
					"=" +
					values.get(0));
				xmlEntry.appendNode(attributeName, values.get(0));

			} else {
				// EMPTY: Add empty node
				thislog.debug("    Adding empty subquery attribute: " + attributeName);
				xmlEntry.appendNode(attributeName, "");
			}
		}

		thislog.debug("  Added " + attributeValues.size() +
			" subquery attributes to preview entry");
	}

	@Override
	public boolean getNodeData(Logger thislog, NodeCollection nodecollection, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {

		String authority = coRequest.getParameter("gp_param1");
		String tenant = coRequest.getParameter("gp_param2");
		String tenantname = coRequest.getParameter("gp_param3");
		String clientid = coRequest.getParameter("gp_param4");
		String scope = coRequest.getParameter("gp_param5");
		String connecteddomain = coRequest.getParameter("gp_param6");
		String proxyhost = coRequest.getParameter("gp_param7");
		String proxyport = coRequest.getParameter("gp_param8");
		String sslproxystring = coRequest.getParameter("gp_param9");
		boolean sslproxy = false;
		if (sslproxystring != null && sslproxystring.equalsIgnoreCase("true"))
			sslproxy = true;
		String proxyusername = coRequest.getParameter("gp_param10");

		String secret = coRequest.getParameter("gp_pwparam1");
		String proxypassword = coRequest.getParameter("gp_pwparam2");

		thislog.info("phase_getnodedata()...[authority] : " + authority);
		thislog.info("phase_getnodedata()...[tenant] : " + tenant);
		thislog.info("phase_getnodedata()...[tenantname] : " + tenantname);
		thislog.info("phase_getnodedata()...[clientid] : " + clientid);
		thislog.info("phase_getnodedata()...[scope] : " + scope);
		thislog.info("phase_getnodedata()...[connecteddomain] : " + connecteddomain);
		thislog.info("phase_getnodedata()...[proxyhost] : " + proxyhost);
		thislog.info("phase_getnodedata()...[proxyport] : " + proxyport);
		thislog.info("phase_getnodedata()...[sslproxy] : " + sslproxy);
		thislog.info("phase_getnodedata()...[proxyusername] : " + proxyusername);
		thislog.info("phase_getnodedata()...[secret] : successfully read.");
		thislog.info("phase_getnodedata()...[proxypassword] : successfully read.");

		String objecttype = coRequest.getParameter("np_param1");
		String resourcepath = coRequest.getParameter("np_param2");
		String uniqueattribute = coRequest.getParameter("np_param3");
		String select = coRequest.getParameter("np_param4");
		String filter = coRequest.getParameter("np_param5");
		String expand = coRequest.getParameter("np_param6");
		String resourcepathsubquery = coRequest.getParameter("np_param7");
		String resourcepathsubqueryselect = coRequest.getParameter("np_param8");
		String resourcepathsubqueryfilter = coRequest.getParameter("np_param9");
		String subqueryattributeprefix = coRequest.getParameter("np_param10");

		thislog.info("phase_getnodedata()...[objecttype] : " + objecttype);
		thislog.info("phase_getnodedata()...[resourcepath] : " + resourcepath);
		thislog.info("phase_getnodedata()...[uniqueattribute] : " + uniqueattribute);
		thislog.info("phase_getnodedata()...[select] : " + select);
		thislog.info("phase_getnodedata()...[filter] : " + filter);
		thislog.info("phase_getnodedata()...[expand] : " + expand);
		thislog.info("phase_getnodedata()...[resourcepathsubquery] : " + resourcepathsubquery);
		thislog.info("phase_getnodedata()...[resourcepathsubqueryselect] : " + resourcepathsubqueryselect);
		thislog.info("phase_getnodedata()...[resourcepathsubqueryfilter] : " + resourcepathsubqueryfilter);
		thislog.info("phase_getnodedata()...[subqueryattributeprefix] : " + subqueryattributeprefix);

		thislog.debug("phase_getnodedata()...whitelistrule: " + nodecollection.getWhitelistrule());
		thislog.debug("phase_getnodedata()...blacklistrule: " + nodecollection.getBlacklistrule());
		thislog.debug("phase_getnodedata()...skipproperties: " + nodecollection.getSkipproperties());

		MSGraphSession session = getOrCreateSession(coRequest);

		if (session.isReady()) {

			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_GET, resourcepath);

			if (filter != null && !filter.equals("")) {
				request.addParameter("$filter", filter);
			}

			if (expand != null && !expand.equals("")) {
				request.addParameter("$expand", expand);
			}

			// Some resources - report endpoints in particular - do not accept $select. np_param11 = false
			// then keeps the field list for reading the answer without sending it to Graph.
			String sendselect = coRequest.getParameter("np_param11");
			boolean sendselectenabled = sendselect == null || sendselect.isBlank() || !"false".equalsIgnoreCase(sendselect.trim());

			if (select != null && !select.equals("") && sendselectenabled) {
				request.addParameter("$select", buildGraphSelect(select));
			} else if (select != null && !select.equals("")) {
				thislog.info("phase_getnodedata()...$select not sent (np_param11=false), fields are only used for reading");
			}

			thislog.debug("phase_getnodedata()...Sending Request for page 1,");
			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			renderNodeResponse(thislog, session, response, nodecollection, coResponse, coRequest);

			int i = 1;
			while (response.getNexturi() != null && !response.getNexturi().equals("")) {

				i++;
				thislog.debug("phase_getnodedata()...Sending Request for page " + i +
					"...");
				String nexturl = response.getNexturi();
				nexturl = nexturl.replace("https://graph.microsoft.com/v1.0/", "");

				MSGraphRequest requestsub = new MSGraphRequest(MSGraphRequest.type_GET, nexturl);
				response = sendRequestWithRetry(session, requestsub, coRequest, thislog);

				renderNodeResponse(thislog, session, response, nodecollection, coResponse, coRequest);
			}

		} else {
			thislog.error("phase_getnodedata()...[ERROR] Unable to connect to Entra ID.");
			throw new Exception(session.getStatusmessage());
		}

		return true;
		// TODO Auto-generated method stub
	}

	private void renderNodeResponse(Logger thislog, MSGraphSession session, MSGraphResponse response, NodeCollection nodecollection,
			ConnectorResponse coResponse, ConnectorRequest coRequest) {

		// String objecttype = coRequest.getParameter("np_param1");
		String uniqueattribute = coRequest.getParameter("np_param3");
		String select = coRequest.getParameter("np_param4");
		// String filter = coRequest.getParameter("np_param5");
		// String expand = coRequest.getParameter("np_param6");
		String resourcepathsubquery = coRequest.getParameter("np_param7");
		String resourcepathsubqueryselect = coRequest.getParameter("np_param8");
		String resourcepathsubqueryfilter = coRequest.getParameter("np_param9");
		// String subqueryattributeprefix = coRequest.getParameter("np_param10");

		boolean hasRules = nodecollection.hasRules();

		XML responsexmlfull = response.getDataAsXML();

		// Count total nodes for logging
		long nodeCount = responsexmlfull.getElementStream().count();
		thislog.info("Processing " + nodeCount +
			" node elements");

		// Reset stream
		responsexmlfull = response.getDataAsXML();

		AtomicReference<RuleEvaluableString> ruleEvaluableRef = new AtomicReference<>();
		responsexmlfull.getElementStream().forEach((Element p) -> {

			XML test = new XML(p);

			String uniqueattributevalue = test.findNode(uniqueattribute).getText();

			if (uniqueattributevalue != null && !uniqueattributevalue.equals("")) {

				thislog.debug("Processing node: " + uniqueattribute +
					"=" +
					uniqueattributevalue);

				// a valid node was found
				if (hasRules)
					ruleEvaluableRef.set(new RuleEvaluableString(coRequest.getNodename()));

				// it's just one query
				// Create line and add uniqueattribute first
				String line = "\"" + uniqueattribute +
					":" +
					uniqueattributevalue +
					"\"";
				if (hasRules)
					ruleEvaluableRef.get().addAttribute(uniqueattribute, uniqueattributevalue);

				String selectarr[] = select.split(",");

				for (int i = 0; i < selectarr.length; i++) {

					if (!selectarr[i].equalsIgnoreCase(uniqueattribute)) {
						String value = readSelectedValue(test, selectarr[i]);

						if (value == null)
							value = "";

						line = nodecollection.addDataToLine(selectarr[i], value, line, ruleEvaluableRef.get());

					}

				}

				thislog.debug("  Base line created: " + line);

				if (resourcepathsubquery != null && !resourcepathsubquery.equals("")) {

					String finalresourcepathsubquery = resourcepathsubquery.replace("$id", uniqueattributevalue);

					thislog.debug("  Executing subquery: " + finalresourcepathsubquery);

					MSGraphRequest subqueryrequest = new MSGraphRequest(MSGraphRequest.type_GET, finalresourcepathsubquery);

					if (resourcepathsubqueryselect != null && !resourcepathsubqueryselect.equals("")) {
						subqueryrequest.addParameter("$select", resourcepathsubqueryselect);
					}

					if (resourcepathsubqueryfilter != null && !resourcepathsubqueryfilter.equals("")) {
						subqueryrequest.addParameter("$filter", resourcepathsubqueryfilter);
					}

					try {
						MSGraphResponse subqueryresponse = sendRequestWithRetry(session, subqueryrequest, coRequest, thislog);
						line = renderNodeSubqueryResponse(thislog, subqueryresponse, line, nodecollection, coResponse, coRequest, ruleEvaluableRef);

						// Follow @odata.nextLink for the subquery too - otherwise
						// any target collection larger than a single Graph page
						// (e.g. $expand caps at ~20, plain collections default to ~100)
						// gets silently truncated.
						int subpage = 1;
						while (subqueryresponse.getNexturi() != null && !subqueryresponse.getNexturi().equals("")) {
							subpage++;
							thislog.debug("  Executing subquery page " + subpage + "...");
							String subnexturl = subqueryresponse.getNexturi().replace("https://graph.microsoft.com/v1.0/", "");
							MSGraphRequest subqueryrequestnext = new MSGraphRequest(MSGraphRequest.type_GET, subnexturl);
							subqueryresponse = sendRequestWithRetry(session, subqueryrequestnext, coRequest, thislog);
							line = renderNodeSubqueryResponse(thislog, subqueryresponse, line, nodecollection, coResponse, coRequest, ruleEvaluableRef);
						}

						thislog.debug("  Line after subquery: " + line);

					} catch (Exception e) {
						thislog.error("Subquery failed for node, skipping subquery data: " + e.getMessage());
					}

				}

				// DEBUG: Log what's in the RuleEvaluable before filtering
				if (hasRules && ruleEvaluableRef.get() != null) {
					thislog.debug("  RuleEvaluable attributes: " + ruleEvaluableRef.get().getAttributes());
					for (String attr : ruleEvaluableRef.get().getAttributes()) {
						List<String> values = ruleEvaluableRef.get().getAttributeValues(attr);
						thislog.debug("    " + attr +
							" = " +
							(values != null ? values : "null"));
					}
				}

				try {

					if (!hasRules || nodecollection.isAllowed(thislog, ruleEvaluableRef.get())) {
						coResponse.writeDataLine(line);
						thislog.debug("  Line written: " + line);
					} else {
						thislog.debug("  Line filtered by rules: " + line);
					}

					// coResponse.writeDataLine(line);
				} catch (IOException e) {
					thislog.error("Error writing node line", e);
					e.printStackTrace();
				}

			}

		});

	}

//	private String renderNodeSubqueryResponse(Logger thislog, MSGraphResponse subqueryresponse, String line, NodeCollection nodecollection,
//			ConnectorResponse coResponse, ConnectorRequest coRequest, AtomicReference<RuleEvaluableString> ruleEvaluableRef) {
//
//		String subqueryattributeprefix = coRequest.getParameter("np_param10");
//
//		XML subqueryresponsexml = subqueryresponse.getDataAsXML();
//
//		// Count subquery elements
//		long subqueryElementCount = subqueryresponsexml.getElementStream().count();
//		thislog.debug("  Subquery returned " + subqueryElementCount +
//			" elements");
//
//		// Reset stream
//		subqueryresponsexml = subqueryresponse.getDataAsXML();
//
//		AtomicReference<String> lineRef = new AtomicReference<>();
//		lineRef.set(line);
//
//		subqueryresponsexml.getElementStream().forEach((Element p) -> {
//
//			XML xml = new XML(p);
//
//			// Check if this is a metadata element (like odata.context)
//			if (!xml.getElement().getName().equalsIgnoreCase("value")) {
//				thislog.debug("    Skipping metadata element: " + xml.getElement().getName());
//				return; // Skip metadata
//			}
//
//			if (xml.hasNodes()) {
//				String key = xml.getFirstChild().getElement().getName();
//				String value = xml.getFirstChild().getText();
//
//				thislog.debug("    Adding subquery attribute: " + subqueryattributeprefix +
//					key +
//					"=" +
//					value);
//
//				lineRef.set(nodecollection.addDataToLine(subqueryattributeprefix + key, value, lineRef.get(), ruleEvaluableRef.get()));
//			}
//		});
//
//		return lineRef.get();
//	}

	/**
	 * Renders subquery response data and adds it to the current CSV line
	 * Processes ALL child elements from subquery response (not just first)
	 * 
	 * @param thislog          Logger instance
	 * @param subqueryresponse Subquery response from MSGraph
	 * @param line             Current CSV line to append data to
	 * @param nodecollection   Node collection for addDataToLine
	 * @param coResponse       Connector response (not used)
	 * @param coRequest        Connector request for parameters
	 * @param ruleEvaluableRef Rule evaluable reference for filtering
	 * @return Updated CSV line with subquery data
	 */
	private String renderNodeSubqueryResponse(Logger thislog, MSGraphResponse subqueryresponse, String line, NodeCollection nodecollection,
			ConnectorResponse coResponse, ConnectorRequest coRequest, AtomicReference<RuleEvaluableString> ruleEvaluableRef) {

		String subqueryattributeprefix = coRequest.getParameter("np_param10");

		XML subqueryresponsexml = subqueryresponse.getDataAsXML();

		// Count subquery elements
		long subqueryElementCount = subqueryresponsexml.getElementStream().count();
		thislog.debug("  Subquery returned " + subqueryElementCount +
			" elements");

		// Reset stream
		subqueryresponsexml = subqueryresponse.getDataAsXML();

		AtomicReference<String> lineRef = new AtomicReference<>();
		lineRef.set(line);

		subqueryresponsexml.getElementStream().forEach((Element p) -> {

			XML xml = new XML(p);

			// Check if this is a metadata element (like odata.context)
			if (!xml.getElement().getName().equalsIgnoreCase("value")) {
				thislog.debug("    Skipping metadata element: " + xml.getElement().getName());
				return; // Skip metadata
			}

			// Process ALL child nodes (not just first!)
			if (xml.hasNodes()) {
				@SuppressWarnings("unchecked")
				List<Element> children = xml.getElement().elements();

				thislog.debug("    Processing " + children.size() +
					" child elements in this value node");

				for (Element child : children) {
					String key = child.getName();
					String value = child.getText();

					if (value == null) {
						value = "";
					}

					thislog.debug("      Adding subquery attribute: " + subqueryattributeprefix +
						key +
						"=" +
						value);

					lineRef.set(nodecollection.addDataToLine(subqueryattributeprefix + key, value, lineRef.get(), ruleEvaluableRef.get()));
				}
			}
		});

		return lineRef.get();
	}

	@Override
	public boolean getRelationData(Logger thislog, RelationCollection relationcollection, ConnectorRequest coRequest, ConnectorResponse coResponse)
			throws Exception {

		String authority = coRequest.getParameter("gp_param1");
		String tenant = coRequest.getParameter("gp_param2");
		String tenantname = coRequest.getParameter("gp_param3");
		String clientid = coRequest.getParameter("gp_param4");
		String scope = coRequest.getParameter("gp_param5");
		String connecteddomain = coRequest.getParameter("gp_param6");
		String proxyhost = coRequest.getParameter("gp_param7");
		String proxyport = coRequest.getParameter("gp_param8");
		String sslproxystring = coRequest.getParameter("gp_param9");
		boolean sslproxy = false;
		if (sslproxystring != null && sslproxystring.equalsIgnoreCase("true"))
			sslproxy = true;
		String proxyusername = coRequest.getParameter("gp_param10");
		String secret = coRequest.getParameter("gp_pwparam1");
		String proxypassword = coRequest.getParameter("gp_pwparam2");

		thislog.info("phase_getrelationdata()...[authority] : " + authority);
		thislog.info("phase_getrelationdata()...[tenant] : " + tenant);
		thislog.info("phase_getrelationdata()...[tenantname] : " + tenantname);
		thislog.info("phase_getrelationdata()...[clientid] : " + clientid);
		thislog.info("phase_getrelationdata()...[scope] : " + scope);
		thislog.info("phase_getrelationdata()...[connecteddomain] : " + connecteddomain);
		thislog.info("phase_getrelationdata()...[proxyhost] : " + proxyhost);
		thislog.info("phase_getrelationdata()...[proxyport] : " + proxyport);
		thislog.info("phase_getrelationdata()...[sslproxy] : " + sslproxy);
		thislog.info("phase_getrelationdata()...[proxyusername] : " + proxyusername);
		thislog.info("phase_getrelationdata()...[secret] : successfully read.");
		thislog.info("phase_getrelationdata()...[proxypassword] : successfully read.");

		thislog.debug("phase_getrelationdata()...whitelistrule: " + relationcollection.getWhitelistrule());
		thislog.debug("phase_getrelationdata()...blacklistrule: " + relationcollection.getBlacklistrule());
		thislog.debug("phase_getrelationdata()...skipproperties: " + relationcollection.getSkipproperties());

		String objecttype = coRequest.getParameter("rp_param1");
		String resourcepath = coRequest.getParameter("rp_param2");
		String select = coRequest.getParameter("rp_param3");
		String sourceuniqueattribute = coRequest.getParameter("rp_param4");
		String filter = coRequest.getParameter("rp_param5");
		String resourcepathsubquery = coRequest.getParameter("rp_param6");
		String resourcepathsubqueryselect = coRequest.getParameter("rp_param7");
		String resourcepathsubqueryfilter = coRequest.getParameter("rp_param8");

		thislog.info("phase_getrelationdata()...[objecttype] : " + objecttype);
		thislog.info("phase_getrelationdata()...[resourcepath] : " + resourcepath);
		thislog.info("phase_getrelationdata()...[select] : " + select);
		thislog.info("phase_getrelationdata()...[sourceuniqueattribute] : " + sourceuniqueattribute);
		thislog.info("phase_getrelationdata()...[filter] : " + filter);
		thislog.info("phase_getrelationdata()...[resourcepathsubquery] : " + resourcepathsubquery);
		thislog.info("phase_getrelationdata()...[resourcepathsubqueryselect] : " + resourcepathsubqueryselect);
		thislog.info("phase_getrelationdata()...[resourcepathsubqueryfilter] : " + resourcepathsubqueryfilter);

		MSGraphSession session = getOrCreateSession(coRequest);

		if (session.isReady()) {

			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_GET, resourcepath);

			if (filter != null && !filter.equals("")) {
				request.addParameter("$filter", filter);
			}

//			if (expand != null && !expand.equals("")) {
//				request.addParameter("$expand", expand);
//			}

			if (select != null && !select.equals("")) {
				request.addParameter("$select", buildGraphSelect(select));
			}

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			thislog.debug("phase_getrelationdata()...Sending Request for page 1...");

			renderRelationResponse(thislog, session, response, relationcollection, coResponse, coRequest);

			int i = 1;
			while (response.getNexturi() != null && !response.getNexturi().equals("")) {
				i++;
				thislog.debug("phase_getrelationdata()...Sending Request for page " + i +
					"...");
				String nexturl = response.getNexturi();
				nexturl = nexturl.replace("https://graph.microsoft.com/v1.0/", "");

				MSGraphRequest requestsub = new MSGraphRequest(MSGraphRequest.type_GET, nexturl);
				response = sendRequestWithRetry(session, requestsub, coRequest, thislog);

				renderRelationResponse(thislog, session, response, relationcollection, coResponse, coRequest);
			}

		}

		return true;
		// TODO Auto-generated method stub
	}

	private void renderRelationResponse(Logger thislog, MSGraphSession session, MSGraphResponse response, RelationCollection relationcollection,
			ConnectorResponse coResponse, ConnectorRequest coRequest) {

		// Read parameters from request
		String sourceuniqueattribute = coRequest.getParameter("rp_param4");
		String select = coRequest.getParameter("rp_param3");
		String resourcepathsubquery = coRequest.getParameter("rp_param6");
		String resourcepathsubqueryselect = coRequest.getParameter("rp_param7");
		String resourcepathsubqueryfilter = coRequest.getParameter("rp_param8");
		String subqueryattributeprefix = coRequest.getParameter("rp_param9");

		boolean hasRules = relationcollection.hasRules();

		XML xmlResponseFull = response.getDataAsXML();
		AtomicReference<RuleEvaluableString> ruleEvaluableRef = new AtomicReference<>();

		// Count total source elements for logging
		long sourceElementCount = xmlResponseFull.getElementStream().count();
		thislog.info("Processing " + sourceElementCount +
			" source elements (users)");

		// Reset stream (count() consumes it)
		xmlResponseFull = response.getDataAsXML();

		xmlResponseFull.getElementStream().forEach((Element p) -> {

			XML xmlElement = new XML(p);

			// Get source unique attribute value (e.g. user id)
			String sourceuniqueattributevalue = xmlElement.findNode(sourceuniqueattribute).getText();

			if (sourceuniqueattributevalue != null && !sourceuniqueattributevalue.equals("")) {

				thislog.debug("Processing source element: " + sourceuniqueattribute +
					"=" +
					sourceuniqueattributevalue);

				ruleEvaluableRef.set(new RuleEvaluableString(coRequest.getNodename()));

				// Build base line with source attributes
				AtomicReference<String> lineRef = new AtomicReference<>();
				lineRef.set("\"" + sourceuniqueattribute +
					":" +
					sourceuniqueattributevalue +
					"\"");
				ruleEvaluableRef.get().addAttribute(sourceuniqueattribute, sourceuniqueattributevalue);

				// Add other selected attributes from main query
				String[] selectarr = select.split(",");
				for (int i = 0; i < selectarr.length; i++) {
					if (!selectarr[i].equalsIgnoreCase(sourceuniqueattribute)) {
						String value = readSelectedValue(xmlElement, selectarr[i]);
						if (value == null)
							value = "";
						lineRef.set(relationcollection.addDataToLine(selectarr[i], value, lineRef.get(), ruleEvaluableRef.get()));
					}
				}

				thislog.debug("  Base line created: " + lineRef.get());

				// Execute subquery to get target data (e.g. groups)
				if (resourcepathsubquery != null && !resourcepathsubquery.equals("")) {

					// Replace $id placeholder with actual source id
					String finalresourcepathsubquery = resourcepathsubquery.replace("$id", sourceuniqueattributevalue);

					thislog.debug("  Executing subquery: " + finalresourcepathsubquery);

					// Build subquery request
					MSGraphRequest subqueryrequest = new MSGraphRequest(MSGraphRequest.type_GET, finalresourcepathsubquery);

					if (resourcepathsubqueryselect != null && !resourcepathsubqueryselect.equals("")) {
						subqueryrequest.addParameter("$select", resourcepathsubqueryselect);
					}

					if (resourcepathsubqueryfilter != null && !resourcepathsubqueryfilter.equals("")) {
						subqueryrequest.addParameter("$filter", resourcepathsubqueryfilter);
					}
					try {
						// Send subquery with retry
						MSGraphResponse subqueryresponse = sendRequestWithRetry(session, subqueryrequest, coRequest, thislog);

						// Create a map to hold all lines (one per target), accumulated across ALL subquery pages
						Map<String, RuleEvaluableString> hmLines = new HashMap<String, RuleEvaluableString>();

						// Follow @odata.nextLink for the subquery too - otherwise any target
						// collection larger than a single Graph page (e.g. $expand caps at
						// ~20, plain collections default to ~100) gets silently truncated.
						int subpage = 1;
						while (true) {

							// Process subquery response - create one line per target element (multiline!)
							XML xmlSubqueryResponse = subqueryresponse.getDataAsXML();

							// Count target elements
							long targetElementCount = xmlSubqueryResponse.getElementStream().count();
							thislog.debug("  Subquery page " + subpage +
								" returned " +
								targetElementCount +
								" target elements");

							// Reset stream
							xmlSubqueryResponse = subqueryresponse.getDataAsXML();

							xmlSubqueryResponse.getElementStream().forEach((Element subElement) -> {

							XML xmlSubElement = new XML(subElement);

							// Only process <value> elements, skip metadata like <odata.context>
							if (!xmlSubElement.getElement().getName().equalsIgnoreCase("value")) {
								return; // Skip this element
							}

							// Start with a copy of the base line (source attributes)
							AtomicReference<String> targetLineRef = new AtomicReference<>();
							targetLineRef.set(lineRef.get());

							// ALWAYS create a new RuleEvaluableString for this specific relation line
							RuleEvaluableString targetRuleEvaluable = new RuleEvaluableString(coRequest.getNodename());

							// Copy source attributes from ruleEvaluableRef to target rule evaluable
							if (ruleEvaluableRef.get() != null) {
								for (String sourceAttributeKey : ruleEvaluableRef.get().getAttributes()) {
									List<String> sourceAttributeValues = ruleEvaluableRef.get().getAttributeValues(sourceAttributeKey);
									if (sourceAttributeValues != null) {
										targetRuleEvaluable.addAttribute(sourceAttributeKey, sourceAttributeValues);
									}
								}
							}

							// Add target attributes with prefix
							String[] subqueryselectarr = resourcepathsubqueryselect.split(",");
							for (int i = 0; i < subqueryselectarr.length; i++) {
								String value = xmlSubElement.findNode(subqueryselectarr[i]).getText();
								if (value == null)
									value = "";

								targetRuleEvaluable.addAttribute(subqueryattributeprefix + subqueryselectarr[i], value);

								// Then add to line (respects skipproperties)
								targetLineRef.set(
										relationcollection.addDataToLine(subqueryattributeprefix + subqueryselectarr[i], value, targetLineRef.get(), null));
							}

							// DEBUG: Log what's in the RuleEvaluable
							if (hasRules) {
								thislog.debug("    RuleEvaluable attributes: " + targetRuleEvaluable.getAttributes());
								for (String attr : targetRuleEvaluable.getAttributes()) {
									List<String> values = targetRuleEvaluable.getAttributeValues(attr);
									thislog.debug("      " + attr +
										" = " +
										(values != null ? values : "null"));
								}
							}

							// Store this line in the map
							hmLines.put(targetLineRef.get(), targetRuleEvaluable);
						});

						if (subqueryresponse.getNexturi() == null || subqueryresponse.getNexturi().equals("")) {
							break;
						}

						subpage++;
						thislog.debug("  Executing subquery page " + subpage + "...");
						String subnexturl = subqueryresponse.getNexturi().replace("https://graph.microsoft.com/v1.0/", "");
						MSGraphRequest subqueryrequestnext = new MSGraphRequest(MSGraphRequest.type_GET, subnexturl);
						subqueryresponse = sendRequestWithRetry(session, subqueryrequestnext, coRequest, thislog);
					}

					thislog.debug("  Created " + hmLines.size() +
						" relation lines for this source element");

					// Write all lines (with rule filtering)
					int writtenCount = 0;
					int filteredCount = 0;
					for (Map.Entry<String, RuleEvaluableString> entry : hmLines.entrySet()) {
						try {
							if (!hasRules || relationcollection.isAllowed(entry.getValue())) {
								coResponse.writeDataLine(entry.getKey());
								writtenCount++;
								thislog.debug("    Line written: " + entry.getKey());
							} else {
								filteredCount++;
								thislog.debug("    Line filtered by rules: " + entry.getKey());
							}
						} catch (IOException e) {
							thislog.error("Error writing relation line", e);
							e.printStackTrace();
						}
					}

					if (hasRules) {
						thislog.info("  Result for source " + sourceuniqueattributevalue +
							": " +
							writtenCount +
							" lines written, " +
							filteredCount +
							" lines filtered");
					} else {
						thislog.info("  Result for source " + sourceuniqueattributevalue +
							": " +
							writtenCount +
							" lines written");
					}

				} catch (Exception e) {
						thislog.error("Subquery failed for source " + sourceuniqueattributevalue +
							", skipping relations: " +
							e.getMessage());
						// Continue with next source element (no relations written for this source)
					}
				}
			}
		});
	}

	/**
	 * Extracts connection parameters from ConnectorRequest for connection pooling.
	 * Maps gp_param* keys directly (ConnectionManager uses these for key
	 * generation)
	 * 
	 * @param request The connector request containing parameters
	 * @return Map with connection parameters using gp_param* keys
	 */
	private Map<String, String> extractConnectionParams(ConnectorRequest request) {
		Map<String, String> params = new HashMap<>();

		// Use gp_param* keys directly (as ConnectionManager expects them)
		params.put("gp_param1", request.getParameter("gp_param1")); // authority
		params.put("gp_param2", request.getParameter("gp_param2")); // tenant
		params.put("gp_param3", request.getParameter("gp_param3")); // tenantname
		params.put("gp_param4", request.getParameter("gp_param4")); // clientid
		params.put("gp_param5", request.getParameter("gp_param5")); // scope
		params.put("gp_param6", request.getParameter("gp_param6")); // connecteddomain
		params.put("gp_param7", request.getParameter("gp_param7")); // proxyhost
		params.put("gp_param8", request.getParameter("gp_param8")); // proxyport
		params.put("gp_param9", request.getParameter("gp_param9")); // sslproxy
		params.put("gp_param10", request.getParameter("gp_param10")); // proxyusername
		params.put("gp_pwparam1", request.getParameter("gp_pwparam1")); // secret
		params.put("gp_pwparam2", request.getParameter("gp_pwparam2")); // proxypassword

		return params;
	}

	/**
	 * Gets an MSGraphSession from the connection pool or creates a new one.
	 * 
	 * @param request The connector request containing connection parameters
	 * @return MSGraphSession ready to use
	 * @throws Exception if session creation fails
	 */
	private MSGraphSession getOrCreateSession(ConnectorRequest request) throws Exception {
		// Extract parameters
		Map<String, String> params = extractConnectionParams(request);

		// Get connection from pool (or create new one)
		MSGraphManagedConnection managedConn =
				ConnectionManager.getInstance().getConnection(params, MSGraphManagedConnection.class, new MSGraphConnectionFactory());

		// Return the wrapped session
		return managedConn.getSession();
	}

	/**
	 * Sends MSGraph request with automatic retry on connection failure.
	 * <p>
	 * If the first attempt fails (token expired, network issue, etc.),
	 * this method automatically gets a fresh session from the connection pool
	 * and retries the request once.
	 * 
	 * @param session   The current MSGraph session
	 * @param request   The request to send
	 * @param coRequest The connector request (needed to get fresh session)
	 * @return MSGraphResponse from the API
	 * @throws Exception if both attempts fail
	 */
	private MSGraphResponse sendRequestWithRetry(MSGraphSession session, MSGraphRequest request, ConnectorRequest coRequest, Logger logger) throws Exception {
		try {
			// First attempt with current session
			return session.sendRequest(request);

		} catch (Exception firstAttempt) {
			// First attempt failed - log and retry
			logger.warn("Request failed on first attempt: {}. Retrying with fresh session...", firstAttempt.getMessage());

			try {
				// Get fresh session from pool (will create new if needed)
				MSGraphSession freshSession = getOrCreateSession(coRequest);

				// Retry with fresh session
				logger.debug("Retrying request with fresh session...");
				return freshSession.sendRequest(request);

			} catch (Exception secondAttempt) {
				// Both attempts failed - log and throw
				logger.error("Request failed after retry: {}", secondAttempt.getMessage());
				throw secondAttempt;
			}
		}
	}

	/*
	 * Parameter contract (daccord 2.3 and later)
	 * ------------------------------------------
	 * The provisioning extension (TaskHandler) sends two kinds of values to the JAVAConnector, which
	 * ProvisioningHelper.buildConnectorRequest (daccord-connector-core) turns into request parameters:
	 *
	 * - Graph attributes travel in the data XML (//attributes/attribute) and arrive as
	 *   pp_data_<attributename>, e.g. pp_data_givenName. They are written into the Graph body as they
	 *   are, PASSWORD is decrypted by the core.
	 * - Identifiers and steering values (accountuniqueid, securityobjectuniqueid, strategy, domainname,
	 *   command, ...) travel as named GadgetAPI variables. The JAVAConnector hands every variable over
	 *   and they arrive as pp_config_<name> - always in lowercase.
	 *
	 * Before daccord 2.3 the JAVAConnector forwarded only a few variables, so the provisioning extension
	 * 1.0 sent identifiers and steering values inside the data XML as well. getIdentifier() and
	 * getConfig() therefore read pp_config_ first and fall back to pp_data_; the steering values are
	 * excluded from the Graph body where they could arrive as attributes.
	 */

	/** Prefix of the Graph attributes from the data XML, see the parameter contract above. */
	private static final String ACCOUNT_DATA_PREFIX = "pp_data_";

	/** Prefix of the identifiers and steering values (named variables), see the parameter contract above. */
	private static final String CONFIG_PARAMETER_PREFIX = "pp_config_";

	/**
	 * Reads an identifier or steering value: as named variable ({@value #CONFIG_PARAMETER_PREFIX}),
	 * otherwise as attribute ({@value #ACCOUNT_DATA_PREFIX}) as sent by the provisioning extension 1.0.
	 *
	 * @param coRequest the request to read from
	 * @param name      parameter name without prefix, e.g. "accountuniqueid"
	 * @return the value, or null if it is present under neither prefix
	 */
	private static String getIdentifier(ConnectorRequest coRequest, String name) {

		String value = coRequest.getParameter(CONFIG_PARAMETER_PREFIX + name);
		if (value == null || value.isBlank())
			value = coRequest.getParameter(ACCOUNT_DATA_PREFIX + name);

		return value;
	}

	/** Matches JSON string values whose property name contains "password" (case-insensitive). */
	private static final Pattern PATTERN_JSON_PASSWORD = Pattern.compile("(\"[^\"]*(?i:password)[^\"]*\"\\s*:\\s*\")([^\"]*)(\")");

	/**
	 * Shortens every password value in a JSON body to its first three characters so that secrets never
	 * appear in full in the log. Must be applied to any body that is written to the log.
	 */
	private static String maskJsonSecrets(String json) {

		if (json == null)
			return null;

		Matcher matcher = PATTERN_JSON_PASSWORD.matcher(json);
		StringBuffer masked = new StringBuffer();

		while (matcher.find()) {
			String value = matcher.group(2);
			String shortened = value.length() > 3 ? value.substring(0, 3) + "..." : value;
			matcher.appendReplacement(masked, Matcher.quoteReplacement(matcher.group(1) + shortened + matcher.group(3)));
		}
		matcher.appendTail(masked);

		return masked.toString();
	}

	/**
	 * Builds a Microsoft Graph JSON request body from all {@link ConnectorRequest} parameters that carry
	 * the {@value #ACCOUNT_DATA_PREFIX} prefix. The part of the parameter name after the prefix is used
	 * verbatim as the JSON property name, e.g. "pp_data_displayName" -&gt; "displayName". Dots build
	 * nested objects, e.g. "pp_data_passwordProfile.password" -&gt; passwordProfile.password.
	 * <p>
	 * Values "true"/"false" (case-insensitive) are written as JSON booleans, everything else as a JSON string.
	 *
	 * @param coRequest the request to read parameters from
	 * @return the JSON body as a string
	 */
	private static String buildJsonBodyFromParameters(ConnectorRequest coRequest) {
		return buildJsonBodyFromParameters(coRequest, Set.of());
	}

	/**
	 * Same as {@link #buildJsonBodyFromParameters(ConnectorRequest)}, but skips the given field names
	 * (the part after the {@value #ACCOUNT_DATA_PREFIX} prefix) - e.g. "accountuniqueid", which
	 * identifies the target object rather than being a field to set on it.
	 *
	 * @param coRequest      the request to read parameters from
	 * @param excludedFields field names (without prefix) to leave out of the body
	 * @return the JSON body as a string
	 */
	private static String buildJsonBodyFromParameters(ConnectorRequest coRequest, Set<String> excludedFields) {
		return buildBodyFromParameters(coRequest, excludedFields).toString();
	}

	/**
	 * Same as {@link #buildJsonBodyFromParameters(ConnectorRequest, Set)}, but returns the node so that
	 * callers can still add derived mandatory fields before serialising.
	 *
	 * @param coRequest      the request to read parameters from
	 * @param excludedFields field names (without prefix) to leave out of the body
	 * @return the body as an {@link ObjectNode}
	 */
	private static ObjectNode buildBodyFromParameters(ConnectorRequest coRequest, Set<String> excludedFields) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode body = mapper.createObjectNode();

		for (Map.Entry<String, String> entry : coRequest.getParameters().entrySet()) {
			String key = entry.getKey();
			if (key == null || !key.startsWith(ACCOUNT_DATA_PREFIX)) {
				continue;
			}
			String fieldPath = key.substring(ACCOUNT_DATA_PREFIX.length());
			if (fieldPath.isEmpty() || excludedFields.contains(fieldPath)) {
				continue;
			}
			setNestedValue(body, fieldPath, entry.getValue());
		}

		return body;
	}

	/**
	 * Derives a display name from the given name and surname already present in the body, falling back
	 * to the local part of the userPrincipalName when neither is set.
	 *
	 * @param body              the body being built
	 * @param userprincipalname the UPN, used as last resort
	 * @return a display name, never null
	 */
	private static String buildDisplayName(ObjectNode body, String userprincipalname) {

		String givenname = body.has("givenName") ? body.get("givenName").asText("") : "";
		String surname = body.has("surname") ? body.get("surname").asText("") : "";
		String displayname = (givenname + " " + surname).trim();

		return displayname.isEmpty() ? userprincipalname.split("@")[0] : displayname;
	}

	/**
	 * Generates a password that satisfies the Entra ID rules (8-256 characters, at least three of the
	 * four character classes). Used only when no password was supplied, so that creating an account
	 * does not fail on a missing one.
	 *
	 * @return a generated password of 18 characters covering all four character classes
	 */
	private static String generatePassword() {

		String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
		String lower = "abcdefghijkmnpqrstuvwxyz";
		String digits = "23456789";
		String symbols = "!@#$%*-_+=?";
		String all = upper + lower + digits + symbols;

		SecureRandom random = new SecureRandom();
		List<Character> characters = new ArrayList<>();

		// guarantee one character from each class, fill the rest at random
		characters.add(upper.charAt(random.nextInt(upper.length())));
		characters.add(lower.charAt(random.nextInt(lower.length())));
		characters.add(digits.charAt(random.nextInt(digits.length())));
		characters.add(symbols.charAt(random.nextInt(symbols.length())));
		while (characters.size() < 18)
			characters.add(all.charAt(random.nextInt(all.length())));

		Collections.shuffle(characters, random);

		StringBuilder password = new StringBuilder();
		characters.forEach(password::append);

		return password.toString();
	}

	/**
	 * Strips everything from a local part that Entra ID does not accept in a userPrincipalName.
	 * Umlauts and other accented characters are decomposed and their diacritics removed, so "Müller"
	 * becomes "mueller"-like "muller"; everything left over besides letters, digits, dot, dash and
	 * underscore is dropped. The provisioning extension normally normalizes the name already, this is
	 * the fallback for a caller that configures nothing.
	 *
	 * @param localpart the local part to clean up
	 * @return the cleaned local part
	 */
	private String normalizeUPNLocalPart(String localpart) {

		String normalized = java.text.Normalizer.normalize(localpart, java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
				.replace("\u00df", "ss")
				.toLowerCase();

		normalized = normalized.replaceAll("[^a-z0-9._-]", "");

		return normalized.isBlank() ? localpart : normalized;
	}

	/**
	 * Turns a failed response into a message worth logging.
	 * <p>
	 * Graph does not always answer a refused request with an error body. The status message is then
	 * empty and says nothing at all - which reason it was (a missing permission, a missing licence, a
	 * feature the tenant does not have) cannot be told apart. In that case the HTTP status code is the
	 * only thing left, so it is reported instead of an empty string.
	 *
	 * @param response the response to describe, may be null
	 * @return the error message, never blank
	 */
	private static String describeError(MSGraphResponse response) {

		if (response == null)
			return "ERROR_NO_RESPONSE";

		String message = response.getStatusmessage();
		if (message != null && !message.isBlank())
			return message;

		int statuscode = response.getStatuscode();
		return statuscode > 0 ? "HTTP " + statuscode + " (no response body)" : "ERROR_NO_RESPONSE";
	}

	/**
	 * Builds the {@code $select} value Graph is asked for.
	 * <p>
	 * A configured field may name a path inside a nested object, e.g.
	 * {@code signInActivity.lastSignInDateTime}. Graph does not accept sub-properties in {@code $select},
	 * only the property itself, so every path is cut down to its first segment and duplicates are
	 * dropped. Plain field names pass through unchanged.
	 *
	 * @param select the configured comma separated field list
	 * @return the field list to send to Graph
	 */
	private static String buildGraphSelect(String select) {

		if (select == null || select.isBlank())
			return select;

		List<String> fields = new ArrayList<String>();

		for (String field : select.split(",")) {
			field = field.trim();
			if (field.isEmpty())
				continue;

			int dot = field.indexOf('.');
			if (dot > 0)
				field = field.substring(0, dot);

			if (!fields.contains(field))
				fields.add(field);
		}

		return String.join(",", fields);
	}

	/**
	 * Reads a configured field out of one result element.
	 * <p>
	 * A field may appear more than once - {@code methodsRegistered} in the authentication report is a
	 * list of strings, {@code assignedLicenses} a list of objects. All values found are collected and
	 * joined with {@value #MULTIVALUE_SEPARATOR}, so nothing is silently dropped.
	 * <p>
	 * Handles three cases the plain lookup did not: a dotted path into a nested object (e.g.
	 * {@code signInActivity.lastSignInDateTime}), a field Graph left out of the answer entirely - which
	 * it does for an empty value, so the node is simply missing - and a nested object addressed without
	 * a path, which has no text of its own.
	 *
	 * @param element the result element
	 * @param field   the configured field name, optionally a dotted path
	 * @return the value, or an empty string if it is not present
	 */
	private static String readSelectedValue(XML element, String field) {

		if (element == null || field == null || field.isBlank())
			return "";

		List<String> values = new ArrayList<String>();
		collectSelectedValues(element, field.trim().split("\\."), 0, values);

		return String.join(MULTIVALUE_SEPARATOR, values);
	}

	/**
	 * Walks one path segment and collects every value found, following repeated nodes at each level.
	 *
	 * @param element  the element to read from
	 * @param segments the path segments
	 * @param index    the segment to handle now
	 * @param values   collects the results
	 */
	private static void collectSelectedValues(XML element, String[] segments, int index, List<String> values) {

		if (element == null)
			return;

		if (index >= segments.length) {
			String value = element.getText();
			if (value != null && !value.isBlank())
				values.add(value);
			return;
		}

		String segment = segments[index];
		if (segment.isEmpty()) {
			collectSelectedValues(element, segments, index + 1, values);
			return;
		}

		List<XML> children = element.findNodes(segment);
		if (children == null || children.isEmpty())
			return;

		for (XML child : children)
			collectSelectedValues(child, segments, index + 1, values);
	}

	/**
	 * Determines the phone number to register when the caller did not name one.
	 * <p>
	 * First an explicitly supplied {@code mobilePhone} attribute is used, then the value stored on the
	 * account in Entra ID. Returning null means neither exists.
	 *
	 * @param thislog         Logger instance
	 * @param session         an open Graph session
	 * @param coRequest       the current request
	 * @param accountuniqueid the account's Object-ID
	 * @return the phone number, or null if none could be determined
	 * @throws Exception if the request could not be sent at all
	 */
	private String resolveMobilePhone(Logger thislog, MSGraphSession session, ConnectorRequest coRequest, String accountuniqueid)
			throws Exception {

		String supplied = getIdentifier(coRequest, "mobilePhone");
		if (supplied != null && !supplied.isBlank()) {
			thislog.debug("resolveMobilePhone()...using the supplied mobilePhone");
			return supplied.trim();
		}

		MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_GET, "users/" + accountuniqueid + "?$select=mobilePhone");
		MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

		if (response == null || !response.wasSuccess()) {
			thislog.warn("resolveMobilePhone()...could not read mobilePhone: " + describeError(response));
			return null;
		}

		String mobilephone = response.getDataAsXML().getValue("mobilePhone");
		if (mobilephone == null || mobilephone.isBlank())
			return null;

		thislog.debug("resolveMobilePhone()...using the account's mobilePhone");
		return mobilephone.trim();
	}

	/**
	 * Fields a reconcile asks for on an account, matching what the EntraID collector reads by default.
	 * Overridable through "reconcileattributes"; fields Entra ID does not know are dropped by Graph
	 * without an error, so a wrong entry stays invisible.
	 */
	private static final String DEFAULT_RECONCILE_ATTRIBUTES_ACCOUNT =
			"id,userPrincipalName,displayName,givenName,surname,mail,userType,accountEnabled,companyName," +
			"department,jobTitle,officeLocation,employeeId,mobilePhone,usageLocation,assignedLicenses," +
			"onPremisesSyncEnabled,onPremisesSamAccountName,createdDateTime";

	/** Fields a reconcile asks for on a security object. */
	private static final String DEFAULT_RECONCILE_ATTRIBUTES_SECURITYOBJECT =
			"id,displayName,description,mail,mailNickname,mailEnabled,securityEnabled,groupTypes,createdDateTime";

	/** Characters RFC 3986 allows in a path or query unencoded, besides letters and digits. */
	private static final String URI_SAFE_CHARACTERS = "-._~!$&'()*+,;=:@/?";

	/**
	 * Percent-encodes a resource so it can be written the way the Graph documentation reads it.
	 * <p>
	 * A custom command carries path, filter and select in one string, so the caller cannot hand the
	 * parts over separately the way the collection parameters do. A space in a filter would end in
	 * {@code URISyntaxException: Illegal character in query} before the request is ever sent.
	 * <p>
	 * Only characters that are not allowed unencoded are replaced. Anything already written as a
	 * percent escape is recognised and left alone, so a hand-encoded resource keeps working and nothing
	 * is encoded twice.
	 *
	 * @param resource the resource as configured
	 * @return the resource, safe to put into a URI
	 */
	private static String encodeGraphResource(String resource) {

		if (resource == null || resource.isBlank())
			return resource;

		StringBuilder encoded = new StringBuilder(resource.length() + 16);

		for (int i = 0; i < resource.length(); i++) {
			char character = resource.charAt(i);

			// Keep an existing percent escape as it is instead of encoding its "%" again.
			if (character == '%' && i + 2 < resource.length()
					&& isHexDigit(resource.charAt(i + 1)) && isHexDigit(resource.charAt(i + 2))) {
				encoded.append(resource, i, i + 3);
				i += 2;
				continue;
			}

			if (Character.isLetterOrDigit(character) && character < 128) {
				encoded.append(character);
				continue;
			}

			if (URI_SAFE_CHARACTERS.indexOf(character) >= 0) {
				encoded.append(character);
				continue;
			}

			for (byte encodedbyte : String.valueOf(character).getBytes(StandardCharsets.UTF_8))
				encoded.append('%').append(String.format("%02X", encodedbyte & 0xFF));
		}

		return encoded.toString();
	}

	/**
	 * @param character the character to test
	 * @return true if the character is a hexadecimal digit
	 */
	private static boolean isHexDigit(char character) {
		return (character >= '0' && character <= '9')
				|| (character >= 'a' && character <= 'f')
				|| (character >= 'A' && character <= 'F');
	}

	/** Separates the values of a field that Graph returns more than once. */
	private static final String MULTIVALUE_SEPARATOR = ";";

	/** Attributes Entra ID masters in the cloud, writable even on a synchronized account. */
	private static final String DEFAULT_CLOUD_MASTERED_ATTRIBUTES = "usageLocation,preferredLanguage";

	/**
	 * Protects a write against an account that is mastered on premise.
	 * <p>
	 * In a hybrid tenant most accounts carry {@code onPremisesSyncEnabled = true}. Graph rejects a PATCH
	 * on their on premise mastered attributes, the provisioning task ends on "error" and then blocks the
	 * next assignment until someone deletes it. This check runs before the PATCH and is steered by
	 * "syncedaccount_policy":
	 * <ul>
	 * <li>"skipattributes" (default) - drop the attributes that cannot be written and keep the rest</li>
	 * <li>"fail" - refuse the whole write with ERROR_ACCOUNT_IS_SYNCED</li>
	 * <li>"ignore" - no check at all, the previous behaviour</li>
	 * </ul>
	 * The sync state is read live from Graph. Only if that read fails does the check fall back to the
	 * value daccord collected ("SYNCENABLED"), which is only as fresh as the last collector run. If
	 * neither is available the write goes ahead unchanged - the check must never block on its own doubt.
	 *
	 * @param thislog    Logger instance
	 * @param session    an open Graph session
	 * @param coRequest  the current request
	 * @param methodName calling method, for the log
	 * @param id         the account's Object-ID
	 * @param body       the body about to be sent, modified in place under "skipattributes"
	 * @return true if the PATCH should be sent, false if nothing is left to write
	 * @throws Exception ERROR_ACCOUNT_IS_SYNCED under the policy "fail"
	 */
	private boolean applySyncedAccountPolicy(Logger thislog, MSGraphSession session, ConnectorRequest coRequest, String methodName, String id,
			ObjectNode body) throws Exception {

		// Steering values arrive as named variables, or as attributes from the provisioning extension 1.0;
		// getIdentifier() covers both prefixes (see the parameter contract).
		String policy = getIdentifier(coRequest, "syncedaccount_policy");
		if (policy == null || policy.isBlank())
			policy = "skipattributes";
		if ("ignore".equalsIgnoreCase(policy))
			return true;

		Boolean synced = isAccountSynchronized(thislog, session, coRequest, id);
		if (synced == null) {
			String collected = getConfig(coRequest, "syncenabled", coRequest.getParameter(ACCOUNT_DATA_PREFIX + "SYNCENABLED"));
			if (collected == null || collected.isBlank()) {
				thislog.warn(methodName + "()...sync state unknown, writing without the synced-account check");
				return true;
			}
			synced = "true".equalsIgnoreCase(collected) || "active".equalsIgnoreCase(collected);
			thislog.info(methodName + "()...sync state not readable, using the collected value: " + collected);
		}

		if (!synced.booleanValue())
			return true;

		if ("fail".equalsIgnoreCase(policy)) {
			thislog.error(methodName + "()...account " + id + " is mastered on premise, policy is 'fail'");
			throw new Exception("ERROR_ACCOUNT_IS_SYNCED");
		}

		Set<String> writable = new HashSet<String>();
		String cloudattributes = getIdentifier(coRequest, "syncedaccount_cloudattributes");
		if (cloudattributes == null || cloudattributes.isBlank())
			cloudattributes = DEFAULT_CLOUD_MASTERED_ATTRIBUTES;

		for (String attribute : cloudattributes.split(","))
			if (!attribute.isBlank())
				writable.add(attribute.trim().toLowerCase());

		List<String> removed = new ArrayList<String>();
		Iterator<String> fields = body.fieldNames();
		while (fields.hasNext()) {
			String field = fields.next();
			if (!writable.contains(field.toLowerCase())) {
				removed.add(field);
				fields.remove();
			}
		}

		if (!removed.isEmpty())
			thislog.warn(methodName + "()...account " + id + " is mastered on premise, skipping attributes: " + String.join(", ", removed));

		if (body.isEmpty()) {
			thislog.info(methodName + "()...nothing writable left for the synchronized account, no request sent");
			return false;
		}

		return true;
	}

	/**
	 * Reads whether an account is synchronized from on premise.
	 *
	 * @param thislog   Logger instance
	 * @param session   an open Graph session
	 * @param coRequest the current request
	 * @param id        the account's Object-ID
	 * @return TRUE or FALSE, or null if the state could not be read
	 * @throws Exception if the request could not be sent at all
	 */
	private Boolean isAccountSynchronized(Logger thislog, MSGraphSession session, ConnectorRequest coRequest, String id) throws Exception {

		MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_GET, "users/" + id + "?$select=onPremisesSyncEnabled");
		MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

		if (response == null || !response.wasSuccess())
			return null;

		String value = response.getDataAsXML().getValue("onPremisesSyncEnabled");
		if (value == null || value.isBlank())
			return Boolean.FALSE;

		return Boolean.valueOf("true".equalsIgnoreCase(value));
	}

	/** Default number of attempts the "unique" strategy makes before giving up. */
	private static final int UPN_ATTEMPT_LIMIT = 20;

	/**
	 * Finds a userPrincipalName that is not taken yet, for the "unique" strategy. The first attempt uses
	 * the base name unchanged, every further one appends an increasing counter:
	 * {@code max.mustermann@example.com}, {@code max.mustermann2@example.com}, and so on.
	 *
	 * @param thislog    Logger instance
	 * @param session    an open Graph session
	 * @param coRequest  the current request, used for the retry handling
	 * @param base       local part to start from, e.g. "max.mustermann"
	 * @param domainname domain to append
	 * @param limit      number of attempts before giving up
	 * @return the first free userPrincipalName
	 * @throws Exception ERROR_NO_FREE_USERPRINCIPALNAME when every attempt is taken
	 */
	private String findFreeUserPrincipalName(Logger thislog, MSGraphSession session, ConnectorRequest coRequest, String base, String domainname, int limit)
			throws Exception {

		for (int attempt = 1; attempt <= limit; attempt++) {
			String candidate = (attempt == 1 ? base : base + attempt) + "@" + domainname;

			if (findUserIdByPrincipalName(thislog, session, coRequest, candidate) == null) {
				if (attempt > 1)
					thislog.info("createAccount()...userPrincipalName taken, using free one after " + attempt + " attempts: " + candidate);
				return candidate;
			}
		}

		thislog.error("createAccount()...no free userPrincipalName found for '" + base + "' within " + limit + " attempts");
		throw new Exception("ERROR_NO_FREE_USERPRINCIPALNAME");
	}

	/**
	 * Looks up a user by its userPrincipalName. Used by the "shared" strategy to reuse an existing
	 * account instead of running into a duplicate error, mirroring the LDAP connector's behaviour.
	 *
	 * @param thislog           Logger instance
	 * @param session           an open Graph session
	 * @param coRequest         the current request, used for the retry handling
	 * @param userprincipalname the UPN to look for
	 * @return the object id of the existing user, or null when there is none
	 */
	private String findUserIdByPrincipalName(Logger thislog, MSGraphSession session, ConnectorRequest coRequest, String userprincipalname)
			throws Exception {

		MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_GET, "users/" + userprincipalname + "?$select=id");
		MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

		if (response == null || !response.wasSuccess())
			return null;

		String id = response.getDataAsXML().getValue("id");
		return (id == null || id.isEmpty()) ? null : id;
	}

	/**
	 * Reads a steering parameter, e.g. "accountname", "domainname" or "upnbase". The provisioning
	 * extension passes it as a named GadgetAPI variable, which arrives with the
	 * {@value #CONFIG_PARAMETER_PREFIX} prefix (names in lowercase). A provisioning extension from
	 * before daccord 2.3 could only send it inside the data XML, so the
	 * {@value #ACCOUNT_DATA_PREFIX} attribute of the same name is read as a fallback.
	 *
	 * @param coRequest    the request to read from
	 * @param name         parameter name without prefix
	 * @param defaultvalue value to return when the parameter is absent or blank
	 * @return the parameter value, or defaultvalue
	 */
	private static String getConfig(ConnectorRequest coRequest, String name, String defaultvalue) {

		String value = getIdentifier(coRequest, name);
		return (value == null || value.isBlank()) ? defaultvalue : value;
	}

	/**
	 * Writes a value into the body only if that field was not already supplied as a
	 * {@value #ACCOUNT_DATA_PREFIX} attribute. This keeps the mandatory fields derived here as
	 * defaults while still allowing an explicit attribute from the provisioning configuration to win.
	 *
	 * @param body      the body being built
	 * @param coRequest the request, used to check for an explicit attribute
	 * @param fieldPath the (possibly dotted) Graph field path
	 * @param value     the derived value; ignored when null or blank
	 */
	private static void setUnlessProvided(ObjectNode body, ConnectorRequest coRequest, String fieldPath, String value) {

		if (value == null || value.isBlank())
			return;

		String explicit = coRequest.getParameter(ACCOUNT_DATA_PREFIX + fieldPath);
		if (explicit != null && !explicit.isBlank())
			return;

		setNestedValue(body, fieldPath, value);
	}

	/**
	 * Sets a value in the given {@link ObjectNode}, creating nested objects for dotted paths.
	 * E.g. dottedPath "passwordProfile.password" creates/reuses a nested "passwordProfile" object
	 * and sets its "password" field.
	 *
	 * @param root       the root node to set the value in
	 * @param dottedPath the (possibly dotted) field path
	 * @param value      the value to set; "true"/"false" (case-insensitive) become JSON booleans
	 */
	private static void setNestedValue(ObjectNode root, String dottedPath, String value) {
		String[] segments = dottedPath.split("\\.");
		ObjectNode current = root;
		for (int i = 0; i < segments.length - 1; i++) {
			JsonNode child = current.get(segments[i]);
			if (child == null || !child.isObject()) {
				child = current.putObject(segments[i]);
			}
			current = (ObjectNode) child;
		}
		String lastSegment = segments[segments.length - 1];
		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
			current.put(lastSegment, Boolean.parseBoolean(value));
		} else {
			current.put(lastSegment, value);
		}
	}

	/**
	 * Creates a new user account in Entra ID via {@code POST /users}.
	 * <p>
	 * The fields Graph requires for a user are derived here from the steering parameters, so that the
	 * provisioning extension only has to supply values, not Entra ID field names:
	 * <ul>
	 * <li>{@code mailNickname} from accountname</li>
	 * <li>{@code userPrincipalName} from accountname + "@" + domainname (default: tenant name gp_param3)</li>
	 * <li>{@code displayName} from the attributes givenName + " " + surname</li>
	 * <li>{@code givenName} / {@code surname} from the respective steering parameters</li>
	 * <li>{@code accountEnabled} from accountenabled (default "true")</li>
	 * <li>{@code passwordProfile} from the attribute PASSWORD plus forcechangepasswordnextsignin</li>
	 * </ul>
	 * Every one of these can be overridden by passing the Graph field explicitly as a pp_data_ attribute.
	 * All further pp_data_ attributes are merged into the body unchanged, which is how additional
	 * Entra ID attributes are configured without changing code.
	 * <p>
	 * The strategy steers how an already existing userPrincipalName is handled:
	 * <ul>
	 * <li>"standalone" (default) - create unconditionally, a duplicate ends in an error</li>
	 * <li>"shared" - reuse the existing account and return its id</li>
	 * <li>"unique" - append an increasing counter until a free name is found, at most UPNLIMIT
	 * attempts (default 20)</li>
	 * </ul>
	 *
	 * @param thislog    Logger instance
	 * @param coRequest  Request with connection, steering parameters and account data
	 * @param coResponse Response object (not used)
	 * @return XML with the created account's data on success, or an error otherwise
	 * @throws Exception if the request could not be sent at all
	 */
	@Override
	public XML createAccount(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		thislog.info("createAccount()...[running]");

		XML xmlResponse = new XML("response");

		try {
			// The provisioning extension normally supplies the userPrincipalName. Without it the
			// connector falls back to the account name plus the tenant domain from the connection
			// parameters, so a caller that configures nothing still gets a working account.
			String accountname = getConfig(coRequest, "accountname", null);
			String userprincipalname = coRequest.getParameter(ACCOUNT_DATA_PREFIX + "userPrincipalName");

			if (userprincipalname == null || userprincipalname.isBlank()) {
				String domainname = getConfig(coRequest, "domainname", coRequest.getParameter("gp_param3"));
				if (accountname != null && !accountname.isBlank() && domainname != null && !domainname.isBlank())
					userprincipalname = accountname + "@" + domainname;
			}

			if (userprincipalname == null || userprincipalname.isBlank())
				throw new Exception("ERROR_MISSING_USERPRINCIPALNAME");

			MSGraphSession session = getOrCreateSession(coRequest);

			String strategy = getConfig(coRequest, "strategy", "standalone");

			// strategy "shared": reuse an existing account instead of failing on a duplicate
			if ("shared".equalsIgnoreCase(strategy)) {
				String existingid = findUserIdByPrincipalName(thislog, session, coRequest, userprincipalname);
				if (existingid != null) {
					thislog.info("createAccount()...account already exists, reusing: " + existingid);
					xmlResponse.appendNode("success", "true");
					xmlResponse.appendNode("objectuniqueid", existingid);
					return xmlResponse;
				}
			}

			// strategy "unique": never reuse and never fail on a duplicate, look for a free name instead.
			// The base name comes from "upnbase" if the provisioning extension supplied one (as variable,
			// or as attribute UPNBASE before daccord 2.3), otherwise it is derived from given name and
			// surname, and as a last resort from the userPrincipalName itself.
			if ("unique".equalsIgnoreCase(strategy)) {
				String base = getConfig(coRequest, "upnbase", coRequest.getParameter(ACCOUNT_DATA_PREFIX + "UPNBASE"));

				if (base == null || base.isBlank()) {
					String givenname = coRequest.getParameter(ACCOUNT_DATA_PREFIX + "givenName");
					String surname = coRequest.getParameter(ACCOUNT_DATA_PREFIX + "surname");
					if (givenname != null && !givenname.isBlank() && surname != null && !surname.isBlank())
						base = givenname.trim() + "." + surname.trim();
				}

				if (base == null || base.isBlank())
					base = userprincipalname.substring(0, userprincipalname.indexOf('@'));

				base = normalizeUPNLocalPart(base);
				String domainname = userprincipalname.substring(userprincipalname.indexOf('@') + 1);

				int limit = UPN_ATTEMPT_LIMIT;
				String configuredlimit = getConfig(coRequest, "upnlimit", coRequest.getParameter(ACCOUNT_DATA_PREFIX + "UPNLIMIT"));
				if (configuredlimit != null && !configuredlimit.isBlank()) {
					try {
						limit = Integer.parseInt(configuredlimit.trim());
					} catch (NumberFormatException e) {
						thislog.warn("createAccount()...UPNLIMIT '" + configuredlimit + "' is not a number, using " + limit);
					}
					if (limit < 1) {
						thislog.warn("createAccount()...UPNLIMIT '" + configuredlimit + "' is below 1, using " + UPN_ATTEMPT_LIMIT);
						limit = UPN_ATTEMPT_LIMIT;
					}
				}

				userprincipalname = findFreeUserPrincipalName(thislog, session, coRequest, base, domainname, limit);
			}

			ObjectNode body = buildBodyFromParameters(coRequest, Set.of("PASSWORD", "UPNBASE", "UPNLIMIT"));

			// Fallbacks for the fields Graph requires. Anything supplied as a pp_data_ attribute wins,
			// so the defaults only fill the gaps.
			// With "unique" the name found by the search is authoritative - an explicitly supplied
			// userPrincipalName only serves as the starting point and must not win here.
			if ("unique".equalsIgnoreCase(strategy))
				body.put("userPrincipalName", userprincipalname);
			else
				setUnlessProvided(body, coRequest, "userPrincipalName", userprincipalname);
			setUnlessProvided(body, coRequest, "mailNickname",
					"unique".equalsIgnoreCase(strategy) || accountname == null || accountname.isBlank()
							? userprincipalname.split("@")[0]
							: accountname);
			setUnlessProvided(body, coRequest, "displayName", buildDisplayName(body, userprincipalname));
			setUnlessProvided(body, coRequest, "accountEnabled", getConfig(coRequest, "accountenabled", "true"));

			// A password is mandatory for a new user. Without one a compliant password is generated, so
			// the account can be created; the user has to change it at first sign-in anyway.
			String password = coRequest.getParameter(ACCOUNT_DATA_PREFIX + "PASSWORD");
			if (password == null || password.isBlank()) {
				password = generatePassword();
				thislog.info("createAccount()...no password supplied, generated one");
			}
			setUnlessProvided(body, coRequest, "passwordProfile.password", password);
			setUnlessProvided(body, coRequest, "passwordProfile.forceChangePasswordNextSignIn",
					getConfig(coRequest, "forcechangepasswordnextsignin", "true"));

			String jsonBody = body.toString();
			thislog.debug("createAccount()...[body]: " + maskJsonSecrets(jsonBody));

			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_POST, "users");
			request.setBody(jsonBody);

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info("createAccount()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
				String objectuniqueid = response.getDataAsXML().getValue("id");
				if (objectuniqueid != null && !objectuniqueid.isEmpty()) {
					xmlResponse.appendNode("objectuniqueid", objectuniqueid);
				}
				xmlResponse.appendXML(response.getDataAsXML());
			} else {
				String errorMessage = describeError(response);
				thislog.error("createAccount()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error("createAccount()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	/**
	 * Modifies an existing user account in Entra ID via {@code PATCH /users/{id}}.
	 * <p>
	 * PARAMETERS:
	 * - gp_param1..10, gp_pwparam1..2: connection parameters (see {@link #checkConnection})
	 * - accountuniqueid: the Entra ID Object-ID of the user to modify (REQUIRED!)
	 * - pp_data_*: account fields to update, same mapping as {@link #createAccount} (see
	 *   {@link #buildJsonBodyFromParameters(ConnectorRequest, Set)})
	 *
	 * @param thislog    Logger instance
	 * @param coRequest  Request with connection data and the account fields to update
	 * @param coResponse Response object (not used)
	 * @return XML with success/error information
	 * @throws Exception if the request could not be sent at all
	 */
	@Override
	public XML modifyAccount(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		thislog.info("modifyAccount()...[running]");

		XML xmlResponse = new XML("response");

		try {
			String accountuniqueid = getIdentifier(coRequest, "accountuniqueid");
			if (accountuniqueid == null || accountuniqueid.isBlank()) {
				throw new Exception("ERROR_MISSING_ACCOUNTUNIQUEID");
			}

			ObjectNode body = buildBodyFromParameters(coRequest, Set.of("accountuniqueid", "PASSWORD", "SYNCENABLED", "syncedaccount_policy", "syncedaccount_cloudattributes"));

			String givenname = getConfig(coRequest, "givenname", null);
			String surname = getConfig(coRequest, "surname", null);
			setUnlessProvided(body, coRequest, "givenName", givenname);
			setUnlessProvided(body, coRequest, "surname", surname);
			if (givenname != null || surname != null) {
				setUnlessProvided(body, coRequest, "displayName",
						((givenname == null ? "" : givenname) + " " + (surname == null ? "" : surname)).trim());
			}

			String password = coRequest.getParameter(ACCOUNT_DATA_PREFIX + "PASSWORD");
			if (password != null && !password.isBlank()) {
				setUnlessProvided(body, coRequest, "passwordProfile.password", password);
				setUnlessProvided(body, coRequest, "passwordProfile.forceChangePasswordNextSignIn",
						getConfig(coRequest, "forcechangepasswordnextsignin", "true"));
			}

			MSGraphSession session = getOrCreateSession(coRequest);

			// A hybrid tenant masters most accounts on premise; writing to those has to be filtered.
			if (!applySyncedAccountPolicy(thislog, session, coRequest, "modifyAccount", accountuniqueid, body)) {
				xmlResponse.appendNode("success", "true");
				return xmlResponse;
			}

			String jsonBody = body.toString();
			thislog.debug("modifyAccount()...[body]: " + maskJsonSecrets(jsonBody));

			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_PATCH, "users/" + accountuniqueid);
			request.setBody(jsonBody);

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info("modifyAccount()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
			} else {
				String errorMessage = describeError(response);
				thislog.error("modifyAccount()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error("modifyAccount()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	/**
	 * Deletes a user account in Entra ID via {@code DELETE /users/{id}}.
	 * <p>
	 * PARAMETERS:
	 * - gp_param1..10, gp_pwparam1..2: connection parameters (see {@link #checkConnection})
	 * - accountuniqueid: the Entra ID Object-ID of the user to delete (REQUIRED!) - same
	 *   convention as "accountuniqueid" in {@code examples/permission-create.xml}.
	 *
	 * @param thislog    Logger instance
	 * @param coRequest  Request with connection data and the account to delete
	 * @param coResponse Response object (not used)
	 * @return XML with success/error information
	 * @throws Exception if the request could not be sent at all
	 */
	@Override
	public XML deleteAccount(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		thislog.info("deleteAccount()...[running]");

		XML xmlResponse = new XML("response");

		try {
			String accountuniqueid = getIdentifier(coRequest, "accountuniqueid");
			if (accountuniqueid == null || accountuniqueid.isBlank()) {
				throw new Exception("ERROR_MISSING_ACCOUNTUNIQUEID");
			}

			MSGraphSession session = getOrCreateSession(coRequest);
			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_DELETE, "users/" + accountuniqueid);

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info("deleteAccount()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
			} else {
				String errorMessage = describeError(response);
				thislog.error("deleteAccount()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error("deleteAccount()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	@Override
	public XML modifyAccountPassword(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		thislog.info("modifyAccountPassword()...[running]");

		XML xmlResponse = new XML("response");

		try {
			String accountuniqueid = getIdentifier(coRequest, "accountuniqueid");
			if (accountuniqueid == null || accountuniqueid.isBlank()) {
				throw new Exception("ERROR_MISSING_ACCOUNTUNIQUEID");
			}

			// "PASSWORD" follows the naming the LDAP connector uses; the lower-case variant stays
			// supported so existing provisioning configurations keep working.
			String password = coRequest.getParameter(ACCOUNT_DATA_PREFIX + "PASSWORD");
			if (password == null || password.isBlank()) {
				password = coRequest.getParameter(ACCOUNT_DATA_PREFIX + "password");
			}
			if (password == null || password.isBlank()) {
				throw new Exception("ERROR_MISSING_PASSWORD");
			}

			String forceChange = getConfig(coRequest, "forcechangepasswordnextsignin",
					coRequest.getParameter(ACCOUNT_DATA_PREFIX + "forcechangepasswordnextsignin"));
			if (forceChange == null || forceChange.isBlank()) {
				forceChange = "true";
			}

			ObjectMapper mapper = new ObjectMapper();
			ObjectNode body = mapper.createObjectNode();
			setNestedValue(body, "passwordProfile.password", password);
			setNestedValue(body, "passwordProfile.forceChangePasswordNextSignIn", forceChange);
			String jsonBody = body.toString();
			thislog.debug("modifyAccountPassword()...[body]: " + "{\"passwordProfile\":{\"password\":\"***\",\"forceChangePasswordNextSignIn\":" + forceChange + "}}");

			MSGraphSession session = getOrCreateSession(coRequest);
			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_PATCH, "users/" + accountuniqueid);
			request.setBody(jsonBody);

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info("modifyAccountPassword()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
			} else {
				String errorMessage = describeError(response);
				thislog.error("modifyAccountPassword()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error("modifyAccountPassword()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	/**
	 * Enables or disables a user account in Entra ID via {@code PATCH /users/{id}} (sets "accountEnabled").
	 * <p>
	 * PARAMETERS:
	 * - gp_param1..10, gp_pwparam1..2: connection parameters (see {@link #checkConnection})
	 * - accountuniqueid: the Entra ID Object-ID of the user (REQUIRED!)
	 * - pp_data_accountEnabled: "true" to enable, "false" to disable (REQUIRED!)
	 *
	 * @param thislog    Logger instance
	 * @param coRequest  Request with connection data, the account and the new status
	 * @param coResponse Response object (not used)
	 * @return XML with success/error information
	 * @throws Exception if the request could not be sent at all
	 */
	@Override
	public XML modifyAccountStatus(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		thislog.info("modifyAccountStatus()...[running]");

		XML xmlResponse = new XML("response");

		try {
			String accountuniqueid = getIdentifier(coRequest, "accountuniqueid");
			if (accountuniqueid == null || accountuniqueid.isBlank()) {
				throw new Exception("ERROR_MISSING_ACCOUNTUNIQUEID");
			}

			String accountEnabled = getConfig(coRequest, "accountenabled", coRequest.getParameter(ACCOUNT_DATA_PREFIX + "accountEnabled"));
			if (accountEnabled == null || accountEnabled.isBlank()) {
				throw new Exception("ERROR_MISSING_ACCOUNTENABLED");
			}

			ObjectMapper mapper = new ObjectMapper();
			ObjectNode body = mapper.createObjectNode();
			setNestedValue(body, "accountEnabled", accountEnabled);
			String jsonBody = body.toString();
			thislog.debug("modifyAccountStatus()...[body]: " + maskJsonSecrets(jsonBody));

			MSGraphSession session = getOrCreateSession(coRequest);

			// accountEnabled is mastered on premise for a synchronized account and cannot be changed in
			// the cloud at all. Unlike an attribute write there is nothing to fall back to, so this always
			// refuses instead of reporting a success that never happened. "ignore" switches it off.
			String policy = getIdentifier(coRequest, "syncedaccount_policy");
			if (!"ignore".equalsIgnoreCase(policy == null ? "" : policy)) {
				Boolean synced = isAccountSynchronized(thislog, session, coRequest, accountuniqueid);
				if (synced == null) {
					String collected = getConfig(coRequest, "syncenabled", coRequest.getParameter(ACCOUNT_DATA_PREFIX + "SYNCENABLED"));
					if (collected != null && !collected.isBlank())
						synced = Boolean.valueOf("true".equalsIgnoreCase(collected) || "active".equalsIgnoreCase(collected));
				}

				if (synced != null && synced.booleanValue()) {
					thislog.error("modifyAccountStatus()...account " + accountuniqueid +
							" is synchronized from on premise - its status can only be changed in the on-premise Active Directory, Entra ID rejects the write");
					throw new Exception("ERROR_ACCOUNT_IS_SYNCED_STATUSCHANGE");
				}
			}

			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_PATCH, "users/" + accountuniqueid);
			request.setBody(jsonBody);

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info("modifyAccountStatus()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
			} else {
				String errorMessage = describeError(response);
				thislog.error("modifyAccountStatus()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error("modifyAccountStatus()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	/**
	 * Changes a user's {@code userType} (e.g. "Member"/"Guest") via {@code PATCH /users/{id}}.
	 * Note: Entra ID restricts Member&lt;-&gt;Guest conversion in several scenarios; Graph will return
	 * an error if the change is not permitted for the target user.
	 * <p>
	 * PARAMETERS: accountuniqueid (REQUIRED), accounttype (REQUIRED, e.g. "Member"/"Guest") - both named variables
	 */
	@Override
	public XML modifyAccountType(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		thislog.info("modifyAccountType()...[running]");

		XML xmlResponse = new XML("response");

		try {
			String accountuniqueid = getIdentifier(coRequest, "accountuniqueid");
			if (accountuniqueid == null || accountuniqueid.isBlank()) {
				throw new Exception("ERROR_MISSING_ACCOUNTUNIQUEID");
			}

			String accounttype = getConfig(coRequest, "accounttype", coRequest.getParameter(ACCOUNT_DATA_PREFIX + "accounttype"));
			if (accounttype == null || accounttype.isBlank()) {
				throw new Exception("ERROR_MISSING_ACCOUNTTYPE");
			}

			ObjectMapper mapper = new ObjectMapper();
			ObjectNode body = mapper.createObjectNode();
			body.put("userType", accounttype);
			String jsonBody = body.toString();
			thislog.debug("modifyAccountType()...[body]: " + maskJsonSecrets(jsonBody));

			MSGraphSession session = getOrCreateSession(coRequest);
			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_PATCH, "users/" + accountuniqueid);
			request.setBody(jsonBody);

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info("modifyAccountType()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
			} else {
				String errorMessage = describeError(response);
				thislog.error("modifyAccountType()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error("modifyAccountType()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	/**
	 * Renames a user account. Entra ID has no dedicated "rename" operation - this updates whichever
	 * naming fields are supplied (typically displayName and/or userPrincipalName) via the same
	 * {@code PATCH /users/{id}} used by {@link #modifyAccount}.
	 * <p>
	 * PARAMETERS: accountuniqueid (REQUIRED), pp_data_* fields to update (see {@link #createAccount})
	 */
	@Override
	public XML renameAccount(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		return patchResource(thislog, coRequest, "renameAccount", "users", "accountuniqueid");
	}

	/**
	 * "Moves" a user account. Entra ID is flat (no organizational-unit/container concept like on-prem
	 * AD) - this updates organizational attributes (e.g. department, officeLocation) via the same
	 * {@code PATCH /users/{id}} used by {@link #modifyAccount}.
	 * <p>
	 * PARAMETERS: accountuniqueid (REQUIRED), pp_data_* fields to update (see {@link #createAccount})
	 */
	@Override
	public XML moveAccount(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		return patchResource(thislog, coRequest, "moveAccount", "users", "accountuniqueid");
	}

	/**
	 * Reconciles (re-reads) a user account's current state via {@code GET /users/{id}}.
	 * <p>
	 * PARAMETERS: accountuniqueid (REQUIRED), reconcileattributes (optional field list,
	 * defaults to the fields the collector reads)
	 */
	@Override
	public XML reconcileAccount(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		return reconcileResource(thislog, coRequest, "reconcileAccount", "users", "accountuniqueid");
	}

	/**
	 * Runs a named command on an account (action "customcommand"), see {@link #routeCustomCommand}.
	 * <p>
	 * COMMANDS: {@value #COMMAND_SET_AUTHENTICATIONMETHODS}, {@value #COMMAND_GRAPHREQUEST} (only if
	 * enabled in the connection parameters)
	 */
	@Override
	public XML customAccountCommand(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		return routeCustomCommand(thislog, coRequest, coResponse, "customAccountCommand", true);
	}

	/** Command that registers authentication methods (MFA) on an account. */
	private static final String COMMAND_SET_AUTHENTICATIONMETHODS = "set_authenticationmethods";

	/** Command that passes httpmethod/resource/body to Graph unchecked - for tests and analysis only. */
	private static final String COMMAND_GRAPHREQUEST = "graphrequest";

	/** Connection parameter that enables {@value #COMMAND_GRAPHREQUEST}; off unless set to "true". */
	private static final String PARAM_ALLOW_GRAPHREQUEST = "gp_param11";

	/**
	 * Selects the command of a "customcommand" action. The command is a name of this extension, not a
	 * Graph operation: each one translates into the Graph calls it needs and accepts only its own
	 * parameters, so a caller can do exactly what the command is meant for.
	 * <p>
	 * PARAMETERS: command (REQUIRED), further parameters per command. Before daccord 2.3 the command
	 * was implicit: "operation=authenticationmethod" selected the authentication methods, everything
	 * else was a Graph passthrough - both are still understood.
	 * <p>
	 * {@value #COMMAND_GRAPHREQUEST} hands any request to Graph with the rights of the app
	 * registration, so it has to be enabled explicitly with {@value #PARAM_ALLOW_GRAPHREQUEST} = true.
	 */
	private XML routeCustomCommand(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse, String methodName,
			boolean account) throws Exception {

		String command = getConfig(coRequest, "command", null);
		if (command == null) {
			if ("authenticationmethod".equalsIgnoreCase(getConfig(coRequest, "operation", "")))
				command = COMMAND_SET_AUTHENTICATIONMETHODS;
			else if (getConfig(coRequest, "httpmethod", null) != null)
				command = COMMAND_GRAPHREQUEST;
		}
		thislog.info(methodName + "()...command: " + command);

		if (account && COMMAND_SET_AUTHENTICATIONMETHODS.equalsIgnoreCase(command))
			return modifyAccountAuthenticationMethod(thislog, coRequest, coResponse);

		if (COMMAND_GRAPHREQUEST.equalsIgnoreCase(command)) {
			if ("true".equalsIgnoreCase(coRequest.getParameter(PARAM_ALLOW_GRAPHREQUEST)))
				return executeCustomCommand(thislog, coRequest, methodName);
			return errorResponse(thislog, methodName, "ERROR_GRAPHREQUEST_DISABLED");
		}

		return errorResponse(thislog, methodName, command == null ? "ERROR_MISSING_COMMAND" : "ERROR_UNKNOWN_COMMAND: " + command);
	}

	private static XML errorResponse(Logger thislog, String methodName, String error) {
		thislog.error(methodName + "()...[ERROR]: " + error);
		XML xmlResponse = new XML("response");
		xmlResponse.appendNode("success", "false");
		xmlResponse.appendNode("error", error);
		return xmlResponse;
	}

	/** Phone types Entra ID accepts; only the ones listed here may be registered. */
	private static final String DEFAULT_ALLOWED_PHONETYPES = "mobile";

	/**
	 * Registers one or more authentication methods on an account.
	 * <p>
	 * Reached through the "customcommand" action with command {@value #COMMAND_SET_AUTHENTICATIONMETHODS}.
	 * <p>
	 * PARAMETERS:
	 * <ul>
	 * <li>{@code accountuniqueid} - the Entra ID Object-ID of the account (REQUIRED)</li>
	 * <li>{@code authenticationmethods} - the methods to add, separated by ";", each written as
	 * {@code type=value}, e.g. {@code phone=+49 170 1234567;email=max.mustermann@example.com}. A phone
	 * entry may name its type explicitly as {@code phone:office=...}; the default is "mobile".</li>
	 * <li>{@code authenticationmethod_allowedphonetypes} - which phone types may be registered,
	 * default "mobile". Anything else ends in ERROR_UNSUPPORTED_PHONETYPE.</li>
	 * </ul>
	 * Adding methods is not transactional: Graph registers each one on its own. If a later entry fails,
	 * the earlier ones stay registered, so the response names exactly which ones were written.
	 *
	 * @param thislog    Logger instance
	 * @param coRequest  Request with connection data and the methods to register
	 * @param coResponse Response object (not used)
	 * @return XML with the registered methods on success, or an error otherwise
	 * @throws Exception if the request could not be sent at all
	 */
	public XML modifyAccountAuthenticationMethod(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		thislog.info("modifyAccountAuthenticationMethod()...[running]");

		XML xmlResponse = new XML("response");

		// Kept outside the try so a failure can still report what was already written.
		List<String> registered = new ArrayList<String>();

		try {
			String accountuniqueid = getIdentifier(coRequest, "accountuniqueid");
			if (accountuniqueid == null || accountuniqueid.isBlank())
				throw new Exception("ERROR_MISSING_ACCOUNTUNIQUEID");

			String methods = getIdentifier(coRequest, "authenticationmethods");
			if (methods == null || methods.isBlank())
				throw new Exception("ERROR_MISSING_AUTHENTICATIONMETHODS");

			Set<String> allowedphonetypes = new HashSet<String>();
			String configuredphonetypes = getIdentifier(coRequest, "authenticationmethod_allowedphonetypes");
			for (String phonetype : (configuredphonetypes == null || configuredphonetypes.isBlank()
					? DEFAULT_ALLOWED_PHONETYPES : configuredphonetypes).split(","))
				if (!phonetype.isBlank())
					allowedphonetypes.add(phonetype.trim().toLowerCase());

			// First pass: check every entry. Graph registers each method on its own, so a rejection
			// found halfway through would leave the earlier ones behind. Validating up front means a
			// wrong configuration never writes anything at all.
			List<String[]> parsed = new ArrayList<String[]>();

			for (String entry : methods.split(";")) {
				entry = entry.trim();
				if (entry.isEmpty())
					continue;

				int separator = entry.indexOf('=');
				if (separator < 1)
					throw new Exception("ERROR_INVALID_AUTHENTICATIONMETHOD");

				String type = entry.substring(0, separator).trim().toLowerCase();
				String value = entry.substring(separator + 1).trim();

				// A phone entry may be left empty on purpose: the number is then taken from the account.
				if (value.isEmpty() && !type.startsWith("phone"))
					throw new Exception("ERROR_MISSING_AUTHENTICATIONMETHOD_VALUE");

				if (type.startsWith("phone")) {
					String phonetype = type.contains(":") ? type.substring(type.indexOf(':') + 1).trim() : "mobile";

					if (!allowedphonetypes.contains(phonetype)) {
						thislog.error("modifyAccountAuthenticationMethod()...phone type '" + phonetype +
								"' is not allowed, permitted: " + String.join(", ", allowedphonetypes));
						throw new Exception("ERROR_UNSUPPORTED_PHONETYPE");
					}

				} else if (!type.equals("email")) {
					thislog.error("modifyAccountAuthenticationMethod()...method type '" + type +
							"' cannot be registered through Graph, supported: phone, email");
					throw new Exception("ERROR_UNSUPPORTED_AUTHENTICATIONMETHOD");
				}

				parsed.add(new String[] { type, value });
			}

			if (parsed.isEmpty())
				throw new Exception("ERROR_MISSING_AUTHENTICATIONMETHODS");

			// Second pass: write.
			MSGraphSession session = getOrCreateSession(coRequest);

			for (String[] method : parsed) {
				String type = method[0];
				String value = method[1];

				String resource;
				ObjectNode body = new ObjectMapper().createObjectNode();

				if (type.startsWith("phone")) {
					// "phone" alone means the default type, "phone:office" names one explicitly.
					String phonetype = type.contains(":") ? type.substring(type.indexOf(':') + 1).trim() : "mobile";

					// The number has to come from somewhere: the entry itself, an explicitly supplied
					// attribute, or the account's own mobilePhone. Without any of them there is nothing
					// to register and registering a wrong second factor is worse than failing here.
					if (value.isEmpty())
						value = resolveMobilePhone(thislog, session, coRequest, accountuniqueid);

					if (value == null || value.isEmpty()) {
						thislog.error("modifyAccountAuthenticationMethod()...no phone number given and account " +
								accountuniqueid + " has no mobilePhone");
						throw new Exception("ERROR_MISSING_MOBILEPHONE");
					}

					resource = "users/" + accountuniqueid + "/authentication/phoneMethods";
					body.put("phoneNumber", value);
					body.put("phoneType", phonetype);

				} else {
					resource = "users/" + accountuniqueid + "/authentication/emailMethods";
					body.put("emailAddress", value);
				}

				String jsonBody = body.toString();
				thislog.debug("modifyAccountAuthenticationMethod()...[body]: " + maskJsonSecrets(jsonBody));

				MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_POST, resource);
				request.setBody(jsonBody);

				MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

				if (response == null || !response.wasSuccess()) {
					String errorMessage = describeError(response);
					thislog.error("modifyAccountAuthenticationMethod()...[ERROR] for '" + type + "': " + errorMessage);
					xmlResponse.appendNode("success", "false");
					xmlResponse.appendNode("error", errorMessage);
					xmlResponse.appendNode("registered", String.join(", ", registered));
					return xmlResponse;
				}

				registered.add(type);
				thislog.info("modifyAccountAuthenticationMethod()...registered: " + type);
			}

			thislog.info("modifyAccountAuthenticationMethod()...[SUCCESS]");
			xmlResponse.appendNode("success", "true");
			xmlResponse.appendNode("registered", String.join(", ", registered));

		} catch (Exception e) {
			thislog.error("modifyAccountAuthenticationMethod()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
			xmlResponse.appendNode("registered", String.join(", ", registered));
		}

		return xmlResponse;
	}

	/**
	 * Creates a new group (security object) in Entra ID via {@code POST /groups}.
	 * <p>
	 * PARAMETERS: pp_data_* fields to set on the new group, same mapping as {@link #createAccount}.
	 * Graph requires at minimum: displayName, mailEnabled, mailNickname, securityEnabled.
	 */
	@Override
	public XML createSecurityObject(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		thislog.info("createSecurityObject()...[running]");

		XML xmlResponse = new XML("response");

		try {
			// Graph requires displayName, mailNickname, mailEnabled and securityEnabled on a group.
			// They are derived from the steering parameters and can each be overridden by an explicit
			// pp_data_ attribute; everything else is taken over unchanged.
			String securityobjectname = getConfig(coRequest, "securityobjectname", getConfig(coRequest, "accountname", null));

			ObjectNode body = buildBodyFromParameters(coRequest, Set.of());

			setUnlessProvided(body, coRequest, "displayName", securityobjectname);
			setUnlessProvided(body, coRequest, "mailNickname", getConfig(coRequest, "mailnickname", securityobjectname == null ? null : securityobjectname.replaceAll("\\s+", "")));
			setUnlessProvided(body, coRequest, "mailEnabled", getConfig(coRequest, "mailenabled", "false"));
			setUnlessProvided(body, coRequest, "securityEnabled", getConfig(coRequest, "securityenabled", "true"));

			if (!body.has("displayName"))
				throw new Exception("ERROR_MISSING_SECURITYOBJECTNAME");

			String jsonBody = body.toString();
			thislog.debug("createSecurityObject()...[body]: " + maskJsonSecrets(jsonBody));

			MSGraphSession session = getOrCreateSession(coRequest);
			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_POST, "groups");
			request.setBody(jsonBody);

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info("createSecurityObject()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
				String objectuniqueid = response.getDataAsXML().getValue("id");
				if (objectuniqueid != null && !objectuniqueid.isEmpty()) {
					xmlResponse.appendNode("objectuniqueid", objectuniqueid);
				}
				xmlResponse.appendXML(response.getDataAsXML());
			} else {
				String errorMessage = describeError(response);
				thislog.error("createSecurityObject()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error("createSecurityObject()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	/**
	 * Modifies an existing group in Entra ID via {@code PATCH /groups/{id}}.
	 * <p>
	 * PARAMETERS: securityobjectuniqueid (REQUIRED), pp_data_* fields to update (see {@link #createSecurityObject})
	 */
	@Override
	public XML modifySecurityObject(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		return patchResource(thislog, coRequest, "modifySecurityObject", "groups", "securityobjectuniqueid");
	}

	/**
	 * Deletes a group in Entra ID via {@code DELETE /groups/{id}}.
	 * <p>
	 * PARAMETERS: securityobjectuniqueid (REQUIRED)
	 */
	@Override
	public XML deleteSecurityObject(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		thislog.info("deleteSecurityObject()...[running]");

		XML xmlResponse = new XML("response");

		try {
			String securityobjectuniqueid = getIdentifier(coRequest, "securityobjectuniqueid");
			if (securityobjectuniqueid == null || securityobjectuniqueid.isBlank()) {
				throw new Exception("ERROR_MISSING_SECURITYOBJECTUNIQUEID");
			}

			MSGraphSession session = getOrCreateSession(coRequest);
			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_DELETE, "groups/" + securityobjectuniqueid);

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info("deleteSecurityObject()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
			} else {
				String errorMessage = describeError(response);
				thislog.error("deleteSecurityObject()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error("deleteSecurityObject()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	/**
	 * Renames a group. Same {@code PATCH /groups/{id}} as {@link #modifySecurityObject}, typically
	 * updating displayName.
	 * <p>
	 * PARAMETERS: securityobjectuniqueid (REQUIRED), pp_data_* fields to update
	 */
	@Override
	public XML renameSecurityObject(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		return patchResource(thislog, coRequest, "renameSecurityObject", "groups", "securityobjectuniqueid");
	}

	/**
	 * "Moves" a group. Entra ID groups have no container/OU concept - same {@code PATCH /groups/{id}}
	 * as {@link #modifySecurityObject}.
	 * <p>
	 * PARAMETERS: securityobjectuniqueid (REQUIRED), pp_data_* fields to update
	 */
	@Override
	public XML moveSecurityObject(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		return patchResource(thislog, coRequest, "moveSecurityObject", "groups", "securityobjectuniqueid");
	}

	/**
	 * Reconciles (re-reads) a group's current state via {@code GET /groups/{id}}.
	 * <p>
	 * PARAMETERS: securityobjectuniqueid (REQUIRED)
	 */
	@Override
	public XML reconcileSecurityObject(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		return reconcileResource(thislog, coRequest, "reconcileSecurityObject", "groups", "securityobjectuniqueid");
	}

	/**
	 * Runs a named command on a group (action "customcommand"), see {@link #routeCustomCommand}.
	 * <p>
	 * COMMANDS: {@value #COMMAND_GRAPHREQUEST} (only if enabled in the connection parameters)
	 */
	@Override
	public XML customSecurityObjectCommand(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		return routeCustomCommand(thislog, coRequest, coResponse, "customSecurityObjectCommand", false);
	}

	/**
	 * Adds a user to a group (grants a "permission") via {@code POST /groups/{groupid}/members/$ref}.
	 * <p>
	 * PARAMETERS: accountuniqueid (REQUIRED, the user to add), securityobjectuniqueid
	 * (REQUIRED, the target group) - same naming as {@code examples/permission-create.xml}.
	 */
	@Override
	public XML createPermission(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		thislog.info("createPermission()...[running]");

		XML xmlResponse = new XML("response");

		try {
			String accountuniqueid = getIdentifier(coRequest, "accountuniqueid");
			if (accountuniqueid == null || accountuniqueid.isBlank()) {
				throw new Exception("ERROR_MISSING_ACCOUNTUNIQUEID");
			}

			String securityobjectuniqueid = getIdentifier(coRequest, "securityobjectuniqueid");
			if (securityobjectuniqueid == null || securityobjectuniqueid.isBlank()) {
				throw new Exception("ERROR_MISSING_SECURITYOBJECTUNIQUEID");
			}

			ObjectMapper mapper = new ObjectMapper();
			ObjectNode body = mapper.createObjectNode();
			body.put("@odata.id", "https://graph.microsoft.com/v1.0/directoryObjects/" + accountuniqueid);
			String jsonBody = body.toString();
			thislog.debug("createPermission()...[body]: " + maskJsonSecrets(jsonBody));

			MSGraphSession session = getOrCreateSession(coRequest);
			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_POST, "groups/" + securityobjectuniqueid + "/members/$ref");
			request.setBody(jsonBody);

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info("createPermission()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
			} else {
				String errorMessage = describeError(response);
				thislog.error("createPermission()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error("createPermission()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	/**
	 * Removes a user from a group (revokes a "permission") via
	 * {@code DELETE /groups/{groupid}/members/{userid}/$ref}.
	 * <p>
	 * PARAMETERS: accountuniqueid (REQUIRED, the user to remove), securityobjectuniqueid
	 * (REQUIRED, the group to remove it from).
	 */
	@Override
	public XML deletePermission(Logger thislog, ConnectorRequest coRequest, ConnectorResponse coResponse) throws Exception {
		thislog.info("deletePermission()...[running]");

		XML xmlResponse = new XML("response");

		try {
			String accountuniqueid = getIdentifier(coRequest, "accountuniqueid");
			if (accountuniqueid == null || accountuniqueid.isBlank()) {
				throw new Exception("ERROR_MISSING_ACCOUNTUNIQUEID");
			}

			String securityobjectuniqueid = getIdentifier(coRequest, "securityobjectuniqueid");
			if (securityobjectuniqueid == null || securityobjectuniqueid.isBlank()) {
				throw new Exception("ERROR_MISSING_SECURITYOBJECTUNIQUEID");
			}

			MSGraphSession session = getOrCreateSession(coRequest);
			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_DELETE,
					"groups/" + securityobjectuniqueid + "/members/" + accountuniqueid + "/$ref");

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info("deletePermission()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
			} else {
				String errorMessage = describeError(response);
				thislog.error("deletePermission()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error("deletePermission()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	/**
	 * Shared generic implementation for "modify/rename/move" operations: {@code PATCH} the given
	 * resource with all supplied pp_data_* fields except the id field itself.
	 */
	private XML patchResource(Logger thislog, ConnectorRequest coRequest, String methodName, String resourcePrefix, String idFieldName) throws Exception {
		thislog.info(methodName + "()...[running]");

		XML xmlResponse = new XML("response");

		try {
			String id = getIdentifier(coRequest, idFieldName);
			if (id == null || id.isBlank()) {
				throw new Exception("ERROR_MISSING_" + idFieldName.toUpperCase());
			}

			ObjectNode body = buildBodyFromParameters(coRequest, Set.of(idFieldName, "PASSWORD", "SYNCENABLED", "syncedaccount_policy", "syncedaccount_cloudattributes"));

			// A rename supplies the new name as a steering parameter: the account resource forwards
			// "accountname", the securityobject resource "securityobjectname". Anything already given as
			// a pp_data_ attribute stays untouched.
			if ("users".equals(resourcePrefix)) {
				String accountname = getConfig(coRequest, "accountname", null);
				if (accountname != null) {
					String domainname = getConfig(coRequest, "domainname", coRequest.getParameter("gp_param3"));
					setUnlessProvided(body, coRequest, "mailNickname", accountname);
					if (domainname != null && !domainname.isBlank())
						setUnlessProvided(body, coRequest, "userPrincipalName", accountname + "@" + domainname);
				}
			} else {
				setUnlessProvided(body, coRequest, "displayName", getConfig(coRequest, "securityobjectname", null));
			}

			MSGraphSession session = getOrCreateSession(coRequest);

			// Groups are never mastered on premise in a way Graph would let us patch here, so the check
			// only applies to users (rename and move end up in this method as well).
			if ("users".equals(resourcePrefix) && !applySyncedAccountPolicy(thislog, session, coRequest, methodName, id, body)) {
				xmlResponse.appendNode("success", "true");
				return xmlResponse;
			}

			String jsonBody = body.toString();
			thislog.debug(methodName + "()...[body]: " + maskJsonSecrets(jsonBody));

			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_PATCH, resourcePrefix + "/" + id);
			request.setBody(jsonBody);

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info(methodName + "()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
			} else {
				String errorMessage = describeError(response);
				thislog.error(methodName + "()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error(methodName + "()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	/**
	 * Shared generic implementation for "reconcile" operations: {@code GET} the given resource and
	 * return its current data.
	 */
	private XML reconcileResource(Logger thislog, ConnectorRequest coRequest, String methodName, String resourcePrefix, String idFieldName) throws Exception {
		thislog.info(methodName + "()...[running]");

		XML xmlResponse = new XML("response");

		try {
			// Same lookup as everywhere else: pp_config_ first, then pp_data_.
			String id = getIdentifier(coRequest, idFieldName);
			if (id == null || id.isBlank()) {
				throw new Exception("ERROR_MISSING_" + idFieldName.toUpperCase());
			}

			// Without $select Graph answers with its default property set - eleven fields for a user,
			// missing everything the collector additionally reads (accountEnabled, usageLocation,
			// onPremisesSyncEnabled and so on). A reconcile is meant to be compared against the
			// collected data, so it asks for the same fields.
			String attributes = getIdentifier(coRequest, "reconcileattributes");
			if (attributes == null || attributes.isBlank())
				attributes = "users".equals(resourcePrefix)
						? DEFAULT_RECONCILE_ATTRIBUTES_ACCOUNT
						: DEFAULT_RECONCILE_ATTRIBUTES_SECURITYOBJECT;

			MSGraphSession session = getOrCreateSession(coRequest);
			MSGraphRequest request = new MSGraphRequest(MSGraphRequest.type_GET, resourcePrefix + "/" + id);

			if (!attributes.isBlank()) {
				thislog.debug(methodName + "()...[attributes]: " + attributes);
				request.addParameter("$select", buildGraphSelect(attributes));
			}

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info(methodName + "()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
				xmlResponse.appendXML(response.getDataAsXML());
			} else {
				String errorMessage = describeError(response);
				thislog.error(methodName + "()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error(methodName + "()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

	/**
	 * Shared generic implementation for "custom command" operations: executes an arbitrary Graph API
	 * call as configured via parameters, rather than a fixed operation.
	 * <p>
	 * PARAMETERS: httpmethod (GET|POST|PATCH|DELETE, REQUIRED), resource (Graph resource
	 * path relative to the API root, REQUIRED), body (raw JSON body, optional, used for POST/PATCH)
	 * <p>
	 * The resource is written the way it reads in the Graph documentation, spaces and all - it is
	 * encoded here before the request is built. Unlike the collection parameters, where filter and
	 * select travel as separate values, everything is part of one string in a custom command, and a
	 * raw space would otherwise end in {@code URISyntaxException: Illegal character in query}.
	 * <p>
	 * EXAMPLE:
	 * <pre>
	 * httpmethod = GET
	 * resource   = servicePrincipals?$filter=appId eq '337294c8-...'&amp;$select=id,displayName
	 * </pre>
	 * Already encoded resources stay untouched, so {@code %20} written by hand keeps working.
	 */
	private XML executeCustomCommand(Logger thislog, ConnectorRequest coRequest, String methodName) throws Exception {
		thislog.info(methodName + "()...[running]");

		XML xmlResponse = new XML("response");

		try {
			String httpMethod = getConfig(coRequest, "httpmethod", null);
			if (httpMethod == null || httpMethod.isBlank()) {
				throw new Exception("ERROR_MISSING_HTTPMETHOD");
			}

			String resource = getConfig(coRequest, "resource", null);
			if (resource == null || resource.isBlank()) {
				throw new Exception("ERROR_MISSING_RESOURCE");
			}

			String rawBody = getConfig(coRequest, "body", null);

			String encodedresource = encodeGraphResource(resource);
			if (!encodedresource.equals(resource))
				thislog.debug(methodName + "()...[resource encoded]: " + encodedresource);

			MSGraphSession session = getOrCreateSession(coRequest);
			MSGraphRequest request = new MSGraphRequest(httpMethod.toUpperCase(), encodedresource);
			if (rawBody != null && !rawBody.isBlank()) {
				request.setBody(rawBody);
			}

			MSGraphResponse response = sendRequestWithRetry(session, request, coRequest, thislog);

			if (response != null && response.wasSuccess()) {
				thislog.info(methodName + "()...[SUCCESS]");
				xmlResponse.appendNode("success", "true");
				xmlResponse.appendXML(response.getDataAsXML());
			} else {
				String errorMessage = describeError(response);
				thislog.error(methodName + "()...[ERROR]: " + errorMessage);
				xmlResponse.appendNode("success", "false");
				xmlResponse.appendNode("error", errorMessage);
			}

		} catch (Exception e) {
			thislog.error(methodName + "()...[EXCEPTION]: " + e.getMessage(), e);
			xmlResponse.appendNode("success", "false");
			xmlResponse.appendNode("error", e.getMessage());
		}

		return xmlResponse;
	}

}
