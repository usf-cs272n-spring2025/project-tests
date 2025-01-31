package edu.usfca.cs272.tests.utils;

import static edu.usfca.cs272.tests.utils.ProjectPath.ACTUAL;
import static edu.usfca.cs272.tests.utils.ProjectPath.EXPECTED;
import static edu.usfca.cs272.tests.utils.ProjectPath.TEXT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.MultipleFailuresError;

import edu.usfca.cs272.Driver;

/**
 * Utility methods used by other JUnit test classes.
 *
 * @author CS 272 Software Development (University of San Francisco)
 * @version Spring 2025
 */
public class ProjectTests {
	/** Amount of time to wait for long-running tests to finish. */
	public static final Duration LONG_TIMEOUT = Duration.ofMinutes(5);

	/** Amount of time to wait for short-running tests to finish. */
	public static final Duration SHORT_TIMEOUT = Duration.ofSeconds(30);

	/** Stores failures from uncaught exceptions. */
	public static final List<Executable> UNCAUGHT = Collections.synchronizedList(new ArrayList<>());

	/**
	 * Generates header for debugging output when an error occurs.
	 *
	 * @param args the arguments
	 * @param message the error message
	 * @return the error header
	 */
	public static StringBuilder errorHeader(String[] args, String message) {
		StringBuilder header = new StringBuilder("\n");

		header.append("Error Message:\n");
		header.append(message);
		header.append("\n\n");

		// add arguments
		header.append("Arguments (");
		header.append(args.length);
		header.append("):\n");
		header.append(args.length > 0 ? String.join(" ", args) : "(none)");
		header.append("\n\n");

		// add working directory information
		header.append("Working Directory:\n");
		header.append(Path.of(".").toAbsolutePath().normalize().getFileName());
		header.append("\n\n");

		return header;
	}

	/**
	 * Only adds the most relevant parts of the stack trace to the debug output.
	 *
	 * @param debug the debug output
	 * @param thrown the thrown error
	 */
	public static void addFilteredTrace(StringBuilder debug, Throwable thrown) {
		// get full stack trace
		StackTraceElement[] trace = thrown.getStackTrace();

		// filter to only include relevant source code
		List<StackTraceElement> filtered = Arrays.stream(trace)
				.filter(line -> line.getClassName().startsWith("edu.usfca.cs272"))
				.filter(line -> !line.getClassName().startsWith("edu.usfca.cs272.tests"))
				.toList();

		// make sure at least one line is displayed
		if (filtered.isEmpty() && trace.length > 0) {
			filtered = List.of(trace[0]);
		}

		// add exception name and message
		debug.append(thrown.toString());
		debug.append("\n");

		// add each filtered line to output
		for (StackTraceElement element : filtered) {
			debug.append("\tat ").append(element.toString()).append("\n");
		}
	}

	/**
	 * Checks whether {@link Driver} generates the expected output without any
	 * exceptions. Will print the stack trace if an exception occurs. Designed to be
	 * used within an unit test. If the test was successful, deletes the actual
	 * file. Otherwise, keeps the file for debugging purposes.
	 *
	 * @param args arguments to pass to {@link Driver}
	 * @param actual path to actual output
	 * @param expected path to expected output
	 */
	public static void checkOutput(String[] args, Path actual, Path expected) {
		checkOutput(args, Map.of(actual, expected));
	}

