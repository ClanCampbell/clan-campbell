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

	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("Usage: AVITimes.pl [-dateFirst] {data-file} ...");
			return;
		}

		new Main().run(args);
	}

	private boolean dateFirst;

	private final SortedMap<String, Long> times;

	private Main() {
		super();
		this.dateFirst = false;
		this.times = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	}

	private Long getTime(String title) {
		Long time = null;

		if (title != null && (time = times.get(title)) == null) {
			// no exact match, check if a prefix of the title is in the map
			SortedMap<String, Long> headMap = times.headMap(title);
			String head;

			if (!headMap.isEmpty() && title.startsWith((head = headMap.lastKey()))) {
				time = times.get(head);
			}
		}

		return time;
	}

	private void readTimes(String dataFile) throws IOException {
		String lineRegex;
		int dateGroup;
		int titleGroup;

		if (dateFirst) {
			lineRegex = "^\\s*(\\d{12})\\s+(\\S.*)$";
			dateGroup = 1;
			titleGroup = 2;
		} else {
			lineRegex = "^\\s*(\\d+)\\s+(\\d{12})\\s*$";
			dateGroup = 2;
			titleGroup = 1;
		}

		Pattern linePattern = Pattern.compile(lineRegex);
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmm");

		try (BufferedReader data = new BufferedReader(new FileReader(dataFile))) {
			String line;

			while ((line = data.readLine()) != null) {
				Matcher matcher = linePattern.matcher(line);

				if (!matcher.matches()) {
					continue;
				}

				try {
					String title = matcher.group(titleGroup);
					Date date = dateFormat.parse(matcher.group(dateGroup));
					Calendar time = new Calendar.Builder().setInstant(date).build();

					if (time.get(Calendar.YEAR) < 1970) {
						System.out.println("Clamping timestamp to 1970 for " + title);
						time.set(Calendar.YEAR, 1970);
					}

					times.put(title, Long.valueOf(time.getTimeInMillis()));
				} catch (ParseException e) {
					// cannot happen because it matches the date group of linePattern
				}
			}
		}
	}

	private void run(String[] args) {
		for (String arg : args) {
			if ("-dateFirst".equals(arg)) {
				dateFirst = true;
				continue;
			}

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
