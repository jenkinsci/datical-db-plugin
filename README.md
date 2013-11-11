DaticalDB4Jenkins
=================

This plugin permits Jenkins to run Datical DB build steps.

See [DaticalDB4Jenkins](https://wiki.jenkins-ci.org/display/JENKINS/DaticalDB4Jenkins) for more information.

[![Build Status](https://buildhive.cloudbees.com/job/jenkinsci/job/datical-db-plugin/badge/icon)](https://buildhive.cloudbees.com/job/jenkinsci/job/datical-db-plugin/)



Release Notes:

Make sure you've run "ssh-add ~/.ssh/id_rsa"

Edit pom.xml to bump the release number and add -SNAPSHOT

git commit -a pom.xml

mvn -Dresume=false release:prepare release:perform

Select the defaults

Verify release at https://wiki.jenkins-ci.org/display/JENKINS/DaticalDB4Jenkins after a day or so


