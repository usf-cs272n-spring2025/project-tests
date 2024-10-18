package edu.usfca.cs272.tests.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Assertions;

import edu.usfca.cs272.Driver;

/**
 * Utility methods used by other JUnit test classes.
 *
 * @author CS 272 Software Development (University of San Francisco)
 * @version Fall 2024
 */
public class ProjectBenchmarks extends ProjectTests {
	/** The system environment. */
	public static final Map<String, String> ENV = System.getenv();

	/** Indicates whether running on GitHub Actions. */
	public static final boolean GITHUB = Boolean.parseBoolean(ENV.get("CI"))
			&& Boolean.parseBoolean(ENV.get("GITHUB_ACTIONS"));

	/** Slower or poor speedup cutoff. */
	public static final double BAD_SPEEDUP = 0.9;

	/** Minimum speedup cutoff. */
	public static final double MIN_SPEEDUP = 1.1;

	/** Moderate speedup cutoff. */
	public static final double MOD_SPEEDUP = 1.5;

	/** Maximum speedup cutoff. */
	public static final double MAX_SPEEDUP = 2.0;

	/** The number of warmup runs when benchmarking. */
	public static final int WARMUP_ROUNDS = GITHUB ? 2 : 5;

	/** The number of rounds to use when benchmarking. */
	public static final int TIMED_ROUNDS = GITHUB ? 6 : 10;

	/** The default number of threads to use when benchmarking single versus multithreading. */
	public static final ProjectBenchmarks.Threads BENCH_MULTI = ProjectBenchmarks.Threads.THREE;

	/** The default number of threads to use when benchmarking workers. */
	public static final ProjectBenchmarks.Threads BENCH_WORKERS = ProjectBenchmarks.Threads.THREE;

	/** Format string used for debug output. */
	public static final String format = "%d workers has a %.2fx speedup (less than the %.1fx required) compared to %s.";

	/**
	 * Attempts to test that multiple threads are being used in this code.
	 *
	 * @param action the action to run
	 */
	public static void assertMultithreaded(Runnable action) {
		Assertions.assertTimeoutPreemptively(ProjectTests.LONG_TIMEOUT, () -> {
			Set<String> before = new HashSet<>(); // threads before
			Set<String> after = new HashSet<>();  // threads after

			CountDownLatch started = new CountDownLatch(1);  // driver started
			CountDownLatch finished = new CountDownLatch(1); // driver finished

			// setup thread to trigger latch when starts
			Thread driver = new Thread(() -> {
				// set before threads after this one has started
				before.addAll(activeThreads());

				// indicate the code should now continue
				started.countDown();
				action.run();
				finished.countDown();
			});

			driver.setPriority(Thread.MAX_PRIORITY);

			driver.start();
			started.await();

			// add current active threads
			after.addAll(activeThreads());

			// an intentional busy-wait loop (slow, but helps catch all possible worker threads)
			while (!finished.await(200, TimeUnit.MILLISECONDS)) {
				after.addAll(activeThreads());
			}

			// wait for driver to terminate (might already be at this state)
			driver.join();

			// try to figure out which ones are worker threads
			Set<String> workers = new HashSet<>(after);
			workers.removeAll(before); // remove threads running this code
			workers.removeIf(name -> name.startsWith("junit")); // remove junit timeout threads
			workers.removeIf(name -> name.startsWith("ForkJoinPool")); // remove other junit threads

			System.out.println("Workers: " + workers);

			String message = "Unable to detect any worker threads. Are you 100% positive threads are being created and used in your code? You can debug this by producing log output inside the run method of your thread objects. This is an imperfect test; if you are able to verify threads are being created and used, make a private post on the course forum. The instructor will look into the problem.";
			String format = "\nThreads Before: %s\nThreads After: %s\nWorker Threads: %s\n\n%s\n\n";
			Assertions.assertTrue(workers.size() > 0, ProjectTests.debug(format, before, after, workers, message));
		});
	}

	/**
	 * Returns the active thread names (approximate).
	 *
	 * @return the active thread names
	 */
	public static List<String> activeThreads() {
		int active = Thread.activeCount(); // only an estimate
		Thread[] threads = new Thread[active * 2]; // make sure large enough
		Thread.enumerate(threads); // fill in active threads

		return Arrays.stream(threads)
				.filter(t -> t != null)
				.map(Thread::getName)
				.toList();
	}

	/**
	 * Compares the runtime using two different sets of arguments. Outputs the
	 * runtimes to the console just in case there are any anomalies.
	 *
	 * @param file the file name to use to save output
	 * @param label1 the label of the first argument set
	 * @param args1 the first argument set
	 * @param label2 the label of the second argument set
	 * @param args2 the second argument set
	 * @return the runtime difference between the first and second set of arguments
	 * @throws IOException if an I/O error occurs
	 */
	public static double compare(String file, String label1, String[] args1, String label2, String[] args2)
			throws IOException {
		return compare(file, label1, args1, label2, args2, WARMUP_ROUNDS, TIMED_ROUNDS);
	}

	/**
	 * Compares the runtime using two different sets of arguments. Outputs the
	 * runtimes to the console just in case there are any anomalies.
	 *
	 * @param file the file name to use to save output
	 * @param label1 the label of the first argument set
	 * @param args1 the first argument set
	 * @param label2 the label of the second argument set
	 * @param args2 the second argument set
	 * @param warmRuns the number of warmup runs to use
	 * @param timeRuns the number of timed runs to use
	 * @return the runtime difference between the first and second set of arguments
	 * @throws IOException if an I/O error occurs
	 */
	public static double compare(String file, String label1, String[] args1, String label2, String[] args2, int warmRuns,
			int timeRuns) throws IOException {
		Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.OFF);