	/**
	 * Checks whether {@link Driver} generates the expected output without any
	 * exceptions. Will print the stack trace if an exception occurs. Designed to be
	 * used within an unit test. If the test was successful, deletes the actual
	 * files. Otherwise, keeps the files for debugging purposes.
	 *
	 * @param args arguments to pass to {@link Driver}
	 * @param files map of actual to expected files to test
	 */
	public static void checkOutput(String[] args, Map<Path, Path> files) {
		try {
			ArrayList<Executable> tests = new ArrayList<>();

			for (var entry : files.entrySet()) {
				Path actual = entry.getKey();
				Path expected = entry.getValue();

				// Remove old actual files (if exists), setup directories if needed
				Files.deleteIfExists(actual);
				Files.createDirectories(actual.toAbsolutePath().getParent());

				// Generate (but do not run) the output test
				Executable test = () -> {
					// Double-check we can read the expected output file
					if (!Files.isReadable(expected)) {
						String message = """
								\tUnable to read expected output file
								\t\tat %s""";

						Assertions.fail(() -> message.formatted(expected));
					}

					// Double-check we can read the actual output file
					if (!Files.isReadable(actual)) {
						String message = """
								\tUnable to read actual output file
								\t\tat %s""";

						Assertions.fail(() -> message.formatted(actual));
					}

					// Compare the two files
					int count = compareFiles(actual, expected);

					if (count <= 0) {
						String message = """
								\tUnexpected output on line %d
								\t\tat %s and
								\t\tat %s""";
						Assertions.fail(() -> message.formatted(-count, actual, expected));
					}

					// Clean up file if get this far
					Files.delete(actual);
				};

				// Add to the output tests
				tests.add(test);
			}

			// Generate actual output files
			assertNoExceptions(args, LONG_TIMEOUT);

			// Compare all output files
			assertMultiple(tests, args, "Found error(s) while comparing file output.");
		}
		catch (Exception e) {
			StringBuilder debug = errorHeader(args, "Unexpected exception while comparing files.");
			Assertions.fail(debug.toString(), e);
		}
	}

	/**
	 * Converts a map of flags and values to an array of command-line arguments.
	 *
	 * @param config the flag and value pairs
	 * @return command-line arguments
	 */
	public static String[] args(Map<ProjectFlag, String> config) {
		return config.entrySet()
				.stream()
				.flatMap(entry -> Stream.of(entry.getKey().flag, entry.getValue()))
				.filter(Objects::nonNull)
				.filter(Predicate.not(String::isBlank))
				.toArray(String[]::new);
	}

	/**
	 * Checks whether {@link Driver} will run without generating any exceptions.
	 * Will print the stack trace if an exception occurs. Designed to be used within
	 * an unit test.
	 *
	 * @param args arguments to pass to {@link Driver}
	 * @param timeout the duration to run before timing out
	 */
	public static void assertNoExceptions(String[] args, Duration timeout) {
		Assertions.assertTimeoutPreemptively(timeout, () -> {
			try {
				System.out.printf("%nRunning Driver %s...%n", String.join(" ", args));
				Driver.main(args);
			}
			catch (Exception e) {
				String summary = "Unexpected exception while running Driver.";
				StringBuilder debug = errorHeader(args, summary);

				debug.append("Error Details:\n");
				addFilteredTrace(debug, e);

				debug.append("\nFull Failure Trace:");
				Assertions.fail(debug.toString());
			}
		});
	}

	/**
	 * Makes multiple assertions with customized error output if there are any
	 * failures.
	 *
	 * @param tests the assertions
	 * @param args the original args
	 * @param header the header
	 */
	public static void assertMultiple(List<Executable> tests, String[] args, String header) {
		// Test the output of all files
		try {
			Assertions.assertAll(tests);
		}
		catch (MultipleFailuresError e) {
			// Customize multiple failure output
			int errors = e.getFailures().size();
			StringBuilder debug = errorHeader(args, header);

			debug.append("Error Details (" + errors + "):");

			for (Throwable t : e.getFailures()) {
				debug.append("\n");
				if (t instanceof AssertionError) {
					debug.append(t.getMessage());
				}
				else {
					addFilteredTrace(debug, t);
				}
				debug.append("\n");
			}

			debug.append("\nFull Failure Trace:");
			Assertions.fail(debug.toString());
		}
	}

	/**
	 * Crafts an executable that tests whether a file exists.
	 *
	 * @param path the output file to test if exists
	 * @param flag the related flag that should trigger the file output
	 * @param exists whether to test if the file does or does not exist
	 * @return an executable
	 */
	public static Executable assertFileExists(Path path, String flag, boolean exists) {
		String format = exists ?
				"Always create %s if the %s flag is present.\n" :
				"Never create %s if the %s flag is missing.\n";

		BiConsumer<Boolean, Supplier<String>> assertion = exists ?
				Assertions::assertTrue : Assertions::assertFalse;

		return () -> {
			assertion.accept(Files.exists(path), debug(format, path, flag));
		};
	}

