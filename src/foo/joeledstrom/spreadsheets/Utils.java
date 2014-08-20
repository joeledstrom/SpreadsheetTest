package foo.joeledstrom.spreadsheets;

public class Utils {
	
	private static String[] unencoded = {"&", "\"", "<", ">", "'"};
	private static String[] encoded = {"&amp;", "&quot;", "&lt;", "&gt;", "&apos;"};
			
	
	public static String encodeXML(String result) {
		
		for (int i = 0; i < unencoded.length; i++) {
			result = result.replace(unencoded[i], encoded[i]);
		}
		
		return result;
	}
	
	public static String decodeXML(String result) {
		for (int i = 0; i < unencoded.length; i++) {
			result = result.replace(encoded[i], unencoded[i]);
		}
		
		return result;
	}
}
