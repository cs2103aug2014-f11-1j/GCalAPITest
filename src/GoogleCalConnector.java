import com.google.api.client.auth.oauth2.DataStoreCredentialRefreshListener;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Lists;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.FileDataStoreFactory;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import java.io.BufferedReader;
import java.io.File;
import java.util.Arrays;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * This class is used to connect to Google Calendar API.
 * 
 * To use this class, the user has to provide the
 * details of their Google account and sign in.
 * This class can create, read, update or delete tasks
 * for the given Google account.
 * 
 * @author Sean Saito
 */

public class GoogleCalConnector {
	
	private static final String CLIENT_ID = "1009064713944-qqeb136ojidkjv4usaog806gcafu5dmn.apps.googleusercontent.com";
	private static final String CLIENT_SECRET = "9ILpkbnlGwVMQiqh10za3exf";
	private static final String APPLICATION_NAME = "Task Commander";
	
	private static final String DATA_STORE_DIR = "credentials";
	private static final String DATA_STORE_NAME = "credentialDataStore";
	private static final String MESSAGE_EXCEPTION_IO = "Unable to read the data retrieved.";
	private static final String MESSAGE_ARGUMENTS_NULL = "Null arguments given.";

	// Option to request access type for application. Can be "online" or "offline".
	private static final String FLOW_ACCESS_TYPE = "offline";
	// Option to request approval prompt type for application. Can be "force" or "auto".
	private static final String FLOW_APPROVAL_PROMPT = "auto";

	private static final String USERNAME = "User";

	//Global instances
	private static Calendar client;
	static final java.util.List<Calendar> addedCalendarsUsingBatch = Lists.newArrayList();
	
	private GoogleAuthorizationCodeFlow flow;
	private final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";
	private FileDataStoreFactory dataStoreFactory;
	private HttpTransport httpTransport;
	private JsonFactory jsonFactory;
	private DataStore<StoredCredential> dataStore;
	
	/**
	 * Returns a GoogleTaskConnector after trying to 
	 * connect to Google.
	 * 
	 */
	public GoogleCalConnector() {
		httpTransport = new NetHttpTransport();
		jsonFactory = new JacksonFactory();

		try {
			File dataStoreFile = new File(DATA_STORE_DIR);
			dataStoreFactory = new FileDataStoreFactory(dataStoreFile);
			dataStore = dataStoreFactory.getDataStore(DATA_STORE_NAME);
		} catch (IOException e) {
			System.out.println(MESSAGE_EXCEPTION_IO);
		}
		setUp();
		getData();
	}
	
	/**
	 * Connects to Google and initialises Tasks service.
	 * Requests can be sent once this method is successfully
	 * executed.
	 */
	public void setUp(){
		GoogleCredential credential = getCredential();
		client = new Calendar.Builder(httpTransport, jsonFactory, credential)
		.setApplicationName(APPLICATION_NAME).build();
	}

	/**
	 * Gets a GoogleCredential for use in Google API requests,
	 * either from storage or by sending a request to Google.
	 * @return           Credential
	 */
	private GoogleCredential getCredential() {
		GoogleCredential credential = new GoogleCredential.Builder()
		.setJsonFactory(jsonFactory)
		.setTransport(httpTransport)
		.setClientSecrets(CLIENT_ID, CLIENT_SECRET)
		.addRefreshListener(new DataStoreCredentialRefreshListener(USERNAME, dataStore))
		.build();
		
		try {
			if(dataStore.containsKey(USERNAME)){
			    StoredCredential storedCredential = dataStore.get(USERNAME);
			    credential.setAccessToken(storedCredential.getAccessToken());
			    credential.setRefreshToken(storedCredential.getRefreshToken());
			}else{
			    credential.setFromTokenResponse(requestAuthorisation());
			}
			saveCredential(credential);
		} catch (IOException e) {
			System.out.println(MESSAGE_EXCEPTION_IO);
		}
		return credential;
	}
	
