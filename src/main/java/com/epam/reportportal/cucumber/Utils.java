/*
 * Copyright 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.cucumber;

import com.epam.reportportal.annotations.TestCaseId;
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.Statuses;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.utils.AttributeParser;
import com.epam.reportportal.utils.TestCaseIdUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import cucumber.api.HookTestStep;
import cucumber.api.PickleStepTestStep;
import cucumber.api.Result;
import cucumber.api.TestStep;
import cucumber.runtime.StepDefinitionMatch;
import gherkin.ast.Tag;
import gherkin.pickles.*;
import io.reactivex.Maybe;
import io.reactivex.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Function;
import rp.com.google.common.collect.ImmutableMap;
import rp.com.google.common.collect.Lists;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Optional.ofNullable;

class Utils {
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
	private static final String TABLE_SEPARATOR = "|";
	private static final String DOCSTRING_DECORATOR = "\n\"\"\"\n";
	private static final String DEFINITION_MATCH_FIELD_NAME = "definitionMatch";
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String GET_LOCATION_METHOD_NAME = "getLocation";
	private static final String METHOD_OPENING_BRACKET = "(";
	private static final String METHOD_FIELD_NAME = "method";
	private static final String PARAMETER_REGEX = "<[^<>]+>";

	//@formatter:off
	private static final Map<Result.Type, ItemStatus> STATUS_MAPPING = ImmutableMap.<Result.Type, ItemStatus>builder()
			.put(Result.Type.PASSED, ItemStatus.PASSED)
			.put(Result.Type.FAILED, ItemStatus.FAILED)
			.put(Result.Type.SKIPPED, ItemStatus.SKIPPED)
			.put(Result.Type.PENDING, ItemStatus.SKIPPED)
			.put(Result.Type.AMBIGUOUS, ItemStatus.SKIPPED)
			.put(Result.Type.UNDEFINED, ItemStatus.SKIPPED).build();
	//@formatter:on

	private Utils() {
		throw new AssertionError("No instances should exist for the class!");
	}


	/**
	 * Map Cucumber statuses to RP item statuses
	 *
	 * @param status - Cucumber status
	 * @return RP test item status and null if status is null
	 */
	static String mapItemStatus(Result.Type status) {
		if (status == null) {
			return null;
		} else {
			if (STATUS_MAPPING.get(status) == null) {
				LOGGER.error(String.format("Unable to find direct mapping between Cucumber and ReportPortal for TestItem with status: '%s'.", status));
				return ItemStatus.SKIPPED.name();
			}
			return STATUS_MAPPING.get(status).name();
		}
	}

	static void finishTestItem(Launch rp, Maybe<String> itemId) {
		finishTestItem(rp, itemId, null);
	}

	static void finishTestItem(Launch rp, Maybe<String> itemId, Result.Type status) {
		if (itemId == null) {
			LOGGER.error("BUG: Trying to finish unspecified test item.");
			return;
		}

		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setStatus(mapItemStatus(status));
		rq.setEndTime(Calendar.getInstance().getTime());

		rp.finishTestItem(itemId, rq);

	}

	static Maybe<String> startNonLeafNode(Launch rp, Maybe<String> rootItemId, String name, String description,
			Set<ItemAttributesRQ> attributes, String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setDescription(description);
		rq.setName(name);
		rq.setAttributes(attributes);
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(type);

		return rp.startTestItem(rootItemId, rq);
	}

	static void sendLog(final String message, final String level, final File file) {
		ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
			@Override
			public SaveLogRQ apply(String itemUuid) {
				SaveLogRQ rq = new SaveLogRQ();
				rq.setMessage(message);
				rq.setItemUuid(itemUuid);
				rq.setLevel(level);
				rq.setLogTime(Calendar.getInstance().getTime());
				if (file != null) {
					rq.setFile(file);
				}
				return rq;
			}
		});
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	static Set<ItemAttributesRQ> extractPickleTags(List<PickleTag> tags) {
		Set<ItemAttributesRQ> attributes = new HashSet<ItemAttributesRQ>();
		for (PickleTag tag : tags) {
			attributes.add(new ItemAttributesRQ(null, tag.getName()));
		}
		return attributes;
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	static Set<ItemAttributesRQ> extractAttributes(List<Tag> tags) {
		Set<ItemAttributesRQ> attributes = new HashSet<ItemAttributesRQ>();
		for (Tag tag : tags) {
			attributes.add(new ItemAttributesRQ(null, tag.getName()));
		}
		return attributes;
	}

	/**
	 * Map Cucumber statuses to RP log levels
	 *
	 * @param cukesStatus - Cucumber status
	 * @return regular log level
	 */
	static String mapLevel(String cukesStatus) {
		String mapped = null;
		if (cukesStatus.equalsIgnoreCase("passed")) {
			mapped = "INFO";
		} else if (cukesStatus.equalsIgnoreCase("skipped")) {
			mapped = "WARN";
		} else {
			mapped = "ERROR";
		}
		return mapped;
	}

	/**
	 * Generate name representation
	 *
	 * @param prefix   - substring to be prepended at the beginning (optional)
	 * @param infix    - substring to be inserted between keyword and name
	 * @param argument - main text to process
	 * @param suffix   - substring to be appended at the end (optional)
	 * @return transformed string
	 */
	//TODO: pass Node as argument, not test event
	static String buildNodeName(String prefix, String infix, String argument, String suffix) {
		return buildName(prefix, infix, argument, suffix);
	}

	private static String buildName(String prefix, String infix, String argument, String suffix) {
		return (prefix == null ? "" : prefix) + infix + argument + (suffix == null ? "" : suffix);
	}

	/**
	 * Generate multiline argument (DataTable or DocString) representation
	 *
	 * @param step - Cucumber step object
	 * @return - transformed multiline argument (or empty string if there is
	 * none)
	 */
	static String buildMultilineArgument(TestStep step) {
		List<PickleRow> table = null;
		String dockString = "";
		StringBuilder marg = new StringBuilder();
		PickleStepTestStep pickleStep = (PickleStepTestStep) step;
		if (!pickleStep.getStepArgument().isEmpty()) {
			Argument argument = pickleStep.getStepArgument().get(0);
			if (argument instanceof PickleString) {
				dockString = ((PickleString) argument).getContent();
			} else if (argument instanceof PickleTable) {
				table = ((PickleTable) argument).getRows();
			}
		}
		if (table != null) {
			marg.append("\r\n");
			for (PickleRow row : table) {
				marg.append(TABLE_SEPARATOR);
				for (PickleCell cell : row.getCells()) {
					marg.append(" ").append(cell.getValue()).append(" ").append(TABLE_SEPARATOR);
				}
				marg.append("\r\n");
			}
		}

		if (!dockString.isEmpty()) {
			marg.append(DOCSTRING_DECORATOR).append(dockString).append(DOCSTRING_DECORATOR);
		}
		return marg.toString();
	}

	static String getStepName(TestStep step) {
		String stepName;
		if (step instanceof HookTestStep) {
			stepName = "Hook: " + ((HookTestStep) step).getHookType().toString();
		} else {
			stepName = ((PickleStepTestStep) step).getPickleStep().getText();
		}

		return stepName;
	}

	@Nullable
	public static Set<ItemAttributesRQ> getAttributes(TestStep testStep) {
		Field definitionMatchField = getDefinitionMatchField(testStep);
		if (definitionMatchField != null) {
			try {
				Method method = retrieveMethod(definitionMatchField, testStep);
				Attributes attributesAnnotation = method.getAnnotation(Attributes.class);
				if (attributesAnnotation != null) {
					return AttributeParser.retrieveAttributes(attributesAnnotation);
				}
			} catch (NoSuchFieldException | IllegalAccessException e) {
				return null;
			}
		}
		return null;
	}

	@Nullable
	static String getCodeRef(TestStep testStep) {

		Field definitionMatchField = getDefinitionMatchField(testStep);

		if (definitionMatchField != null) {

			try {
				StepDefinitionMatch stepDefinitionMatch = (StepDefinitionMatch) definitionMatchField.get(testStep);
				Field stepDefinitionField = stepDefinitionMatch.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
				stepDefinitionField.setAccessible(true);
				Object javaStepDefinition = stepDefinitionField.get(stepDefinitionMatch);
				Method getLocationMethod = javaStepDefinition.getClass().getDeclaredMethod(GET_LOCATION_METHOD_NAME, boolean.class);
				getLocationMethod.setAccessible(true);
				String fullCodeRef = String.valueOf(getLocationMethod.invoke(javaStepDefinition, true));
				return !"null".equals(fullCodeRef) ? fullCodeRef.substring(0, fullCodeRef.indexOf(METHOD_OPENING_BRACKET)) : null;
			} catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				return null;
			}

		} else {
			return null;
		}

	}

	public static TestCaseIdEntry getTestCaseId(TestStep testStep, String codeRef) {
		Field definitionMatchField = getDefinitionMatchField(testStep);
		if (definitionMatchField != null) {
			try {
				Method method = retrieveMethod(definitionMatchField, testStep);
				TestCaseId testCaseIdAnnotation = method.getAnnotation(TestCaseId.class);
				return ofNullable(testCaseIdAnnotation).flatMap(annotation -> ofNullable(getTestCaseId(testCaseIdAnnotation,
						method,
						((PickleStepTestStep) testStep).getDefinitionArgument()
				))).orElseGet(() -> getTestCaseId(codeRef, ((PickleStepTestStep) testStep).getDefinitionArgument()));
			} catch (NoSuchFieldException | IllegalAccessException e) {
				return getTestCaseId(codeRef, ((PickleStepTestStep) testStep).getDefinitionArgument());
			}
		} else {
			return getTestCaseId(codeRef, ((PickleStepTestStep) testStep).getDefinitionArgument());
		}
	}

	static List<ParameterResource> getParameters(List<cucumber.api.Argument> arguments, String text) {
		List<ParameterResource> parameters = Lists.newArrayList();
		ArrayList<String> parameterNames = Lists.newArrayList();
		Matcher matcher = Pattern.compile(PARAMETER_REGEX).matcher(text);
		while (matcher.find()) {
			parameterNames.add(text.substring(matcher.start() + 1, matcher.end() - 1));
		}
		IntStream.range(0, parameterNames.size()).forEach(index -> {
			String parameterName = parameterNames.get(index);
			if (index < arguments.size()) {
				String parameterValue = arguments.get(index).getValue();
				ParameterResource parameterResource = new ParameterResource();
				parameterResource.setKey(parameterName);
				parameterResource.setValue(parameterValue);
				parameters.add(parameterResource);
			}
		});
		return parameters;
	}

	private static Method retrieveMethod(Field definitionMatchField, TestStep testStep)
			throws NoSuchFieldException, IllegalAccessException {
		StepDefinitionMatch stepDefinitionMatch = (StepDefinitionMatch) definitionMatchField.get(testStep);
		Field stepDefinitionField = stepDefinitionMatch.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
		stepDefinitionField.setAccessible(true);
		Object javaStepDefinition = stepDefinitionField.get(stepDefinitionMatch);
		Field methodField = javaStepDefinition.getClass().getDeclaredField(METHOD_FIELD_NAME);
		methodField.setAccessible(true);
		return (Method) methodField.get(javaStepDefinition);
	}

	@Nullable
	private static TestCaseIdEntry getTestCaseId(TestCaseId testCaseId, Method method, List<cucumber.api.Argument> arguments) {
		if (testCaseId.parametrized()) {
			List<String> values = new ArrayList<String>(arguments.size());
			for (cucumber.api.Argument argument : arguments) {
				values.add(argument.getValue());
			}
			return TestCaseIdUtils.getParameterizedTestCaseId(method, values.toArray());
		} else {
			return new TestCaseIdEntry(testCaseId.value());
		}
	}

	private static TestCaseIdEntry getTestCaseId(String codeRef, List<cucumber.api.Argument> arguments) {
		return ofNullable(arguments).filter(args -> !args.isEmpty())
				.map(args -> new TestCaseIdEntry(codeRef + TRANSFORM_PARAMETERS.apply(args)))
				.orElseGet(() -> new TestCaseIdEntry(codeRef));
	}

	private static final Function<List<cucumber.api.Argument>, String> TRANSFORM_PARAMETERS = it -> it.stream()
			.map(cucumber.api.Argument::getValue)
			.collect(Collectors.joining(",", "[", "]"));

	@Nullable
	private static Field getDefinitionMatchField(TestStep testStep) {

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
}
