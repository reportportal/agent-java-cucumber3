# Cucumber3 Agent for ReportPortal
 [ ![Download](https://api.bintray.com/packages/epam/reportportal/agent-java-cucumber3/images/download.svg) ](https://bintray.com/epam/reportportal/agent-java-cucumber3/_latestVersion)
 
![CI Build](https://github.com/reportportal/agent-java-cucumber3/workflows/CI%20Build/badge.svg)
[![Join Slack chat!](https://reportportal-slack-auto.herokuapp.com/badge.svg)](https://reportportal-slack-auto.herokuapp.com)
[![stackoverflow](https://img.shields.io/badge/reportportal-stackoverflow-orange.svg?style=flat)](http://stackoverflow.com/questions/tagged/reportportal)
[![UserVoice](https://img.shields.io/badge/uservoice-vote%20ideas-orange.svg?style=flat)](https://rpp.uservoice.com/forums/247117-report-portal)
[![Build with Love](https://img.shields.io/badge/build%20with-❤%EF%B8%8F%E2%80%8D-lightgrey.svg)](http://reportportal.io?style=flat)


### Installation

Add to POM.xml

**dependency**

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
<repositories>
     <repository>
        <snapshots>
          <enabled>false</enabled>
        </snapshots>
        <id>bintray-epam-reportportal</id>
        <name>bintray</name>
        <url>http://dl.bintray.com/epam/reportportal</url>
     </repository>
</repositories>


<dependency>
  <groupId>com.epam.reportportal</groupId>
  <artifactId>agent-java-cucumber3</artifactId>
  <version>5.0.1</version>
  <type>pom</type>
</dependency>
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

### Install Reporter, Configuration

See readme for Cucumber Agent 2 for details on installation and configuration (as this (3rd) version of agent is a copy of the 2nd version with minor chagnes in dependencies) - https://github.com/reportportal/agent-java-cucumber2


