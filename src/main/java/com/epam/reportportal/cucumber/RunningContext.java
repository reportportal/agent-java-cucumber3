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

import cucumber.api.PickleStepTestStep;
import cucumber.api.Result;
import cucumber.api.TestCase;
import cucumber.api.TestStep;
import cucumber.api.event.TestSourceRead;
import gherkin.AstBuilder;
import gherkin.Parser;
import gherkin.ParserException;
import gherkin.TokenMatcher;
import gherkin.ast.*;
import io.reactivex.Maybe;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Running context that contains mostly manipulations with Gherkin objects.
 * Keeps necessary information regarding current Feature, Scenario and Step
 *
 * @author Serhii Zharskyi
 * @author Vitaliy Tsvihun
 * @author Vadzim Hushchanskou
 */
public class RunningContext {

	private RunningContext() {
		throw new AssertionError("No instances should exist for the class!");
	}

	public static class FeatureContext {
		private static final Map<String, TestSourceRead> PATH_TO_READ_EVENT_MAP = new ConcurrentHashMap<>();
		private final String currentFeatureUri;
		private final Feature currentFeature;
		private Maybe<String> currentFeatureId;

		public FeatureContext(TestCase testCase) {
			TestSourceRead event = PATH_TO_READ_EVENT_MAP.get(testCase.getUri());
			currentFeature = getFeature(event.source);
			currentFeatureUri = event.uri;
		}

		public static void addTestSourceReadEvent(String path, TestSourceRead event) {
			PATH_TO_READ_EVENT_MAP.put(path, event);
		}

		public ScenarioContext getScenarioContext(TestCase testCase) {
			ScenarioDefinition scenario = getScenario(testCase);
			ScenarioContext context = new ScenarioContext();
			context.processScenario(scenario);
			context.setTestCase(testCase);
			context.processBackground(getBackground());
			context.processScenarioOutline(scenario);
			context.setFeatureUri(getUri());
			return context;
		}

		public Feature getFeature(String source) {
			Parser<GherkinDocument> parser = new Parser<>(new AstBuilder());
			TokenMatcher matcher = new TokenMatcher();
			GherkinDocument gherkinDocument;
			try {
				gherkinDocument = parser.parse(source, matcher);
			} catch (ParserException e) {
				// Ignore exceptions
				return null;
			}
			return gherkinDocument.getFeature();
		}

		public Background getBackground() {
			ScenarioDefinition background = getFeature().getChildren().get(0);
			return background instanceof Background ? (Background) background : null;
		}

		public Feature getFeature() {
			return currentFeature;
		}

		public String getUri() {
			return currentFeatureUri;
		}

		public Maybe<String> getFeatureId() {
			return currentFeatureId;
		}

		public void setFeatureId(Maybe<String> featureId) {
			this.currentFeatureId = featureId;
		}

		@SuppressWarnings("unchecked")
		public <T extends ScenarioDefinition> T getScenario(TestCase testCase) {
			List<ScenarioDefinition> featureScenarios = getFeature().getChildren();
			for (ScenarioDefinition scenario : featureScenarios) {
				if (scenario instanceof Background) {
					continue;
				}
				if (testCase.getLine() == scenario.getLocation().getLine() && testCase.getName().equals(scenario.getName())) {
					return (T) scenario;
				} else {
					if (scenario instanceof ScenarioOutline) {
						for (Examples example : ((ScenarioOutline) scenario).getExamples()) {
							for (TableRow tableRow : example.getTableBody()) {
								if (tableRow.getLocation().getLine() == testCase.getLine()) {
									return (T) scenario;
								}
							}
						}
					}
				}
			}
			throw new IllegalStateException("Scenario can't be null!");
		}
	}

	public static class ScenarioContext {
		private static final Map<ScenarioDefinition, List<Integer>> scenarioOutlineMap = new ConcurrentHashMap<>();

		private final Queue<Step> backgroundSteps = new ArrayDeque<>();
		private final Map<Integer, Step> scenarioLocationMap = new HashMap<>();
		private Maybe<String> currentStepId;
		private Maybe<String> hookStepId;
		private Result.Type hookStatus;
		private Maybe<String> id;
		private Background background;
		private ScenarioDefinition scenario;
		private TestCase testCase;
		private boolean hasBackground = false;
		private String outlineIteration;
		private String uri;
		private String text;

