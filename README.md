# Jenkins Library

This repository consists of a Jenkins shared library to extend Jenkins pipelines in order to share common parts between various projects and to reduce redundancies and keep code "DRY".

<img src=".github/logo.png" align="left" alt="Automotive DevOps Platform">

**Jenkins Library** project is part of the [Automotive DevOps Platform](https://www.tracetronic.com/products/automotive-devops-platform/) by TraceTronic.<br>With the **Automotive DevOps Platform**, we go from the big picture to the details and unite all phases of vehicle software testing – from planning the test scopes to summarizing the test results. At the same time, continuous monitoring across all test phases always provides an overview of all activities – even with several thousand test executions per day and in different test environments.

## Table of Content

- [Project Structure](#project-structure)
- [Dependencies](#dependencies)
- [Implementation](#usage)
  - [Use Cases](#use-cases)
  - [Technical Usage](#technical-usage)
- [Contribution](#contribution)
- [Support](#support)
- [License](#license)

## Project Structure

This repository is based on the predefined [directory structure](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#directory-structure) of Jenkins shared libraries.

## Dependencies

To simplify maintenance and to avoid duplicate implementations, some `vars` utilize features from other Jenkins plugins.

| Global Vars           | Prerequisites                                                                       |
|-----------------------|-------------------------------------------------------------------------------------|
| *maven*               | Local Maven installation or configured in Jenkins (Global Tool Configuration)       |
| *log*                 | [ANSI Color Plugin](https://plugins.jenkins.io/ansicolor/)                          |
| *pipeline2ATX*        | [Pipeline Utility Steps Plugin](https://plugins.jenkins.io/pipeline-utility-steps/) |

For more information open `/pipeline-syntax/globals` on your Jenkins instance or see the help files in the `vars` folder.

## Usage

### Use Cases

Each global variable is designed to cover specific requirements. The description of such use cases and requirements can be found in the `vars` directory next to the implementation. For a higher level description have a look at our [Automotive DevOps Platform](https://www.tracetronic.com/products/automotive-devops-platform/).

### Technical Usage

There are several ways to include this shared library within your own environment:

- Global Shared Libaries
- Folder-level Shared Libraries
- Automatic Shared Libraries

For further information and a deeper insight into usage, dynamic loading or versioning, please read [Extending with Shared Libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/) and [Pipeline: Shared Groovy Libraries](https://www.jenkins.io/doc/pipeline/steps/workflow-cps-global-lib/).

## Contribution

We encourage you to contribute to **Jenkins Library** using the [issue tracker](https://github.com/tracetronic/jenkins-library/issues/new/choose) to suggest feature requests and report bugs.

Currently, we do not accept any external pull requests.

## Support

If you have any questions, please contact us at [support@tracetronic.de](mailto:support@tracetronic.de).

## License

This project is licensed under the terms of the [MIT license](LICENSE).
