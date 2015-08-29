package quotes;

import quotes.yahoo.HistoryService;

public final class Main {

	private static final java.lang.String[] symbols = { /* */
			//
			"EGHT", // <br/>
			"AMD", // <br/>
			"ALU", // <br/>
			"AAPL", // <br/>
			"APM.V", // <br/>
			"RPT.V", // <br/>
			"BNS.TO", // <br/>
			"BCE.TO", // <br/>
			"BRK-B", // <br/>
			"BRCM", // <br/>
			"CWL.TO", // <br/>
			"CM.TO", // <br/>
			"COS.TO", // <br/>
			"DF.TO", // <br/>
			"FTS.TO", // <br/>
			"HSE.TO", // <br/>
			"IDG.TO", // <br/>
			"IBM", // <br/>
			"XIC.TO", // <br/>
			"JML.V", // <br/>
			"LITE", // <br/>
			"MAS.V", // <br/>
			"MSFT", // <br/>
			"NA.TO", // <br/>
			"NBD.TO", // <br/>
			"NRTLQ", // <br/>
			"ORCL", // <br/>
			"PPL.TO", // <br/>
			"PWT.TO", // <br/>
			"POW.TO", // <br/>
			"PG.TO", // <br/>
			"RMK.TO", // <br/>
			"SXG.V", // <br/>
			"SGF.TO", // <br/>
			"SGL.TO", // <br/>
			"SLF.TO", // <br/>
			"SU.TO", // <br/>
			"SNCR", // <br/>
			"TTWO", // <br/>
			"T.TO", // <br/>
			"TA.TO", // <br/>
			"TRP.TO", // <br/>
			"VIAV", // <br/>
			"WPK.TO", // <br/>
			"WVNTF", // <br/>
			"WSP.TO", // <br/>
			"^DJI", // <br/>
			"^IXIC", // <br/>
			"^GSPC", // <br/>
			"^GSPTSE", // <br/>
			"XIU.TO" // <br/>
	};

	public static void main(String[] args) {
		Date date;

		if (args.length > 0) {
			date = new Date(args[0]);
		} else {
			date = Date.yesterday();
		}

		System.out.format("Quotes for %s\n", date);
		System.out.println();
		System.out.println("Symbol        Price         Volume");
		System.out.println("------        -----         ------");

		for (String symbol : symbols) {
			printQuote(symbol, date);
		}
	}

	private static void printQuote(String symbol, Date date) {
		Quote quote = HistoryService.getQuote(symbol, date);

		if (quote == null) {
			System.out.format("%-8s %10s\n", // <br/>
					symbol, "-.--");
		} else {
			System.out.format(
					"%-8s %,10.2f %,14d\n", // <br/>
					symbol, Double.valueOf(quote.getPrice()),
					Long.valueOf(quote.getVolume()));

		}
	}

	private Main() {
		super();
	}
}