	/**
	 * Returns a supplier that will return a formatted error message. Used within
	 * assertions.
	 *
	 * @param message the format message to output
	 * @param values the values to substitute into the message
	 * @return the formatted message
	 *
	 * @see String#formatted(Object...)
	 * @see System#err
	 * @see Supplier
	 */
	public static Supplier<String> debug(String message, Object... values) {
		return () -> {
			return String.format(message, values);
		};
	}

	/**
	 * Returns a supplier that will output an error message to the console, and
	 * then return that error message. Used within assertions.
	 *
	 * @param message the format message to output
	 * @param values the values to substitute into the message
	 * @return the formatted message
	 *
	 * @see String#formatted(Object...)
	 * @see System#err
	 * @see Supplier
	 */
	public static Supplier<String> fail(String message, Object... values) {
		return () -> {
			String formatted = String.format(message, values);
			System.err.printf(formatted);
			return formatted;
		};
	}

	/**
	 * Checks if a commit should be made before the tests are run.
	 *
	 * @throws Exception if unable to parse git information
	 *
	 * @see <a href="https://github.com/eclipse-jgit/jgit/wiki/User-Guide">jgit</a>
	 * @see <a href="https://github.com/centic9/jgit-cookbook">jgit-cookbook</a>
	 */
	@BeforeAll
	public static void checkCommits() throws Exception {
		if ("true".equals(System.getenv("GITHUB_ACTIONS"))) {
			// do not run on GitHub Actions environment
			return;
		}

		// sometimes test run in project-tests repo, not project source repo
		// need to find location of Driver class
		// see: https://stackoverflow.com/a/778246
		URL resource = Driver.class.getResource("Driver.class");

		if (resource == null || !resource.getProtocol().equalsIgnoreCase("file")) {
			Assertions.fail("Unable to locate Driver.java for project...");
		}

		// attempt to find .git folder
		Path driver = Path.of(resource.toURI());
		Path current = driver.getParent();

		Path gitDir = null;
		Path pomXml = null;

		while (current != null) {
			gitDir = current.resolve(".git");
			pomXml = current.resolve("pom.xml");

			if (Files.isDirectory(gitDir) && Files.isRegularFile(pomXml)) {
				break;
			}

			current = current.getParent();
		}

		if (!Files.isDirectory(gitDir)) {
			Assertions.fail("Unable to locate .git directory for project...");
		}

		// setup repository builder
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		builder = builder.setGitDir(gitDir.toFile())
				.readEnvironment()
				.findGitDir();

		// try to load repository
		try (
			Repository repository = builder.build();
			Git git = new Git(repository);
		) {
			Status status = git.status().call();

			// get uncommitted changes to java files only
			List<String> changes = status.getUncommittedChanges()
					.stream()
					.filter(s -> s.endsWith(".java"))
					.toList();

			// if there are uncommitted java changes,
			// check how long its been since the last commit
			if (!changes.isEmpty()) {
				System.err.printf("Found %d uncommitted changes: %s%n", changes.size(), changes);
				Iterator<RevCommit> logs = git.log().call().iterator();

				if (logs.hasNext()) {
					RevCommit commit = logs.next();

					// get commit date/time and current date/time in same time zone
					ZoneId zone = commit.getAuthorIdent().getTimeZone().toZoneId();
					Instant timestamp = Instant.ofEpochSecond(commit.getCommitTime());
					ZonedDateTime committed = ZonedDateTime.ofInstant(timestamp, zone);
					ZonedDateTime today = ZonedDateTime.now(zone);

					// get elapsed minutes since last commit
					Duration elapsed = Duration.between(committed, today);
					long minutes = elapsed.toMinutes();

					// output a warning and stop running tests if over 30 minutes
					if (minutes > 30) {
						String date = DateTimeFormatter.ofPattern("h:mm a 'on' MMM d, yyyy").format(committed);
						String output = "Your last commit was at " + date + ". " +
								"Please make a new commit before running the tests.";

						System.err.println(output);
						Assertions.fail(output);
					}
				}
				else {
					String output = "Please make a first commit before running the tests.";
					System.err.println(output);
					Assertions.fail(output);
				}
			}
		}
	}

