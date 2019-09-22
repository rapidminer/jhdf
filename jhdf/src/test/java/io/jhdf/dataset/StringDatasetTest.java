/*******************************************************************************
 * This file is part of jHDF. A pure Java library for accessing HDF5 files.
 *
 * http://jhdf.io
 *
 * Copyright 2019 James Mudd
 *
 * MIT License see 'LICENSE' file
 ******************************************************************************/
package io.jhdf.dataset;

import static io.jhdf.TestUtils.flatten;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;

public class StringDatasetTest {

	private static final String HDF5_TEST_EARLIEST_FILE_NAME = "../test_string_datasets_earliest.hdf5";
	private static final String HDF5_TEST_LATEST_FILE_NAME = "../test_string_datasets_latest.hdf5";

	private static HdfFile earliestHdfFile;
	private static HdfFile latestHdfFile;

	@BeforeAll
	static void setup() {
		String earliestTestFileUrl = StringDatasetTest.class.getResource(HDF5_TEST_EARLIEST_FILE_NAME).getFile();
		earliestHdfFile = new HdfFile(new File(earliestTestFileUrl));
		String latestTestFileUrl = StringDatasetTest.class.getResource(HDF5_TEST_LATEST_FILE_NAME).getFile();
		latestHdfFile = new HdfFile(new File(latestTestFileUrl));
	}

	@TestFactory
	Collection<DynamicNode> stringDataset1DTests() {
		// List of all the datasetPaths
		return Arrays.asList(
				dynamicContainer("earliest", Arrays.asList(
						dynamicTest("fixed ASCII",
								createTest(earliestHdfFile, "/fixed_length_ascii")),
						dynamicTest("fixed ASCII 1 char",
								createTest(earliestHdfFile, "/fixed_length_ascii_1_char")),
						dynamicTest("variable ASCII",
								createTest(earliestHdfFile, "/variable_length_ascii")),
						dynamicTest("variable UTF8",
								createTest(earliestHdfFile, "/variable_length_utf8")))),

				dynamicContainer("latest", Arrays.asList(
						dynamicTest("fixed ASCII",
								createTest(latestHdfFile, "/fixed_length_ascii")),
						dynamicTest("fixed ASCII 1 char",
								createTest(latestHdfFile, "/fixed_length_ascii_1_char")),
						dynamicTest("variable ASCII",
								createTest(latestHdfFile, "/variable_length_ascii")),
						dynamicTest("variable UTF8",
								createTest(latestHdfFile, "/variable_length_utf8")))));
	}

	private Executable createTest(HdfFile file, String datasetPath) {
		return () -> {
			Dataset dataset = file.getDatasetByPath(datasetPath);
			Object data = dataset.getData();
			assertThat(getDimensions(data), is(equalTo(new int[] { 10 })));
			Object[] flatData = flatten(data);
			for (int i = 0; i < flatData.length; i++) {
				// Do element comparison as there are all different primitive numeric types
				// convert to double
				assertThat(flatData[i], is(equalTo("string number " + i)));
			}
		};
	}

	@Test
	void test2DStringDatasetEarliest() {
		Dataset dataset = earliestHdfFile.getDatasetByPath("variable_length_2d");
		Object data = dataset.getData();
		assertThat(getDimensions(data), is(equalTo(new int[] { 5, 7 })));
		Object[] flatData = flatten(data);
		for (int i = 0; i < flatData.length; i++) {
			// Do element comparison as there are all different primitive numeric types
			// convert to double
			assertThat(flatData[i], is(equalTo(Integer.toString(i))));
		}
	}

	@Test
	void test2DStringDatasetLatest() {
		Dataset dataset = latestHdfFile.getDatasetByPath("variable_length_2d");
		Object data = dataset.getData();
		assertThat(getDimensions(data), is(equalTo(new int[] { 5, 7 })));
		Object[] flatData = flatten(data);
		for (int i = 0; i < flatData.length; i++) {
			// Do element comparison as there are all different primitive numeric types
			// convert to double
			assertThat(flatData[i], is(equalTo(Integer.toString(i))));
		}
	}

	private int[] getDimensions(Object data) {
		List<Integer> dims = new ArrayList<>();
		dims.add(Array.getLength(data));

		while (Array.get(data, 0).getClass().isArray()) {
			data = Array.get(data, 0);
			dims.add(Array.getLength(data));
		}
		return ArrayUtils.toPrimitive(dims.toArray(new Integer[0]));
	}
}
