package testing;

/**
 * Used for management of a running {@link Test}.
 *
 * @author Josef Litoš
 */
public interface Timer {

	/**
	 * This method is called every second of the {@link Test}, until the time runs out.
	 *
	 * @param secsLeft seconds left to the end of the test
	 * @return if the countdown should continue
	 */
	boolean doOnSec(int secsLeft);
}
