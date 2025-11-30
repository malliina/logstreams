# API

## WebSocket /ws/sources

Open a WebSocket to send log events to the server. Authenticate with basic HTTP authentication.

Use the following JSON message payload, oldest events first:

    { 
        "events": [ 
            {
                "timestamp": 111111111,
                "timeFormatted": "today",
                "message": "Log message here",
                "loggerName": "com.malliina.Module",
                "threadName": "thread-123",
                "level": "info",
                "stackTrace": "optional stringified stacktrace",
            }
        ]
    }

Field `timestamp` is milliseconds since epoch.