	/**
	 * Makes sure the expected environment is setup before running any tests.
	 */
	@BeforeAll
	public static void setupEnvironment() {
		System.out.print(" Working Directory: ");
		System.out.println(Path.of(".").toAbsolutePath().normalize().getFileName());

		System.out.print("Expected Directory: ");
		System.out.println(EXPECTED.text);
		System.out.println();

		Assertions.assertTrue(Files.isDirectory(TEXT.path), fail("Unable to access: %s%n", TEXT.text));

		try {
			// create or clean up actual directory
			Files.createDirectories(ACTUAL.path);
			Assertions.assertTrue(Files.isWritable(ACTUAL.path), ACTUAL.text);

			if (!System.getenv().containsKey("SKIP_ACTUAL_CLEANUP")) {
				int count = deleteFiles(ACTUAL.path);
				System.out.printf("Removed %d old actual files...%n", count);
			}
		}
		catch (IOException e) {
			Assertions.fail("Unable to clean up actual output directory.");
		}

		try {
			// try to make Windows expected files if necessary
			if (!File.separator.equals("/")) {
				int count = copyExpected();
				System.out.printf("Converted %d files to Windows format...%n", count);
			}
		}
		catch (IOException e) {
			Assertions.fail("Unable to copy expected files for Windows systems.", e);
		}

		Assertions.assertTrue(Files.isDirectory(EXPECTED.path), fail("Unable to access: %s%n", EXPECTED.text));

		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
			UNCAUGHT.add(() -> Assertions.fail("Thread " + thread.getName() + " threw an exception.", throwable));
		});
	}

	/**
	 * Makes sure there is a test failure if any thread threw an otherwise uncaught
	 * exception.
	 */
	@AfterAll
	public static void checkUncaught() {
		if (!UNCAUGHT.isEmpty()) {
			Assertions.assertAll("One or more threads threw an exception.", UNCAUGHT);
		}
	}

	/**
	 * Checks line-by-line if two files are equal. If one file contains extra blank
	 * lines at the end of the file, the two are still considered equal. Works even
	 * if the path separators in each file are different.
	 *
	 * @param path1 path to first file to compare with
	 * @param path2 path to second file to compare with
	 * @return positive value if two files are equal, negative value if not
	 *
	 * @throws IOException if IO error occurs
	 */
	public static int compareFiles(Path path1, Path path2) throws IOException {
		// used to output line mismatch
		int count = 0;

		try (
				BufferedReader reader1 = Files.newBufferedReader(path1, UTF_8);
				BufferedReader reader2 = Files.newBufferedReader(path2, UTF_8);
		) {
			String line1 = reader1.readLine();
			String line2 = reader2.readLine();

			while (true) {
				count++;

				// compare lines until we hit a null (i.e. end of file)
				if (line1 != null && line2 != null) {
					// remove trailing spaces
					line1 = line1.stripTrailing();
					line2 = line2.stripTrailing();

					// check if lines are equal
					if (!line1.equals(line2)) {
						return -count;
					}

					// read next lines if we get this far
					line1 = reader1.readLine();
					line2 = reader2.readLine();
				}
				else {
					// discard extra blank lines at end of reader1
					while (line1 != null && line1.isBlank()) {
						line1 = reader1.readLine();
					}

					// discard extra blank lines at end of reader2
					while (line2 != null && line2.isBlank()) {
						line2 = reader2.readLine();
					}

					if (line1 == line2) {
						// only true if both are null, otherwise one file had extra non-empty lines
						return count;
					}

					// extra blank lines found in one file
					return -count;
				}
			}
		}
	}

	/**
	 * Deletes all files in a directory without suppressing any IO exceptions.
	 *
	 * @param directory the directory to delete files within
	 * @return number of files deleted
	 * @throws IOException if unable to list directory or delete files
	 */
	public static int deleteFiles(Path directory) throws IOException {
		int count = 0;

		try (
			Stream<Path> files = Files.walk(directory).filter(Files::isRegularFile);
		) {
			for (Path file: files.toList()) {
				Files.delete(file);
				count++;
			}
		}

		return count;
	}

	/**
	 * Converts forward slashes to backslashes.
	 *
	 * @param original the original file with forward slashes
	 * @param copy where to write the copy with backslashes
	 * @throws IOException if unable to read or write files
	 */
	public static void convertSlashes(Path original, Path copy) throws IOException {
		try (
				BufferedReader in = Files.newBufferedReader(original, UTF_8);
				BufferedWriter out = Files.newBufferedWriter(copy, UTF_8);
		) {
			String line = in.readLine();

			if (line != null) {
				// handle first line separately to avoid extra newline at end of file
				out.write(line.replace('/', '\\'));

				while ((line = in.readLine()) != null) {
					out.newLine();
					out.write(line.replace('/', '\\'));
				}
			}
		}
	}

	/**
	 * Copies the expected files for Unix-like operating systems to expected files
	 * for Windows operating systems.
	 *
	 * @return number of files copied
	 * @throws IOException if an IO error occurs
	 */
	public static int copyExpected() throws IOException {
		int count = 0;

		Path nix = Path.of("expected-nix");
		Path win = Path.of("expected-win");
		Path crawl = nix.resolve("crawl"); // do not convert URLs

		try (
				Stream<Path> stream = Files.walk(nix, FileVisitOption.FOLLOW_LINKS)
					.filter(Files::isReadable)
					.filter(Files::isRegularFile);
		) {
			for (Path original : stream.toList()) {
				Path copy = win.resolve(nix.relativize(original));

				if (!Files.isReadable(copy) || Files.size(original) != Files.size(copy)) {
					Files.createDirectories(copy.getParent());

					if (!original.startsWith(crawl)) {
						convertSlashes(original, copy);
					}
					else {
						Files.copy(original, copy);
					}

					count++;
				}
			}
		}

		return count;
	}

	/**
	 * Encourages the garbage collector to run; useful in between intensive groups
	 * of tests or before benchmarking.
	 */
	public static void freeMemory() {
		Runtime runtime = Runtime.getRuntime();
		long bytes = 1048576;
		double before = (double) (runtime.totalMemory() - runtime.freeMemory()) / bytes;

		// try to free up memory before another run of intensive tests
		runtime.gc();

		// collect rest of system information
		int processors = runtime.availableProcessors();
		double maximum = (double) runtime.maxMemory() / bytes;
		double after = (double) (runtime.totalMemory() - runtime.freeMemory()) / bytes;

		String format = """

				```
				%8.2f Processors
				%8.2f MB Memory Maximum
				%8.2f MB Memory Used (Before GC)
				%8.2f MB Memory Used (After GC)
				```

				""";

		System.out.printf(format, (double) processors, maximum, before, after);
	}

	/**
	 * Counts the number of failed tests so far. Used to prevent tests from running
	 * if there are any previous failures.
	 */
	public static class TestCounter implements TestWatcher {
		/** Tracks number of successes. */
		public static AtomicInteger passed = null;

		/** Tracks number of failures. */
		public static AtomicInteger failed = null;

		/** Initializes the test counter. */
		public TestCounter() {
			passed = new AtomicInteger(0);
			failed = new AtomicInteger(0);

			System.out.println(this.getClass().getSimpleName() + " initialized.");
		}

		@Override
		public void testSuccessful(ExtensionContext context) {
			passed.incrementAndGet();
		}

		@Override
		public void testFailed(ExtensionContext context, Throwable cause) {
			failed.incrementAndGet();
		}

		/**
		 * Asserts tests are passing. Primarily used to prevent connecting to a web
		 * server when tests are failing.
		 *
		 * @param info test information
		 */
		public static void assertNoFailures(TestInfo info) {
			String format = """
					Disabling "%s" due to earlier failing tests.
					""";

			Assertions.assertTrue(
					TestCounter.failed.get() == 0,
					() -> format.formatted(info.getDisplayName()));
		}
	}

	/** Creates a new instance of this class. */
	public ProjectTests() {
	}
}