	/**
	 * Saves given credential in the datastore.
	 */
	public void saveCredential(GoogleCredential credential){
		StoredCredential storedCredential = new StoredCredential();
		storedCredential.setAccessToken(credential.getAccessToken());
		storedCredential.setRefreshToken(credential.getRefreshToken());
		try {
			dataStore.set(USERNAME, storedCredential);
		} catch (IOException e) {
			System.out.println(MESSAGE_EXCEPTION_IO);
		}
	}
	
	/**
	 * Returns a token response after requesting user
	 * login and authorisation.
	 * 
	 * Makes an authorisation request to Google and prints
	 * out a URL. The user has to enter the given URL into 
	 * a browser and login to Google, then paste the returned
	 * authorisation code into command line. 
	 */
	private GoogleTokenResponse requestAuthorisation() {
		try {
			flow = buildAuthorisationCodeFlow(httpTransport, jsonFactory, dataStoreFactory);
		} catch (IOException e) {
			System.out.println(MESSAGE_EXCEPTION_IO);
		}
		
		askUserForAuthorisationCode(flow);
		String code = getUserInput();

		return getTokenResponse(flow, code);
	}
	
	/**
	 * Sends a token request to get a GoogleTokenResponse.
	 * If an IOException occurs, returns null.
	 * 
	 * @param flow
	 * @param code
	 * @return      Token response
	 */
	private GoogleTokenResponse getTokenResponse(GoogleAuthorizationCodeFlow flow, String code) {
		try {
			GoogleTokenResponse response = flow.newTokenRequest(code)
					.setRedirectUri(REDIRECT_URI).execute();
			return response;
		} catch (IOException e) {
			System.out.println(MESSAGE_EXCEPTION_IO);
		}
		return null;
	}

	/**
	 * Creates the authorisation code flow needed for the authorisation URL.
	 * 
	 * @param httpTransport
	 * @param jsonFactory
	 * @param fdsf           FileDataStoreFactory
	 * @return               GoogleAuthorizationCodeFlow object
	 * @throws IOException
	 */
	private GoogleAuthorizationCodeFlow buildAuthorisationCodeFlow(
			HttpTransport httpTransport, 
			JsonFactory jsonFactory,
			FileDataStoreFactory fdsf) throws IOException {
		return new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, jsonFactory, CLIENT_ID, CLIENT_SECRET, Arrays.asList(CalendarScopes.CALENDAR))
		.setAccessType(FLOW_ACCESS_TYPE)
		.setApprovalPrompt(FLOW_APPROVAL_PROMPT)
		.setDataStoreFactory(fdsf).build();
	}

	/**
	 * Creates the authorization URL, asks the user to open the URL and sign in, then type in the
	 * Authorization code from Google.
	 * @param flow
	 */
	private void askUserForAuthorisationCode(GoogleAuthorizationCodeFlow flow) {
		String url = flow.newAuthorizationUrl().setRedirectUri(REDIRECT_URI).build();
		System.out.println("Please open the following URL in your browser then type the authorization code:");
		System.out.println("  " + url);
	}

	/**
	 * Reads user input and returns it. 
	 * @return      String of user input.
	 */
	private String getUserInput() {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String input = "";
		try {
			input = br.readLine();
			br.close();
		} catch (IOException e) {
			System.out.println(MESSAGE_EXCEPTION_IO);
		}
		return input;
	}
	
	
	//Get some data
	private void getData(){
		GoogleCredential credential = getCredential();
		Calendar.Builder serviceBuilder = new Calendar.Builder(httpTransport, jsonFactory, credential);
		serviceBuilder.setApplicationName(APPLICATION_NAME);
		Calendar calendar = serviceBuilder.build();
		
		try {
			Calendar.CalendarList.List listRequest = calendar.calendarList().list();
			CalendarList feed = listRequest.execute();
			for(CalendarListEntry entry:feed.getItems()){
	            System.out.println("ID: " + entry.getId());
	            System.out.println("Summary: " + entry.getSummary());
	        }
		} catch (IOException e) {
			System.out.print("Could not get data!\n");
			e.printStackTrace();
		}
	}
		
}