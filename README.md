# Tube
The pipeline for Slack


# Developers

Developers should not check in any IDE-related files.

## Code coverage 

Tube uses code coverage via [scoverage](https://github.com/scoverage/sbt-scoverage) because we believe we don't deliver shit to users.

*Note*: code coverage is enabled, by default. To disable it, change the
property of `coverageEnabled` to `false` in `build.sbt`. If you want to know
more customizations, please read the [official site](https://github.com/scoverage/sbt-scoverage)

### Usage

Run the command:
```
sbt clean coverage test
```

## Scala Style

Tube uses the the sbt plugin [Scalastyle](http://www.scalastyle.org/sbt.html) for conformance of style. 

### Usage

You will need a configuration file. The easiest way to get one is to use the `scalastyleGenerateConfig` command:
```
sbt scalastyleGenerateConfig
```
This will create a `scalastyle-config.xml` in the current directory, with the default settings. Then, you can check your code with the scalastyle command
```
sbt scalastyle
```
This produces a list of errors on the console, as well as an XML result file `target/scalastyle-result.xml` (CheckStyle compatible format).

Finally, to check whether the code in the repository conforms to the style, run
the command:
```
sbt test:scalastyle
```

## IntelliJ gitignore

The official `.gitignore` file in this project contains the following from
[here](https://github.com/github/gitignore/blob/master/Global/JetBrains.gitignore)

## Eclipse gitignore

The official `.gitignore` file in this project contains the following from 
[here](https://github.com/github/gitignore/blob/master/Global/Eclipse.gitignore)

