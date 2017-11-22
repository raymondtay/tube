
When using the `git submodule update --remote slacks` or the similar, you might
discover that the code/configuration files are not being updated, what you need
to do is to

(1) Exit the sbt session
(2) Clear out the directories underneath `~/.sbt/1.0/staging/` for `slacks` 
  (2.1) a copy of the codebase is apparently maintained by sbt in the `staging`
  directories; not entirely sure why this is the case.
(3) Start the sbt session 