		// free up memory before benchmarking
		ProjectTests.freeMemory();

		// begin benchmarking
		long[] runs1 = benchmark(args1, warmRuns, timeRuns);
		long[] runs2 = benchmark(args2, warmRuns, timeRuns);

		long total1 = 0;
		long total2 = 0;

		long min1 = Long.MAX_VALUE;
		long min2 = Long.MAX_VALUE;

		StringWriter writer = new StringWriter();
		PrintWriter out = new PrintWriter(writer);

		out.printf("%n## Testing %s - %s versus %s%n", file, label1, label2);

		String labelFormat = "%-6s    %10s    %10s%n";
		String valueFormat = "%-6d    %10.6f    %10.6f%n";

		out.printf("%n```%n");
		out.printf(labelFormat, "Warmup", label1, label2);

		for (int i = 0; i < warmRuns; i++) {
			min1 = Math.min(min1, runs1[i]);
			min2 = Math.min(min2, runs2[i]);

			out.printf(valueFormat, i + 1, (double) runs1[i] / Duration.ofSeconds(1).toMillis(),
					(double) runs2[i] / Duration.ofSeconds(1).toMillis());
		}

		out.println();
		out.printf(labelFormat, "Timed", label1, label2);

		for (int i = warmRuns; i < warmRuns + timeRuns; i++) {
			total1 += runs1[i];
			total2 += runs2[i];

			min1 = Math.min(min1, runs1[i]);
			min2 = Math.min(min2, runs2[i]);

			out.printf(valueFormat, i + 1, (double) runs1[i] / Duration.ofSeconds(1).toMillis(),
					(double) runs2[i] / Duration.ofSeconds(1).toMillis());
		}

		double average1 = (double) total1 / timeRuns;
		double average2 = (double) total2 / timeRuns;

		out.println();
		out.printf("%10s:  %10.6f seconds average%n", label1, average1 / Duration.ofSeconds(1).toMillis());
		out.printf("%10s:  %10.6f seconds average%n%n", label2, average2 / Duration.ofSeconds(1).toMillis());
		out.printf("%10s:  %10.6f seconds minimum%n", label1, (double) min1 / Duration.ofSeconds(1).toMillis());
		out.printf("%10s:  %10.6f seconds minimum%n%n", label2, (double) min2 / Duration.ofSeconds(1).toMillis());

		double speedup = (double) min1 / min2;
		out.printf("%10s: x%10.6f %n", "Speedup", speedup);
		out.printf("```%n%n");
		out.flush();

		// output to console and to file
		String results = writer.toString();
		System.out.print(results);

		String test = label1.equals("Single") ? "single" : "multi";
		String format = "bench-%s-%s.txt";
		String filename = String.format(format, file.toLowerCase(), test);
		Files.writeString(ProjectPath.ACTUAL.resolve(filename), results);

		return speedup;
	}

	/**
	 * Benchmarks the {@link Driver#main(String[])} method with the provided
	 * arguments. Tracks the timing of every run to allow of visual inspection.
	 *
	 * @param args the arguments to run
	 * @param warmRuns the number of warmup runs to use
	 * @param timeRuns the number of timed runs to use
	 * @return an array of all the runtimes, including warmup runs and timed runs
	 */
	public static long[] benchmark(String[] args, int warmRuns, int timeRuns) {
		long[] runs = new long[warmRuns + timeRuns];

		Instant start;
		Duration elapsed;

		// suppress all console output for the warmup and timed runs
		PrintStream systemOut = System.out;
		PrintStream systemErr = System.err;

		PrintStream nullStream = new PrintStream(OutputStream.nullOutputStream());
		System.setOut(nullStream);
		System.setErr(nullStream);

		systemOut.print("Benchmarking");

		try {
			for (int i = 0; i < warmRuns; i++) {
				start = Instant.now();
				Driver.main(args);
				elapsed = Duration.between(start, Instant.now());
				runs[i] = elapsed.toMillis();
				systemOut.print(".");
			}

			for (int i = 0; i < timeRuns; i++) {
				start = Instant.now();
				Driver.main(args);
				elapsed = Duration.between(start, Instant.now());
				runs[i + warmRuns] = elapsed.toMillis();
				systemOut.print(".");
			}
		}
		catch (Exception e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));

			String format = "%nArguments:%n    [%s]%nException:%n    %s%n";
			String debug = String.format(format, String.join(" ", args), writer.toString());
			fail(debug);
		}
		finally {
			systemOut.println("done.");

			// restore console output
			System.setOut(systemOut);
			System.setErr(systemErr);
		}

		return runs;
	}

	/** The number of threads to use in testing. */
	public static enum Threads {
		/** One thread */
		ONE(1),

		/** Two threads */
		TWO(2),

		/** Three threads */
		THREE(3);

		/** The number of threads as an int. */
		public final int num;

		/** The number of threads as text. */
		public final String text;

		/**
		 * Initializes this thread count.
		 * @param num the number of threads
		 */
		private Threads(int num) {
			this.num = num;
			this.text = Integer.toString(num);
		}
	}

	/** Creates a new instance of this class. */
	public ProjectBenchmarks() {
	}
}
