package com.schandler.peskyspinachservlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.logging.Logger;

import javax.servlet.http.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@SuppressWarnings("serial")
public class PeskySpinachServletServlet extends HttpServlet {
	public static String TWILIO_ACCOUNT_SID;
	public static String TWILIO_AUTH_TOKEN;
	public static String TWILIO_NUMBER;
	public static String TWILIO_APP_SID;
	public static String TWILIO_API_URL;
	private static final Logger log = Logger.getLogger(PeskySpinachServletServlet.class.getName());

	public void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {

		TWILIO_API_URL = "https://api.twilio.com";
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

		// Handle requests to make call
		if (req.getParameter("CallSid") == null && req.getParameter("Body") != null) {
			String message = req.getParameter("Body");
			String toNumber = req.getParameter("To");
			TWILIO_ACCOUNT_SID = req.getParameter("TWILIO_ACCOUNT_SID");
			TWILIO_AUTH_TOKEN = req.getParameter("TWILIO_AUTH_TOKEN");
			TWILIO_NUMBER = req.getParameter("TWILIO_NUMBER");
			URL url = new URL(TWILIO_API_URL + "/2010-04-01/Accounts/" + TWILIO_ACCOUNT_SID + "/Calls");
			
			String authString = TWILIO_ACCOUNT_SID + ":" + TWILIO_AUTH_TOKEN;
			System.out.println("auth string: " + authString);
			byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
			String authStringEnc = new String(authEncBytes);

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
			connection.setDoOutput(true);
			connection.setRequestMethod("POST");

			String callData = URLEncoder.encode("To","UTF-8") + "=" + URLEncoder.encode(toNumber, "UTF-8");
			callData += "&" + URLEncoder.encode("From", "UTF-8") + "=" + URLEncoder.encode(TWILIO_NUMBER, "UTF-8");
			callData += "&" + URLEncoder.encode("Url", "UTF-8") + "=" + URLEncoder.encode("http://peskyspinach.appspot.com/peskyspinachservlet", "UTF-8");

			OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());

			resp.getWriter().println(callData);
			writer.write(callData);
			writer.close();

			InputStream responseStream = connection.getInputStream();
			DocumentBuilder builder;
			Document xmlResponse;
			String callSid = "";
			try {
				builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
				xmlResponse = builder.parse(responseStream);
				NodeList sidNodes = xmlResponse.getElementsByTagName("Sid");
				Node sid = sidNodes.item(0);
				callSid = sid.getFirstChild().getNodeValue();
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			Entity callEntity = new Entity(KeyFactory.createKey("Call", "fredtest"));
			callEntity.setProperty("message", message);
			datastore.put(callEntity);
			connection.disconnect();
			resp.getWriter().println("Request sent to Twilio");
		}
		
		// Handle requests from Twilio
		else {
			String callSid = req.getParameter("CallSid");
			log.info("CallSid: " + req.getParameter("CallSid"));
			String message = "default message";
			try {
				Key key = KeyFactory.createKey("Call", "fredtest");
				log.info("Key: " + KeyFactory.keyToString(key));
				Entity callEntity = datastore.get(key);
				message = (String) callEntity.getProperty("message");
			} catch (EntityNotFoundException e) {
				e.printStackTrace();
			}
			resp.getWriter().println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
			resp.getWriter().println("<Response>");
			resp.getWriter().println("<Say>" + message + "</Say>");
			resp.getWriter().println("</Response>");
		}
	}
}
