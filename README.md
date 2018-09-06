# Test Runner

This plugin allows you to create tools that operate on Java (specifically, JUnit) tests with extremely fine-grained control.

If you want the latest version of this plugin (or for some reason you can't get if off Maven central), you can run:

# Quickstart

First, create a class that implements the following trait/interface:

Java:
```java
public interface TestPlugin {
    void execute(final Properties properties, final MavenProject project);
}
```

Scala:
```scala
trait TestPlugin {
    def execute(properties: Properties, project: MavenProject): Unit
}
```

Then, add the following plugin to the `pom.xml` of the Maven project you wish to run your `TestPlugin` on.

```xml
<plugin>
	<groupId>com.reedoei</groupId>
	<artifactId>testrunner-maven-plugin</artifactId>
	<version>1.0-SNAPSHOT</version>
	<configuration>
		<className>FULLY_QUALIFIED_CLASS_NAME_GOES_HERE</className>
	</configuration>
</plugin>
```

# Installation

If you want the latest version of this plugin (or for some reason you can't get if off Maven central), you can run:

```bash
git clone https://github.com/ReedOei/testrunner-maven-plugin
cd testrunner-maven-plugin
mvn install
```

You can generate documentation using:
```
mvn javadoc:javadoc scala:doc
```

# Example Usage

For example, if my class looks like this:
```scala
package com.my.package

class MyClass extends TestPlugin {
    def execute(properties: Properties, project: MavenProject): Unit = ???
}
```

Then my plugin declaration would be:
```xml
<plugin>
	<groupId>com.reedoei</groupId>
	<artifactId>testrunner-maven-plugin</artifactId>
	<version>1.0-SNAPSHOT</version>
	<configuration>
		<className>com.my.package.MyClass</className>
	</configuration>
</plugin>
```

# Configuration

The following options can be provided in the `configuration` tag of the plugin.
- `className`: This is the fully qualified class name of the class to invoke. The class should implement the `TestPlugin` trait.
- `propertiesPath`: The path to a [properties](https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html) file that will be used throughout the program.

Additionally, the following properties can also be specified in the properties file:

- `testplugin.runner.capture_state` (`boolean`, default `false`)
    - Whether to record heap pollution (using the technique described [here](https://experts.illinois.edu/en/publications/reliable-testing-detecting-state-polluting-tests-to-prevent-test-)) and return it as part of the `TestRunResult`. If turned on, this will slow down testing considerably.
- `testplugin.runner.timeout.universal` (`int`, default `-1`)
    - If a positive value is specified, an automatic timeout will be added to every test for the specified number of seconds.

The following properties are specific to the SmartRunner (more details below):
- `testplugin.runner.smart.timeout.default` (`double`, default `10800`)
    - How many seconds to wait for before timing out when running tests if there isn't enough prior information to guess (default is 6 hours).
- `testplugin.runner.smart.timeout.multiplier` (`double`, defualt `4`)
    - How much longer than expected to wait before timing out (e.g., if it expects the tests to take 10 seconds, it will wait 4 * 10 = 40 seconds).
- `testplugin.runner.smart.timeout.offset` (`double`, default `5`)
    - Minimum number of seconds to wait for any order (i.e., will never timeout before 5 seconds).
- `testplugin.runner.smart.timeout.pertest` (`double`, default `2`)
    - A flat number of seconds to wait per test (e.g., if you have 20 tests, it will add 40 seconds to the timeout time).

# Built-in functionality

## Plugins
There is one test plugin already built-in, which simply runs tests and outputs the results to a JSON file.
It's not terribly interesting on it's own, but it demonstrates a number of features and may be a helpful guide to follow.
You can see it's source code [here](https://github.com/ReedOei/testrunner-maven-plugin/blob/master/src/main/scala/com/reedoei/testrunner/mavenplugin/TestRunner.scala).

## Runners
Test runner infrastructure:

There are several ways to run tests.
The easiest is to simply use the `RunnerFactory` to create a runner for you.
To do so, simply do:

```scala
val runner = RunnerFactory.from(project)
```

Then you can run an arbitrary order of tests like so:

```scala
runner.run(List("test1", "test2"))
```

There is also a `runList` method provided for ease of use with Java code.
Note that the test names passed to the runner should be fully qualified names.

The two following runners are available:
- FixedOrderRunner: The FixedOrderRunner will simply run the specified list of tests in a given order.
- SmartRunner: The SmartRunner will run tests in a given order, but will also automatically timeout tests using knowledge from prior runs, detect flaky tests, and do some basic sanity checking of results.

Runners will all return an `Option[TestRunResult]`, where `TestRunResult` contains the following methods for accessing data:
```java
public java.util.List<java.lang.String> testOrder();
public java.util.Map<java.lang.String,TestResult> results();
public java.util.Map<java.lang.String,DiffContainer> diffs(); // This will be empty if the capture_state property (above) is not set to true.
```

The SmartRunner is used by default.

## Utility

### Test discovery

If you do not know the list of tests in your project, you may obtain them by using the `TestLocator`:

```scala
val testOrder = TestLocator.tests(project)
```

This will return a list of fully qualified test names that can be passed to a runner in the same order that surefire would return.

