package avi.times;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Keith
 */
public class Main {

	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("Usage: AVITimes.pl {data-file} ...");
			return;
		}

		Main main = new Main();

		for (String dataFile : args) {
			try {
				main.readTimes(dataFile);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}

		main.updateFileTimes();
	}

	private final Map<String, Long> times;

	public Main() {
		super();
		this.times = new HashMap<>();
	}

	private void readTimes(String dataFile) throws IOException {
		BufferedReader data = new BufferedReader(new FileReader(dataFile));
		String timeRegex = "^\\s*(\\d+)\\s+(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)\\s*$";
		Pattern timePattern = Pattern.compile(timeRegex);

		try {
			String line;

			while ((line = data.readLine()) != null) {
				Matcher matcher = timePattern.matcher(line);

				if (!matcher.matches()) {
					continue;
				}

				String episode = matcher.group(1);
				int year = Integer.parseInt(matcher.group(2));
				int month = Integer.parseInt(matcher.group(3)) - 1;
				int date = Integer.parseInt(matcher.group(4));
				int hour = Integer.parseInt(matcher.group(5));
				int minute = Integer.parseInt(matcher.group(6));
				GregorianCalendar cal = new GregorianCalendar(year, month,
						date, hour, minute);

				times.put(episode, Long.valueOf(cal.getTimeInMillis()));
			}
		} finally {
			data.close();
		}
	}

	private void updateFileTimes() {
		File dir = new File(".");
		String nameRegex = "^(\\d+).*\\.(avi|mkv|mov|mp4|mpg)$";
		Pattern namePattern = Pattern.compile(nameRegex);

		for (File file : dir.listFiles()) {
			String name = file.getName();
			Matcher matcher = namePattern.matcher(name);

			if (!matcher.matches()) {
				continue;
			}

			Long time = times.get(matcher.group(1));

			if (time == null) {
				System.out.format("Warning: no time specified for %s\n", name);
				continue;
			}

			long timeInMillis = time.longValue();
			long modified = file.lastModified();

			if (modified == timeInMillis || file.setLastModified(timeInMillis))
				continue;

			System.out.format("Warning: failed to update time for %s\n", name);
		}
	}
}
