# Using Scalastyle on your local development environment

As a developer, you might want the option to run style checks locally before
you commit to a code repository aka as `pre-hooks`.

The default way is to leverage the sbt plugin for scalastyle and run that via
sbt itself, however developers often have another way to do it via the good'ol
commandline. Here's how you can set things up on your OSX (works on macOS
Sierra):

# install 'overcommit'

* run the command `gem install overcommit`
* install scalastyle via `brew` like `brew install scalastyle`
* create the file `.overcommit.yml` at the root of your codebase
* navigate to your code repository and install it `overcommit --install`
* run the command `overcommit -r` which would give you something like
  ```
  overcommit -r
  Running pre-commit hooks
  Analyze with Scalastyle..................................[Scalastyle] WARNING
  Warnings:
  warning file=/Users/raymondtay/NugitCode/tube/src/test/scala/MainSpec.scala message=Magic Number line=11 column=21
  
  
  ⚠ All pre-commit hooks passed, but with warnings
  ```
* With every fix, re-run the command `overcommit -r` till you get to the point
  of success like this:
  ```
  overcommit -r
  Running pre-commit hooks
  Analyze with Scalastyle..................................[Scalastyle] OK
  
  ✓ All pre-commit hooks passed
  ```

*Note:* Once you are done with this, whenever you commit to the repository
`git` would understand to trigger the pre-hooks to start running and you can
decide the next action to take.

# git hooks in action

Now you can see how things work in totality
```
tayboonls-MacBook-Pro:tube raymondtay$ git commit -a -m "test"
Running pre-commit hooks
Analyze with Scalastyle..................................[Scalastyle] WARNING
Warnings:
warning file=/Users/raymondtay/NugitCode/tube/src/test/scala/MainSpec.scala message=Whitespace at end of line line=3 column=19


⚠ All pre-commit hooks passed, but with warnings

Running commit-msg hooks
Check subject line................................[SingleLineSubject] OK
Check subject capitalization.....................[CapitalizedSubject] WARNING
Subject should start with a capital letter
Check text width..........................................[TextWidth] OK
Check for trailing periods in subject................[TrailingPeriod] OK

⚠ All commit-msg hooks passed, but with warnings

```
