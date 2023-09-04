include(":app", ":service", ":tile")

include(
    ":lib:common",
    ":lib:endpoint",
    ":lib:ipc",
    ":lib:model",
    ":lib:resource",
    ":lib:talpid",
    ":lib:theme",
    ":lib:common-test",
    ":lib:http-client"
)

include(":test", ":test:common", ":test:e2e", ":test:mockapi")
