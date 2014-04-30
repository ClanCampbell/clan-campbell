package quotes;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Date {

	private static final Pattern NumericPattern = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})");

	private static int value(int year, int month, int day) {
		if (!(0 <= year && year < 9999)) {
			throw new IllegalArgumentException("Bad year: " + year);
		}

		if (!(0 <= month && month < 12)) {
			throw new IllegalArgumentException("Bad month: " + month);
		}

		if (!(1 <= day && day <= 31)) {
			throw new IllegalArgumentException("Bad day: " + day);
		}

		return (((year << 4) + month) << 5) + day;
	}

	public static Date yesterday() {
		Calendar date = Calendar.getInstance();

		date.setTimeInMillis(date.getTimeInMillis() - (24 * 60 * 60 * 1000));

		return new Date(date);
	}

	private final int value;

	public Date(Calendar date) {
		this(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
	}

	public Date(int year, int month, int day) {
		super();
		this.value = value(year, month, day);
	}

	public Date(String yyyymmdd) {
		super();

		Matcher matcher = NumericPattern.matcher(yyyymmdd);

		if (!matcher.matches()) {
			throw new IllegalArgumentException("Bad date: " + yyyymmdd);
		}

		this.value = value( // <br/>
				Integer.parseInt(matcher.group(1)), // <br/>
				Integer.parseInt(matcher.group(2)) - 1, // <br/>
				Integer.parseInt(matcher.group(3)));
	}

	public int day() {
		return value & 31;
	}

	public int month() {
		return (value >> 5) & 15;
	}

	@Override
	public String toString() {
		return String.format("%04d/%02d/%02d", // <br/>
				Integer.valueOf(year()), Integer.valueOf(month() + 1), Integer.valueOf(day()));
	}

	public int year() {
		return value >>> 9;
	}
}
