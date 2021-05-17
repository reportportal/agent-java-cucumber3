/*
 *  Copyright 2020 EPAM Systems
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ItemStatus;
import cucumber.api.Result;
import cucumber.api.TestStep;
import cucumber.runtime.StepDefinitionMatch;
import io.reactivex.annotations.Nullable;
import rp.com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Optional.ofNullable;

class Utils {
	private static final String DEFINITION_MATCH_FIELD_NAME = "definitionMatch";
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String METHOD_FIELD_NAME = "method";
	private static final String ONE_SPACE = "&nbsp;";
	private static final String NEW_LINE = "\r\n";
	private static final String TABLE_INDENT = "&nbsp;&nbsp;&nbsp;&nbsp;";
	private static final String TABLE_SEPARATOR = "|";

	//@formatter:off
	public static final Map<Result.Type, ItemStatus> STATUS_MAPPING = ImmutableMap.<Result.Type, ItemStatus>builder()
			.put(Result.Type.PASSED, ItemStatus.PASSED)
			.put(Result.Type.FAILED, ItemStatus.FAILED)
			.put(Result.Type.SKIPPED, ItemStatus.SKIPPED)
			.put(Result.Type.PENDING, ItemStatus.SKIPPED)
			.put(Result.Type.AMBIGUOUS, ItemStatus.SKIPPED)
			.put(Result.Type.UNDEFINED, ItemStatus.SKIPPED).build();

	public static final Map<Result.Type, String> LOG_LEVEL_MAPPING = ImmutableMap.<Result.Type, String>builder()
			.put(Result.Type.PASSED, "INFO")
			.put(Result.Type.FAILED, "ERROR")
			.put(Result.Type.SKIPPED, "WARN")
			.put(Result.Type.PENDING, "WARN")
			.put(Result.Type.AMBIGUOUS, "WARN")
			.put(Result.Type.UNDEFINED, "WARN").build();
	//@formatter:on

	private Utils() {
		throw new AssertionError("No instances should exist for the class!");
	}

	/**
	 * Generate name representation
	 *
	 * @param prefix   - substring to be prepended at the beginning (optional)
	 * @param infix    - substring to be inserted between keyword and name
	 * @param argument - main text to process
	 * @return transformed string
	 */
	public static String buildName(String prefix, String infix, String argument) {
		return (prefix == null ? "" : prefix) + infix + argument;
	}

	public static Method retrieveMethod(Field definitionMatchField, TestStep testStep) throws NoSuchFieldException, IllegalAccessException {
		StepDefinitionMatch stepDefinitionMatch = (StepDefinitionMatch) definitionMatchField.get(testStep);
		Field stepDefinitionField = stepDefinitionMatch.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
		stepDefinitionField.setAccessible(true);
		Object javaStepDefinition = stepDefinitionField.get(stepDefinitionMatch);
		Field methodField = javaStepDefinition.getClass().getDeclaredField(METHOD_FIELD_NAME);
		methodField.setAccessible(true);
		return (Method) methodField.get(javaStepDefinition);
	}

	public static final java.util.function.Function<List<cucumber.api.Argument>, List<?>> ARGUMENTS_TRANSFORM = arguments -> ofNullable(
			arguments).map(args -> args.stream().map(cucumber.api.Argument::getValue).collect(Collectors.toList())).orElse(null);

	@Nullable
	public static Field getDefinitionMatchField(@Nonnull TestStep testStep) {
		Class<?> clazz = testStep.getClass();

		try {
			return clazz.getField(DEFINITION_MATCH_FIELD_NAME);
		} catch (NoSuchFieldException e) {
			do {
				try {
					Field definitionMatchField = clazz.getDeclaredField(DEFINITION_MATCH_FIELD_NAME);
					definitionMatchField.setAccessible(true);
					return definitionMatchField;
				} catch (NoSuchFieldException ignore) {
				}

				clazz = clazz.getSuperclass();
			} while (clazz != null);

			return null;
		}
	}

	/**
	 * Converts a table represented as List of Lists to a formatted table string
	 *
	 * @param table a table object
	 * @return string representation of the table
	 */
	@Nonnull
	public static String formatDataTable(@Nonnull final List<List<String>> table) {
		StringBuilder result = new StringBuilder();
		int tableLength = table.stream().mapToInt(List::size).max().orElse(-1);
		List<Iterator<String>> iterList = table.stream().map(List::iterator).collect(Collectors.toList());
		List<Integer> colSizes = IntStream.range(0, tableLength)
				.mapToObj(n -> iterList.stream().filter(Iterator::hasNext).map(Iterator::next).collect(Collectors.toList()))
				.map(col -> col.stream().mapToInt(String::length).max().orElse(0))
				.collect(Collectors.toList());

		for (List<String> row : table) {
			result.append(TABLE_INDENT).append(TABLE_SEPARATOR);
			for (int i = 0; i < row.size(); i++) {
				String cell = row.get(i);
				int maxSize = colSizes.get(i) - cell.length() + 2;
				int lSpace = maxSize / 2;
				int rSpace = maxSize - lSpace;
				IntStream.range(0, lSpace).forEach(j -> result.append(ONE_SPACE));
				result.append(cell);
				IntStream.range(0, rSpace).forEach(j -> result.append(ONE_SPACE));
				result.append(TABLE_SEPARATOR);
			}
			result.append(NEW_LINE);
		}
		return result.toString().trim();
	}
}
