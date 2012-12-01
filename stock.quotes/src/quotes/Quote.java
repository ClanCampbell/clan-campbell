package quotes;

public final class Quote {

	private final double price;

	private final long volume;

	public Quote(double price, long volume) {
		super();
		this.price = price;
		this.volume = volume;
	}

	public double getPrice() {
		return price;
	}

	public long getVolume() {
		return volume;
	}
}
