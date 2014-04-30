package avi.copy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.NumberFormat;

public final class Util {

	public static String format(long amount) {
		NumberFormat format = NumberFormat.getInstance();

		if (0 <= amount && amount < 1024) {
			return format.format(amount);
		}

		double value;

		if (amount < 0) {
			value = (amount >>> 1) * 2.0 + (amount & 1);
		} else {
			value = amount;
		}

		for (int index = 0;; ++index) {
			if ((value /= 1024) < 1024) {
				format.setMaximumFractionDigits(2);

				return format.format(value) + "kMGTPE".charAt(index); //$NON-NLS-1$
			}
		}
	}

	private static String format2(long amount) {
		BigDecimal v = new BigDecimal(amount >>> 1).multiply(new BigDecimal(2));

		if ((amount & 1) != 0) {
			v = v.add(BigDecimal.ONE);
		}

		final BigDecimal k = new BigDecimal(1024);
		BigDecimal s = BigDecimal.ONE;

		for (int index = 0;; ++index) {
			s = s.multiply(k);

			if (v.compareTo(s) < 0) {
				if (index == 0) {
					NumberFormat format = NumberFormat.getInstance();

					return format.format(v);
				}

				v = v.divide(s.divide(k), new MathContext(5));

				return v.toPlainString() + "kMGTPE".charAt(index - 1); //$NON-NLS-1$
			}
		}
	}

	public static void main(String[] args) {
		long value = 1;

		do {
			show(value);
		} while ((value *= 3) > 0);

		show(Long.MAX_VALUE);
		show(-1);
	}

	private static void show(long value) {
		System.out.format("%,d -> %s / %s\n", //$NON-NLS-1$
				Long.valueOf(value), format(value), format2(value));
	}
}
