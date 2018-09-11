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

Then run:

```
mvn testrunner:testplugin
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
