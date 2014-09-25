import java.io.IOException;
import java.util.Date;
import java.util.TimeZone;

import com.google.api.client.util.DateTime;

/**
 * This class is used to test the connector to Google Task API.
 * 
 * @author Michelle Tan
 */
public class GCalAPITest {
	public static void main(String[] args) {
		GoogleCalConnector gcc = new GoogleCalConnector();
		
		Date startDate = new Date();
		Date endDate = new Date(startDate.getTime() + 360000);
		DateTime start = new DateTime(startDate, TimeZone.getTimeZone("UTC"));
		DateTime end = new DateTime(endDate, TimeZone.getTimeZone("UTC"));
		System.out.println(gcc.addEvent("Event 1", start, end));
		System.out.println(gcc.getAllEvents());

	}
}
