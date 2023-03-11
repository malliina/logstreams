# logstreams-client

Install this logback appender to stream logs to a given logstreams server.

## Installation

Specify the following in your `logback.xml`:

    <configuration>
        <appender name="LOGSTREAMS" class="com.malliina.logstreams.client.LogstreamsLogbackAkkaAppender">
            <endpoint>${LOGSTREAMS_HOST:-logs.malliina.com}</endpoint>
            <secure>true</secure>
            <username>${LOGSTREAMS_USER:-user123}</username>
            <password>${LOGSTREAMS_PASS}</password>
        </appender>
    </configuration>

You need to provide the following parameters to the appender:

| Name     | Meaning                          |
|----------|----------------------------------|
| endpoint | Host of logstreams server        |
| username | Username to logstreams server    |
| password | Password to logstreams server    |
| secure   | true to use SSL, false otherwise |
