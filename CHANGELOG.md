# Changelog

## [Unreleased]
### Fixed
- Table parameter handling for different reporters
### Changed
- Client version updated on [5.0.21](https://github.com/reportportal/client-java/releases/tag/5.0.21)
### Fixed
- Empty interrupted suite in case of duplicate step

## [5.0.1]
### Added
- Missed javadocs
- Missed annotations
- A protected method `buildStartFeatureRequest` to ease Feature creation customization
### Changed
- Client version updated on [5.0.15](https://github.com/reportportal/client-java/releases/tag/5.0.15)
### Fixed
- 'CHILD_START_TIME_EARLIER_THAN_PARENT' Exception in some cases
- Double error message reporting

## [5.0.0]
### Added
- Docstring parameter handling
### Changed
- Many static methods from Util class were moved to AbstractReporter class and made protected to ease extension
- Client version updated 

## [5.0.0-RC-1]
### Added
- Callback reporting
### Changed
- Test step parameters handling
- Mime type processing for data embedding was improved
### Fixed
- Manually-reported nested steps now correctly fail all parents
### Removed
- Scenario Outline iteration number in item names, to not break re-runs

## [5.0.0-BETA-14]
### Fixed
- A bug when ambiguous item cases a Null Pointer Exception
- Incorrect item type settings
### Added
- Nested steps support

## [5.0.0-BETA-13]
### Added
- multi-thread execution support
- Test Case ID support
### Fixed
- codeRef reporting was added for every item in an item tree

## [5.0.0-BETA-12]
### Fixed
- cucumber status mapping