		public void processScenario(ScenarioDefinition scenario) {
			this.scenario = scenario;
			for (Step step : scenario.getSteps()) {
				scenarioLocationMap.put(step.getLocation().getLine(), step);
			}
		}

		public void processBackground(Background background) {
			if (background != null) {
				this.background = background;
				hasBackground = true;
				backgroundSteps.addAll(background.getSteps());
				mapBackgroundSteps(background);
			}
		}

		/**
		 * Takes the serial number of scenario outline and links it to the executing scenario
		 **/
		public void processScenarioOutline(ScenarioDefinition scenarioOutline) {
			if (isScenarioOutline(scenarioOutline)) {
				scenarioOutlineMap.computeIfAbsent(scenarioOutline,
						k -> ((ScenarioOutline) scenarioOutline).getExamples()
								.stream()
								.flatMap(e -> e.getTableBody().stream())
								.map(r -> r.getLocation().getLine())
								.collect(Collectors.toList())
				);
				int iterationIdx = IntStream.range(0, scenarioOutlineMap.get(scenarioOutline).size())
						.filter(i -> getLine() == scenarioOutlineMap.get(scenarioOutline).get(i))
						.findFirst()
						.orElseThrow(() -> new IllegalStateException(String.format("No outline iteration number found for scenario %s:%s",
								uri,
								getLine()
						)));
				outlineIteration = String.format("[%d]", iterationIdx + 1);
			}
		}

		public void mapBackgroundSteps(Background background) {
			for (Step step : background.getSteps()) {
				scenarioLocationMap.put(step.getLocation().getLine(), step);
			}
		}

		public String getName() {
			return scenario.getName();
		}

		public String getKeyword() {
			return scenario.getKeyword();
		}

		public int getLine() {
			return isScenarioOutline(scenario) ? testCase.getLine() : scenario.getLocation().getLine();
		}

		public String getStepPrefix() {
			return hasBackground() && withBackground() ? background.getKeyword().toUpperCase() + AbstractReporter.COLON_INFIX : "";
		}

		public Step getStep(TestStep testStep) {
			PickleStepTestStep pickleStepTestStep = (PickleStepTestStep) testStep;
			Step step = scenarioLocationMap.get(pickleStepTestStep.getStepLine());
			if (step != null) {
				return step;
			}
			throw new IllegalStateException(String.format("Trying to get step for unknown line in feature. Scenario: %s, line: %s",
					scenario.getName(),
					getLine()
			));
		}

		public Maybe<String> getId() {
			return id;
		}

		public void setId(Maybe<String> newId) {
			if (id != null) {
				throw new IllegalStateException("Attempting re-set scenario ID for unfinished scenario: " + getName());
			}
			id = newId;
		}

		public void setTestCase(TestCase testCase) {
			this.testCase = testCase;
		}

		public void nextBackgroundStep() {
			backgroundSteps.poll();
		}

		public boolean isScenarioOutline(ScenarioDefinition scenario) {
			if (scenario != null) {
				return scenario instanceof ScenarioOutline;
			}
			return false;
		}

		public boolean withBackground() {
			return !backgroundSteps.isEmpty();
		}

		public boolean hasBackground() {
			return hasBackground && background != null;
		}

		public String getOutlineIteration() {
			return outlineIteration;
		}

		public Maybe<String> getCurrentStepId() {
			return currentStepId;
		}

		public void setCurrentStepId(Maybe<String> currentStepId) {
			this.currentStepId = currentStepId;
		}

		public Maybe<String> getHookStepId() {
			return hookStepId;
		}

		public void setHookStepId(Maybe<String> hookStepId) {
			this.hookStepId = hookStepId;
		}

		public Result.Type getHookStatus() {
			return hookStatus;
		}

		public void setHookStatus(Result.Type hookStatus) {
			this.hookStatus = hookStatus;
		}

		public void setFeatureUri(String featureUri) {
			this.uri = featureUri;
		}

		public String getFeatureUri() {
			return uri;
		}

		public void setCurrentText(String stepText) {
			this.text = stepText;
		}

		public String getCurrentText() {
			return text;
		}

		public TestCase getTestCase() {
			return testCase;
		}
	}
}
