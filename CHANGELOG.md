# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Changed
- Use jnasmartcardio as provider.
- Logging improvement.

## [2.2.1] - 2024-04-12
### Changed
- Java source and target levels `1.6` -> `1.8`
### Upgraded
- Gradle `6.8.3` -> `7.6.4`

## [2.2.0] - 2024-03-29
### Added
- Added a note to warn about observability issues when using **Java 16+** to `README.md` file.
- Added project status badges on `README.md` file.
### Fixed
- CI: code coverage report when releasing.
- Documentation of `setDisconnectionMode` (the default value indicated was incorrect).
### Upgraded
- Keyple Plugin API `2.0.0` -> `2.3.0`
- Keyple Util Library `2.3.0` -> `2.3.1` (source code not impacted)

## [2.1.2] - 2023-04-24
### Fixed
- Unnecessary logging in Java 16+ during reflexive calls used to overcome the deficiencies of `smartcard.io` in Windows 
environment.

## [2.1.1] - 2023-04-05
### Changed
- `PcscPluginFactoryBuilder.useContactReaderIdentificationFilter` method marked as deprecated.
### Fixed
- Use default reader type identification filters.
- Logging of the available readers names in the `AbstractPcscPluginAdapter` class.

## [2.1.0] - 2022-07-25
### Added
- `PcscReader.transmitControlCommand` and `PcscReader.getIoctlCcidEscapeCommandId` methods (issue [#9]).
- "CHANGELOG.md" file (issue [eclipse-keyple/keyple#6]).
- CI: Forbid the publication of a version already released (issue [#6]).
### Upgraded
- "Keyple Util Library" to version `2.1.0` by removing the use of deprecated methods.
### Fixed
- Setting the smartcard.io path in the case of MacOS platform.

## [2.0.0] - 2021-10-06
This is the initial release.
It follows the extraction of Keyple 1.0 components contained in the `eclipse-keyple/keyple-java` repository to dedicated repositories.
It also brings many major API changes.

[unreleased]: https://github.com/eclipse-keyple/keyple-plugin-pcsc-java-lib/compare/2.2.1...HEAD
[2.2.1]: https://github.com/eclipse-keyple/keyple-plugin-pcsc-java-lib/compare/2.2.0...2.2.1
[2.2.0]: https://github.com/eclipse-keyple/keyple-plugin-pcsc-java-lib/compare/2.1.2...2.2.0
[2.1.2]: https://github.com/eclipse-keyple/keyple-plugin-pcsc-java-lib/compare/2.1.1...2.1.2
[2.1.1]: https://github.com/eclipse-keyple/keyple-plugin-pcsc-java-lib/compare/2.1.0...2.1.1
[2.1.0]: https://github.com/eclipse-keyple/keyple-plugin-pcsc-java-lib/compare/2.0.0...2.1.0
[2.0.0]: https://github.com/eclipse-keyple/keyple-plugin-pcsc-java-lib/releases/tag/2.0.0

[#9]: https://github.com/eclipse-keyple/keyple-plugin-pcsc-java-lib/issues/9
[#6]: https://github.com/eclipse-keyple/keyple-plugin-pcsc-java-lib/issues/6

[eclipse-keyple/keyple#6]: https://github.com/eclipse-keyple/keyple/issues/6
