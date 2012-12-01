package quotes.yahoo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quotes.Date;
import quotes.Quote;

public final class HistoryService {

	private static final String Hostname = "ichart.finance.yahoo.com";

	private static final String UTF_8 = "UTF-8";

	public static quotes.Quote getQuote(String symbol, Date date) {
		try {
			return getQuote0(symbol, date);
		} catch (IOException e) {
			return null;
		}
	}

	private static quotes.Quote getQuote0(String symbol, Date date) throws IOException {
		URL url = makeQuoteURL(symbol, date);
		InputStream in = url.openStream();
		InputStreamReader rd = new InputStreamReader(in, UTF_8);
		BufferedReader br = new BufferedReader(rd);
		Pattern pattern = quotePattern(date);

		try {
			for (;;) {
				String line = br.readLine();

				if (line == null) {
					break;
				}

				Matcher matcher = pattern.matcher(line);

				if (matcher.matches()) {
					double close = Double.parseDouble(matcher.group(1));
					long volume = Long.parseLong(matcher.group(2));

					return new Quote(close, volume);
				}
			}
		} finally {
			br.close();
		}

		return null;
	}

	private static URL makeQuoteURL(String symbol, Date date) throws IOException {
		StringBuilder file = new StringBuilder();

		file.append("/table.csv");
		file.append("?s=").append(URLEncoder.encode(symbol, UTF_8));

		file.append("&a=").append(date.month());
		file.append("&b=").append(date.day());
		file.append("&c=").append(date.year());

		file.append("&d=").append(date.month());
		file.append("&e=").append(date.day());
		file.append("&f=").append(date.year());

		file.append("&g=d");
		file.append("&ignore=.csv");

		return new URL("http", Hostname, file.toString());
	}

	private static Pattern quotePattern(Date date) {
		String pattern = "^%04d.%02d.%02d,[^,]+,[^,]+,[^,]+,(\\d+\\.\\d+),(\\d+),[^,]+$";
		String regex = String.format(pattern, // <br/>
				Integer.valueOf(date.year()), // <br/>
				Integer.valueOf(date.month() + 1), // <br/>
				Integer.valueOf(date.day()));

		return Pattern.compile(regex);
	}

	private HistoryService() {
		super();
	}
}
