package edu.usfca.cs272.tests;

import static edu.usfca.cs272.tests.utils.ProjectFlag.COUNTS;
import static edu.usfca.cs272.tests.utils.ProjectFlag.INDEX;
import static edu.usfca.cs272.tests.utils.ProjectFlag.PARTIAL;
import static edu.usfca.cs272.tests.utils.ProjectFlag.QUERY;
import static edu.usfca.cs272.tests.utils.ProjectFlag.RESULTS;
import static edu.usfca.cs272.tests.utils.ProjectFlag.TEXT;
import static edu.usfca.cs272.tests.utils.ProjectFlag.THREADS;
import static edu.usfca.cs272.tests.utils.ProjectPath.ACTUAL;
import static edu.usfca.cs272.tests.utils.ProjectPath.EXPECTED;
import static edu.usfca.cs272.tests.utils.ProjectPath.HELLO;
import static edu.usfca.cs272.tests.utils.ProjectPath.QUERY_COMPLEX;
import static edu.usfca.cs272.tests.utils.ProjectPath.QUERY_SIMPLE;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import edu.usfca.cs272.tests.utils.ProjectBenchmarks;
import edu.usfca.cs272.tests.utils.ProjectPath;
import edu.usfca.cs272.tests.utils.ProjectTests;

/**
 * A test suite for project v3.0.
 *
 * @author CS 272 Software Development (University of San Francisco)
 * @version Spring 2025
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@ExtendWith(ProjectTests.TestCounter.class)
public class ThreadSearchTests extends ProjectBenchmarks {
	/** Creates a new instance of this class. */
	public ThreadSearchTests() {}

	/**
	 * Tests that threads are being used for this project. These tests are slow and
	 * should only be run when needed. The tests are also imperfect and may not
	 * reliably pass unless on the GitHub Actions environment.
	 */
	@Nested
	@Order(1)
	@Tag("test-v3.0")
	@Tag("test-v3.1")
	@Tag("test-v3.2")
	@Tag("test-v3.3")
	@Tag("test-v3.4")
	@TestMethodOrder(OrderAnnotation.class)
	public class ApproachTests {
		/** Creates a new instance of this class. */
		public ApproachTests() {}

		/**
		 * See the JUnit output for test details.
		 */
		@Test
		@Order(1)
		public void testPartial() {
			String[] args = {
					TEXT.flag, ProjectPath.TEXT.text,
					QUERY.flag, ProjectPath.QUERY_COMPLEX.text,
					PARTIAL.flag,
					THREADS.flag, ProjectBenchmarks.Threads.TWO.text
			};

			Runnable test = () -> {
				assertNoExceptions(args, LONG_TIMEOUT);
				System.out.println("Random: " + Math.random());
			};

			ProjectBenchmarks.assertMultithreaded(test);
		}
	}

	/**
	 * Tests the output of this project.
	 */
	@Nested
	@Order(2)
	@Tag("test-v3.0")
	@TestMethodOrder(OrderAnnotation.class)
	public class ExactTests {
		/** The default search mode for this nested class. */
		public boolean partial;

		/** The search flag to add to the command-line arguments. */
		public String searchFlag;

		/** Creates a new instance of this class. */
		public ExactTests() {}

		/**
		 * Sets up the tests before running.
		 */
		@BeforeEach
		public void setup() {
			partial = false;
			searchFlag = partial ? PARTIAL.flag : "";
		}

		/**
		 * See the JUnit output for test details.
		 *
		 * @param threads the threads
		 */
		@ParameterizedTest
		@Order(1)
		@EnumSource()
		public void testSimple(ProjectBenchmarks.Threads threads) {
			testSearch(partial, "simple", ProjectPath.SIMPLE, threads);
		}

		/**
		 * See the JUnit output for test details.
		 *
		 * @param threads the threads
		 */
		@ParameterizedTest
		@Order(2)
		@EnumSource()
		public void testStems(ProjectBenchmarks.Threads threads) {
			testSearch(partial, "words", ProjectPath.STEMS, threads);
		}

		/**
		 * See the JUnit output for test details.
		 *
		 * @param threads the threads
		 */
		@ParameterizedTest
		@Order(3)
		@EnumSource()
		public void testRfcs(ProjectBenchmarks.Threads threads) {
			testSearch(partial, "letters", ProjectPath.RFCS, threads);
		}

		/**
		 * See the JUnit output for test details.
		 *
		 * @param threads the threads
		 */
		@ParameterizedTest
		@Order(4)
		@EnumSource()
		public void testGutenGreat(ProjectBenchmarks.Threads threads) {
			testSearch(partial, "complex", ProjectPath.GUTEN_GREAT, threads);
		}

		/**
		 * See the JUnit output for test details.
		 *
		 * @param threads the threads
		 */
		@ParameterizedTest
		@Order(5)
		@EnumSource()
		public void testGuten(ProjectBenchmarks.Threads threads) {
			testSearch(partial, "complex", ProjectPath.GUTEN, threads);
		}

		/**
		 * See the JUnit output for test details.
		 *
		 * @param threads the threads
		 */
		@ParameterizedTest
		@Order(6)
		@EnumSource()
		public void testTextRespect(ProjectBenchmarks.Threads threads) {
			testSearch(partial, "respect", ProjectPath.TEXT, threads);
		}

		/**
		 * See the JUnit output for test details.
		 *
		 * @param threads the threads
		 */
		@ParameterizedTest
		@Order(7)
		@EnumSource()
		@Tag("test-v3.1")
		@Tag("test-v3.2")
		@Tag("test-v3.3")
		@Tag("test-v3.4")
		public void testTextComplex(ProjectBenchmarks.Threads threads) {
			testSearch(partial, "complex", ProjectPath.TEXT, threads);
		}

		/**
		 * Free up memory after running --- useful for following tests.
		 */
		@AfterAll
		public static void freeMemory() {
			ProjectTests.freeMemory();
		}
	}

	/**
	 * Tests the output of this project.
	 */
	@Nested
	@Order(3)
	@Tag("test-v3.0")
	@TestMethodOrder(OrderAnnotation.class)
	public class PartialTests extends ExactTests {
		/** Creates a new instance of this class. */
		public PartialTests() {}

		/**
		 * Sets up the tests before running.
		 */
		@Override
		@BeforeEach
		public void setup() {
			super.partial = true;
			super.searchFlag = partial ? PARTIAL.flag : "";
		}
	}

	/**
	 * Tests the index exception handling of this project.
	 */
	@Nested
	@Order(4)
	@Tag("test-v3.0")
	@Tag("test-v3.1")
	@Tag("test-v3.2")
	@Tag("test-v3.3")
	@Tag("test-v3.4")
	@Tag("past-v4")
	@Tag("past-v5")
	@TestMethodOrder(OrderAnnotation.class)
	public class ExceptionTests {
		/** Creates a new instance of this class. */
		public ExceptionTests() {}

		/**
		 * Tests no exceptions are thrown with the provided arguments.
		 *
		 * @throws Exception if exception occurs
		 */
		@Test
		@Order(1)
		public void testNegativeThreads() throws Exception {
			String[] args = { TEXT.flag, HELLO.text, THREADS.flag, "-4", QUERY.flag, QUERY_SIMPLE.text, RESULTS.flag };
			Files.deleteIfExists(RESULTS.path);
			assertNoExceptions(args, SHORT_TIMEOUT);
			Assertions.assertTrue(Files.exists(RESULTS.path), RESULTS.value);
		}

		/**
		 * Tests no exceptions are thrown with the provided arguments.
		 *
		 * @throws Exception if exception occurs
		 */
		@Test
		@Order(2)
		public void testZeroThreads() throws Exception {
			String[] args = { TEXT.flag, HELLO.text, THREADS.flag, "0", QUERY.flag, QUERY_SIMPLE.text, RESULTS.flag };
			Files.deleteIfExists(RESULTS.path);
			assertNoExceptions(args, SHORT_TIMEOUT);
			Assertions.assertTrue(Files.exists(RESULTS.path), RESULTS.value);
		}

		/**
		 * Tests no exceptions are thrown with the provided arguments.
		 *
		 * @throws Exception if exception occurs
		 */
		@Test
		@Order(3)
		public void testFractionThreads() throws Exception {
			String[] args = { TEXT.flag, HELLO.text, THREADS.flag, "3.14", QUERY.flag, QUERY_SIMPLE.text, RESULTS.flag };
			Files.deleteIfExists(RESULTS.path);
			assertNoExceptions(args, SHORT_TIMEOUT);
			Assertions.assertTrue(Files.exists(RESULTS.path), RESULTS.value);
		}

		/**
		 * Tests no exceptions are thrown with the provided arguments.
		 *
		 * @throws Exception if exception occurs
		 */
		@Test
		@Order(4)
		public void testWordThreads() throws Exception {
			String[] args = { TEXT.flag, HELLO.text, THREADS.flag, "fox", QUERY.flag, QUERY_SIMPLE.text, RESULTS.flag };
			Files.deleteIfExists(RESULTS.path);
			assertNoExceptions(args, SHORT_TIMEOUT);
			Assertions.assertTrue(Files.exists(RESULTS.path), RESULTS.value);
		}

		/**
		 * Tests no exceptions are thrown with the provided arguments.
		 *
		 * @throws Exception if exception occurs
		 */
		@Test
		@Order(5)
		public void testDefaultThreads() throws Exception {
			String[] args = { TEXT.flag, HELLO.text, THREADS.flag, QUERY.flag, QUERY_SIMPLE.text, RESULTS.flag };
			Files.deleteIfExists(RESULTS.path);
			assertNoExceptions(args, SHORT_TIMEOUT);
			Assertions.assertTrue(Files.exists(RESULTS.path), RESULTS.value);
		}

		/**
		 * Tests no exceptions are thrown with the provided arguments.
		 *
		 * @throws Exception if exception occurs
		 */
		@Test
		@Order(6)
		public void testNoOutput() throws Exception {
			String[] args = { TEXT.flag, HELLO.text, THREADS.flag, QUERY.flag, QUERY_SIMPLE.text };
			assertNoExceptions(args, SHORT_TIMEOUT);
		}

		/**
		 * Tests no exceptions are thrown with the provided arguments.
		 *
		 * @throws Exception if exception occurs
		 */
		@Test
		@Order(7)
		public void testOnlyThreadsAndPartial() throws Exception {
			String[] args = { THREADS.flag, PARTIAL.flag };
			assertNoExceptions(args, SHORT_TIMEOUT);
		}

		/**
		 * Tests no exceptions are thrown with the provided arguments.
		 *
		 * @throws Exception if exception occurs
		 */
		@Test
		@Order(8)
		public void testOnlyThreadsAndOutput() throws Exception {
			String[] args = { THREADS.flag, RESULTS.flag };
			Files.deleteIfExists(RESULTS.path);
			assertNoExceptions(args, SHORT_TIMEOUT);
			Assertions.assertTrue(Files.exists(RESULTS.path), RESULTS.value);
		}
	}

	/**
	 * Tests specific to multithreading.
	 *
	 * THESE ARE SLOW TESTS. AVOID RUNNING UNLESS REALLY NEEDED.
	 */
	@Nested
	@Order(4)
	@TestMethodOrder(OrderAnnotation.class)
	public class ThreadTests {
		/** Creates a new instance of this class. */
		public ThreadTests() {}

		/**
		 * Sets up the tests before running. Only runs the tests if other tests had
		 * no failures.
		 *
		 * @param info test information
		 */
		@BeforeAll
		public static void checkStatus(TestInfo info) {
			// disable if there were earlier failures
			ProjectTests.TestCounter.assertNoFailures(info);
		}

		/**
		 * See the JUnit output for test details.
		 */
		@Test
		@Order(1)
		@Tag("time-v3.0")
		public void okaySearchOneMany() {
			// should be slightly faster
			timeSearchOneMany(1.01);
		}

		/**
		 * See the JUnit output for test details.
		 */
		@Test
		@Order(2)
		@Tag("time-v3.0")
		public void slowSearchSingleMulti() {
			// shouldn't be significantly slower
			timeSearchSingleMulti(0.9);
		}
	}

	/**
	 * All-in-one tests of this project functionality.
	 */
	@Nested
	@Order(5)
	@Tag("past-v4")
	@Tag("past-v5")
	@TestMethodOrder(OrderAnnotation.class)
	public class ComboTests {
		/** Creates a new instance of this class. */
		public ComboTests() {}

		/**
		 * See the JUnit output for test details.
		 *
		 * @param partial whether partial or exact search is enabled
		 */
		@ParameterizedTest
		@ValueSource(booleans = { false, true })
		@Order(1)
		public void testCountsIndexResults(boolean partial) {
			ProjectPath input = ProjectPath.TEXT;
			ProjectBenchmarks.Threads threads = ProjectBenchmarks.Threads.TWO;

			String query = "complex";
			String type = partial ? "partial" : "exact";

			String indexName = String.format("index-%s", input.id);
			String countsName = String.format("counts-%s", input.id);
			String resultsName = String.format("%s-%s-%s", type, query, input.id);

			Path indexActual = ACTUAL.resolve(indexName + "-" + threads.text + ".json");
			Path countsActual = ACTUAL.resolve(countsName + "-" + threads.text + ".json");
			Path resultsActual = ACTUAL.resolve(resultsName + "-" + threads.text + ".json");

			String[] args = {
					TEXT.flag, input.text,
					INDEX.flag, indexActual.toString(),
					COUNTS.flag, countsActual.toString(),
					QUERY.flag, ProjectPath.QUERY.resolve(query + ".txt").toString(),
					partial ? PARTIAL.flag : "",
					RESULTS.flag, resultsActual.toString(),
					THREADS.flag, threads.text
			};

			Map<Path, Path> files = Map.of(
					countsActual, EXPECTED.resolve("counts").resolve(countsName + ".json"),
					indexActual, EXPECTED.resolve("index").resolve(indexName + ".json"),
					resultsActual, EXPECTED.resolve(type).resolve(resultsName + ".json")
			);

			Executable test = () -> ProjectTests.checkOutput(args, files);
			Assertions.assertTimeoutPreemptively(LONG_TIMEOUT, test);
		}
	}

	/**
	 * Generates the arguments to use for this test case. Designed to be used
	 * inside a JUnit test.
	 * @param partial whether to perform exact or partial search
	 * @param query the query file to use for search
	 * @param input the input path to use
	 * @param threads the threads
	 */
	public static void testSearch(boolean partial, String query, ProjectPath input, ProjectBenchmarks.Threads threads) {
		String type = partial ? "partial" : "exact";
		String single = String.format("%s-%s-%s.json", type, query, input.id);
		String threaded = String.format("%s-%s-%s-%s.json", type, query, input.id, threads.text);

		Path actual = ACTUAL.resolve(threaded).normalize();
		Path expected = EXPECTED.resolve(type).resolve(single).normalize();
		Path queries = ProjectPath.QUERY.resolve(query + ".txt").normalize();

		String[] args = {
				TEXT.flag, input.text, QUERY.flag, queries.toString(),
				RESULTS.flag, actual.toString(), partial ? PARTIAL.flag : "",
				THREADS.flag, threads.text
		};

		Executable debug = () -> checkOutput(args, actual, expected);
		Assertions.assertTimeoutPreemptively(LONG_TIMEOUT, debug);
	}

	/**
	 * Time building with 1 versus many workers.
	 *
	 * @param target the target speedup to pass these tests
	 */
	public static void timeSearchOneMany(double target) {
		String[] args1 = {
				TEXT.flag, ProjectPath.TEXT.text, QUERY.flag, QUERY_COMPLEX.text,
				PARTIAL.flag, THREADS.flag, String.valueOf(1)
		};

		String[] args2 = {
				TEXT.flag, ProjectPath.TEXT.text, QUERY.flag, QUERY_COMPLEX.text,
				PARTIAL.flag, THREADS.flag, BENCH_WORKERS.text
		};

		// make sure code runs without exceptions before testing
		assertNoExceptions(args1, SHORT_TIMEOUT);
		assertNoExceptions(args2, SHORT_TIMEOUT);

		// then test the timing
		assertTimeoutPreemptively(LONG_TIMEOUT, () -> {
			double result = compare("Search", "1 Worker", args1, BENCH_WORKERS.text + " Workers", args2);
			Supplier<String> debug = () -> String.format(format, BENCH_WORKERS.num, result, target, "1 worker");
			assertTrue(result >= target, debug);
		});
	}

	/**
	 * Time searching with single versus threading.
	 *
	 * @param target the target speedup to pass these tests
	 */
	public static void timeSearchSingleMulti(double target) {
		String[] args1 = { TEXT.flag, ProjectPath.TEXT.text, QUERY.flag, QUERY_COMPLEX.text, PARTIAL.flag };

		String[] args2 = {
				TEXT.flag, ProjectPath.TEXT.text, QUERY.flag, QUERY_COMPLEX.text,
				PARTIAL.flag, THREADS.flag, BENCH_MULTI.text
		};

		// make sure code runs without exceptions before testing
		assertNoExceptions(args1, SHORT_TIMEOUT);
		assertNoExceptions(args2, SHORT_TIMEOUT);

		// then test the timing
		assertTimeoutPreemptively(LONG_TIMEOUT, () -> {
			double result = compare("Search", "Single", args1, BENCH_MULTI.text + " Workers", args2);
			Supplier<String> debug = () -> String.format(format, BENCH_MULTI.num, result, target, "single-threading");
			assertTrue(result >= target, debug);
		});
	}
}
