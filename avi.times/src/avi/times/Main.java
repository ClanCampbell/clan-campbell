package avi.times;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Keith
 */
public final class Main {

	private static final Pattern DateAndTitle = Pattern.compile("^\\s*(\\d{12})\\s+(\\S.*)$");

	private static final Pattern EpisodeAndDate = Pattern.compile("^\\s*(\\d+)\\s+(\\d{12})\\s*$");

	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("Usage: AVITimes.pl [-dateFirst] {data-file} ...");
			return;
		}

		new Main().run(args);
	}

	private static boolean startsWithIgnoreCase(String string, String prefix) {
		return string.regionMatches(true, 0, prefix, 0, prefix.length());
	}

	private final SortedMap<String, Long> times;

	private Main() {
		super();
		this.times = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	}

	private Long getTime(String title) {
		Long time = null;

		if (title != null && (time = times.get(title)) == null) {
			// no exact match, check if a prefix of the title is in the map
			SortedMap<String, Long> headMap = times.headMap(title);
			String head;

			if (!headMap.isEmpty() && startsWithIgnoreCase(title, head = headMap.lastKey())) {
				time = times.get(head);
			}
		}

		return time;
	}

	private void readTimes(String dataFile) throws IOException {
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmm");

		try (BufferedReader data = new BufferedReader(new FileReader(dataFile))) {
			String line;

			while ((line = data.readLine()) != null) {
				Matcher matcher;
				String dateText;
				String title;

				if ((matcher = DateAndTitle.matcher(line)).matches()) {
					dateText = matcher.group(1);
					title = matcher.group(2);
				} else if ((matcher = EpisodeAndDate.matcher(line)).matches()) {
					dateText = matcher.group(2);
					title = matcher.group(1);
				} else {
					continue;
				}

				try {
					Date date = dateFormat.parse(dateText);
					Calendar time = new Calendar.Builder().setInstant(date).build();

					if (time.get(Calendar.YEAR) < 1970) {
						System.out.println("Clamping timestamp to 1970 for " + title);
						time.set(Calendar.YEAR, 1970);
					}

					times.put(title, Long.valueOf(time.getTimeInMillis()));
				} catch (ParseException e) {
					// cannot happen because the pattern matched
				}
			}
		}
	}

	private void run(String[] args) {
		for (String arg : args) {
			try {
				readTimes(arg);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}

		updateFileTimes();
	}

	private void updateFileTimes() {
		String nameRegex = "^.+\\.(avi|mkv|mov|mp4|mpg)$";
		Pattern namePattern = Pattern.compile(nameRegex);

		for (File file : new File(".").listFiles()) {
			String name = file.getName();
			Matcher matcher = namePattern.matcher(name);

			if (!matcher.matches()) {
				continue;
			}

			Long time = getTime(name);

			if (time == null) {
				System.out.format("Warning: no time specified for '%s'\n", name);
				continue;
			}

			long timeInMillis = time.longValue();
			long modified = file.lastModified();

			if (modified == timeInMillis || file.setLastModified(timeInMillis)) {
				continue;
			}

			System.out.format("Warning: failed to update time for '%s'\n", name);
		}
	}
}
